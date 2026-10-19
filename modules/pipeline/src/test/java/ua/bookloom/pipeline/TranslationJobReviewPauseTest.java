package ua.bookloom.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.pipeline.ChunkRunFixtures.DRAFT;
import static ua.bookloom.pipeline.ChunkRunFixtures.ECHO1;
import static ua.bookloom.pipeline.ChunkRunFixtures.EDIT;
import static ua.bookloom.pipeline.ChunkRunFixtures.FIX;
import static ua.bookloom.pipeline.ChunkRunFixtures.REVIEW;
import static ua.bookloom.pipeline.ChunkRunFixtures.S0;
import static ua.bookloom.pipeline.ChunkRunFixtures.S1;
import static ua.bookloom.pipeline.ChunkRunFixtures.S2;
import static ua.bookloom.pipeline.ChunkRunFixtures.S3;
import static ua.bookloom.pipeline.ChunkRunFixtures.T0;
import static ua.bookloom.pipeline.ChunkRunFixtures.T1;
import static ua.bookloom.pipeline.ChunkRunFixtures.T2;
import static ua.bookloom.pipeline.ChunkRunFixtures.T3;
import static ua.bookloom.pipeline.ChunkRunFixtures.formats;
import static ua.bookloom.pipeline.ChunkRunFixtures.pauses;
import static ua.bookloom.pipeline.ChunkRunFixtures.preceding;
import static ua.bookloom.pipeline.ChunkRunFixtures.reviewed;
import static ua.bookloom.pipeline.ChunkRunFixtures.shown;
import static ua.bookloom.pipeline.ChunkRunFixtures.status;
import static ua.bookloom.pipeline.ChunkRunFixtures.userMessage;
import static ua.bookloom.pipeline.TranslationJobTestSupport.await;
import static ua.bookloom.pipeline.TranslationJobTestSupport.awaitPaused;
import static ua.bookloom.pipeline.TranslationJobTestSupport.brief;
import static ua.bookloom.pipeline.TranslationJobTestSupport.executor;
import static ua.bookloom.pipeline.TranslationJobTestSupport.job;
import static ua.bookloom.pipeline.TranslationJobTestSupport.project;
import static ua.bookloom.pipeline.TranslationJobTestSupport.replies;
import static ua.bookloom.pipeline.TranslationJobTestSupport.report;
import static ua.bookloom.pipeline.TranslationJobTestSupport.stored;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.stream.IntStream;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.pipeline.Finished;
import ua.bookloom.api.pipeline.JobEvent;
import ua.bookloom.api.pipeline.JobProgress;
import ua.bookloom.api.pipeline.JobReport;
import ua.bookloom.api.pipeline.JobStage;
import ua.bookloom.api.pipeline.JobState;
import ua.bookloom.api.pipeline.PauseReason;
import ua.bookloom.api.pipeline.Paused;
import ua.bookloom.api.pipeline.QualityDial;
import ua.bookloom.api.pipeline.Resumed;
import ua.bookloom.api.pipeline.ReviewMode;
import ua.bookloom.api.pipeline.StageStarted;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.RunRecord;
import ua.bookloom.api.project.SegmentRecord;
import ua.bookloom.api.project.TermType;
import ua.bookloom.pipeline.TranslationJobTestSupport.TestProject;
import ua.bookloom.pipeline.glossary.GlossaryIds;

/**
 * A run pauses where its review mode says — Assisted on a flagged segment, Manual after every segment, Unattended never
 * — naming the segment just decided, and every draft made after the resume reads what the person saved meanwhile.
 * A segment is flagged here by answering its draft and its fix with the upper-cased echo of its own source.
 */
class TranslationJobReviewPauseTest {

    private static final String S4 = "The gulls cried above the pier.";
    private static final String T4 = "Чайки кричали над старим причалом.";
    private static final String S5 = "The fishermen mended their nets.";
    private static final String T5 = "Рибалки лагодили свої сіті.";
    private static final String S6 = "The children ran along the shore.";
    private static final String T6 = "Діти весело бігли вздовж берега.";
    private static final String S7 = "The baker opened his small shop.";
    private static final String ECHO7 = "THE BAKER OPENED HIS SMALL SHOP.";
    private static final String S8 = "The captain studied the old map.";
    private static final String T8 = "Капітан вивчав стару карту.";

    @TempDir
    private Path tempDir;

    @AfterEach
    void cleanUpWorkers() {
        TranslationJobTestSupport.shutdownAll();
    }

    @Test
    void run_assistedFastFlagged_pausesOnFlaggedBeforeTheNextCall() {
        final ScriptedChatModel model = replies(T0, ECHO1, ECHO1, T2);
        final TestProject project = project(ChunkRunFixtures.threeParagraphs(tempDir), brief("en", "uk"));
        final TranslationJobImpl translation = job(project, model, ReviewMode.ASSISTED);
        final LinkedBlockingQueue<Paused> pauses = pauses(translation);

        final Future<Result<JobReport>> run = executor().submit(translation::run);
        final Paused pause = awaitPaused(pauses);
        final List<ChatRequest> beforeResume = model.requests();
        translation.resume();
        final JobReport ended = report(await(run));

        assertThat(pause)
                .extracting(Paused::reason, Paused::segmentId)
                .containsExactly(PauseReason.ON_FLAGGED, "Book.md:1");
        assertThat(pause.progress())
                .extracting(JobProgress::accepted, JobProgress::flagged, JobProgress::pending)
                .containsExactly(1, 1, 1);
        assertThat(beforeResume)
                .hasSize(3)
                .noneMatch(request -> userMessage(request).contains(shown(S2)));
        assertThat(pauses).isEmpty();
        assertThat(ended.end()).isEqualTo(JobState.COMPLETED);
    }

    @Test
    void run_unattendedFlagged_neverPauses() {
        final ScriptedChatModel model = replies(T0, ECHO1, ECHO1, T2);
        final TestProject project = project(ChunkRunFixtures.threeParagraphs(tempDir), brief("en", "uk"));
        final TranslationJobImpl translation = job(project, model, ReviewMode.UNATTENDED);
        final List<PauseSeen> seen = resumeEveryPause(translation, model);

        final JobReport ended = report(translation.run());

        assertThat(seen).isEmpty();
        assertThat(status(project, "Book.md:1")).isEqualTo(SegmentStatus.FLAGGED);
        assertThat(ended.end()).isEqualTo(JobState.COMPLETED);
    }

    @Test
    void run_manualAccepted_pausesAfterEachSegmentNamingIt() {
        final ScriptedChatModel model = replies(T0, T1, T2);
        final TestProject project = project(ChunkRunFixtures.threeParagraphs(tempDir), brief("en", "uk"));
        final TranslationJobImpl translation = job(project, model, ReviewMode.MANUAL);
        final List<PauseSeen> seen = resumeEveryPause(translation, model);

        report(translation.run());

        assertThat(seen)
                .containsExactly(
                        new PauseSeen(PauseReason.AFTER_SEGMENT, "Book.md:0", 1),
                        new PauseSeen(PauseReason.AFTER_SEGMENT, "Book.md:1", 2),
                        new PauseSeen(PauseReason.AFTER_SEGMENT, "Book.md:2", 3));
    }

    @Test
    void run_manualFlagged_pausesOnceOnFlagged() {
        final ScriptedChatModel model = replies(T0, ECHO1, ECHO1, T2);
        final TestProject project = project(ChunkRunFixtures.threeParagraphs(tempDir), brief("en", "uk"));
        final TranslationJobImpl translation = job(project, model, ReviewMode.MANUAL);
        final List<PauseSeen> seen = resumeEveryPause(translation, model);

        report(translation.run());

        assertThat(seen)
                .containsExactly(
                        new PauseSeen(PauseReason.AFTER_SEGMENT, "Book.md:0", 1),
                        new PauseSeen(PauseReason.ON_FLAGGED, "Book.md:1", 3),
                        new PauseSeen(PauseReason.AFTER_SEGMENT, "Book.md:2", 4));
    }

    // With the judge on, the rest of the chunk is drafted and judged before any decision, so the edit reaches the
    // next chunk and the paused chunk's last segment is decided from the draft it already had.
    @Test
    void run_assistedBalancedFlagged_pausesAfterTheJudgeAndFeedsTheEditToTheNextChunk() {
        // The fix returns the echo unchanged, so its segment is flagged after one round.
        final ScriptedChatModel model = replies(T0, T1, T2, T3, T5, T6, ECHO7, T8, ECHO7, T4)
                .answerTo(REVIEW, reviewed())
                .answerTo(REVIEW, reviewed());
        final TestProject project = project(nineParagraphs(), brief("en", "uk", QualityDial.BALANCED));
        final TranslationJobImpl translation = job(project, model, ReviewMode.ASSISTED);
        final LinkedBlockingQueue<Paused> pauses = pauses(translation);

        final Future<Result<JobReport>> run = executor().submit(translation::run);
        final Paused pause = awaitPaused(pauses);
        final List<String> atPause = formats(model);
        final List<SegmentStatus> storedAtPause = statuses(project, 8);
        saveEdit(project, "Book.md:6");
        translation.resume();
        report(await(run));

        assertThat(pause)
                .extracting(Paused::reason, Paused::segmentId)
                .containsExactly(PauseReason.ON_FLAGGED, "Book.md:6");
        assertThat(atPause).containsExactly(DRAFT, DRAFT, DRAFT, DRAFT, DRAFT, DRAFT, DRAFT, DRAFT, REVIEW, FIX);
        assertThat(storedAtPause).containsExactlyElementsOf(sixAcceptedThenFlaggedThenPending());
        assertThat(formats(model)).hasSize(12).endsWith(DRAFT, REVIEW);
        assertThat(userMessage(model.requests().get(10))).contains(shown(S4), preceding(EDIT, T8));
        assertThat(stored(project, "Book.md:7"))
                .extracting(SegmentRecord::status, SegmentRecord::machineTarget)
                .containsExactly(SegmentStatus.ACCEPTED, T8);
        assertThat(stored(project, "Book.md:6"))
                .extracting(SegmentRecord::status, SegmentRecord::userTarget)
                .containsExactly(SegmentStatus.REVISED, EDIT);
    }

    @Test
    void run_assistedFastEditDuringPause_feedsTheNextDraft() {
        final ScriptedChatModel model = replies(T0, ECHO1, ECHO1, T2);
        final TestProject project = project(ChunkRunFixtures.threeParagraphs(tempDir), brief("en", "uk"));
        final TranslationJobImpl translation = job(project, model, ReviewMode.ASSISTED);
        final LinkedBlockingQueue<Paused> pauses = pauses(translation);

        final Future<Result<JobReport>> run = executor().submit(translation::run);
        awaitPaused(pauses);
        saveEdit(project, "Book.md:1");
        translation.resume();
        report(await(run));

        assertThat(model.requests()).hasSize(4);
        assertThat(userMessage(model.requests().getLast())).contains(shown(S2), preceding(EDIT));
    }

    // A chunk reads the glossary once, when it starts, so a lock made inside it applies from the next chunk.
    @Test
    void run_lockDuringFlaggedPauseInsideChunk_masksFromTheNextChunk() {
        final ScriptedChatModel model = haleReplies();
        final TestProject project = project(haleBook(), brief("en", "uk"));
        final TranslationJobImpl translation = job(project, model, ReviewMode.ASSISTED);
        final LinkedBlockingQueue<Paused> pauses = pauses(translation);

        final Future<Result<JobReport>> run = executor().submit(translation::run);
        awaitPaused(pauses);
        lockHale(project);
        translation.resume();
        report(await(run));

        assertThat(model.requests()).hasSize(10);
        assertThat(model.requests().subList(3, 9))
                .extracting(ChunkRunFixtures::userMessage)
                .allSatisfy(message -> assertThat(message).contains("<Text>\nHale counted"));
        assertThat(userMessage(model.requests().getLast())).contains(shown("⟦g0⟧ counted the boats in row 8."));
        assertThat(stored(project, "Book.md:8").machineTarget()).isEqualTo("Гейл рахував човни в ряду 8.");
    }

    @Test
    void runRecord_assistedPauseOnFlagged_readsRunningPausedRunningCompleted() {
        final ScriptedChatModel model = replies(T0, ECHO1, ECHO1, T2);
        final TestProject project = project(ChunkRunFixtures.threeParagraphs(tempDir), brief("en", "uk"));
        final TranslationJobImpl translation = job(project, model, ReviewMode.ASSISTED);
        final List<JobState> observed = new CopyOnWriteArrayList<>();
        translation.subscribe(event -> observeStored(project, observed, event));
        resumeEveryPause(translation, model);

        report(translation.run());

        assertThat(observed).containsExactly(JobState.RUNNING, JobState.PAUSED, JobState.RUNNING, JobState.COMPLETED);
    }

    /** What a pause named, and how many model requests had been made when it came. */
    private record PauseSeen(PauseReason reason, @Nullable String segmentId, int requests) {}

    /** Records every pause and resumes at once, from the job thread, before the job starts to wait. */
    private static List<PauseSeen> resumeEveryPause(
            final TranslationJobImpl translation, final ScriptedChatModel model) {
        final List<PauseSeen> seen = new CopyOnWriteArrayList<>();
        translation.subscribe(event -> {
            if (event instanceof Paused paused) {
                seen.add(new PauseSeen(
                        paused.reason(), paused.segmentId(), model.requests().size()));
                translation.resume();
            }
        });
        return seen;
    }

    private static void observeStored(final TestProject project, final List<JobState> observed, final JobEvent event) {
        final boolean translating = event instanceof StageStarted started && started.stage() == JobStage.TRANSLATE;
        if (translating || event instanceof Paused || event instanceof Resumed || event instanceof Finished) {
            observed.add(latestRun(project).state());
        }
    }

    private static RunRecord latestRun(final TestProject project) {
        return Objects.requireNonNull(
                        project.stores().runs().latest(project.id()).data(), "latest")
                .orElseThrow();
    }

    private static void saveEdit(final TestProject project, final String segmentId) {
        project.stores()
                .segments()
                .update(
                        project.id(),
                        segmentId,
                        record -> record.withUserTarget(EDIT, EDIT).withStatus(SegmentStatus.REVISED));
    }

    private static void lockHale(final TestProject project) {
        final GlossaryEntry entry = new GlossaryEntry(
                GlossaryIds.of(project.id(), "Hale"),
                project.id(),
                "Hale",
                "Гейл",
                TermType.CHARACTER,
                Gender.MALE,
                true);
        Objects.requireNonNull(project.stores().glossary().add(entry).data(), "added Hale");
    }

    private static List<SegmentStatus> sixAcceptedThenFlaggedThenPending() {
        return List.of(
                SegmentStatus.ACCEPTED,
                SegmentStatus.ACCEPTED,
                SegmentStatus.ACCEPTED,
                SegmentStatus.ACCEPTED,
                SegmentStatus.ACCEPTED,
                SegmentStatus.ACCEPTED,
                SegmentStatus.FLAGGED,
                SegmentStatus.PENDING);
    }

    private static List<SegmentStatus> statuses(final TestProject project, final int count) {
        return IntStream.range(0, count)
                .mapToObj(index -> status(project, "Book.md:" + index))
                .toList();
    }

    /** One full Balanced chunk of eight paragraphs and a ninth that opens the next chunk. */
    private Path nineParagraphs() {
        return TestBooks.markdown(tempDir.resolve("Book.md"), String.join("\n\n", S0, S1, S2, S3, S5, S6, S7, S8, S4));
    }

    /** Nine rows naming Hale at their start, where the name scan never proposes it. */
    private Path haleBook() {
        final String book = String.join(
                "\n\n",
                IntStream.range(0, 9)
                        .mapToObj(TranslationJobReviewPauseTest::haleSource)
                        .toList());
        return TestBooks.markdown(tempDir.resolve("Book.md"), book);
    }

    /** Every row accepted but the flagged second, and the ninth answered behind the locked name's placeholder. */
    private static ScriptedChatModel haleReplies() {
        return replies(
                haleTarget(0),
                haleEcho(1),
                haleEcho(1),
                haleTarget(2),
                haleTarget(3),
                haleTarget(4),
                haleTarget(5),
                haleTarget(6),
                haleTarget(7),
                "⟦g0⟧ рахував човни в ряду 8.");
    }

    private static String haleSource(final int row) {
        return "Hale counted the boats in row " + row + ".";
    }

    private static String haleTarget(final int row) {
        return "Гейл рахував човни в ряду " + row + ".";
    }

    private static String haleEcho(final int row) {
        return haleSource(row).toUpperCase(Locale.ROOT);
    }
}
