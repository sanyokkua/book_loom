package ua.bookloom.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.pipeline.ChunkRunFixtures.DOOR_ECHO;
import static ua.bookloom.pipeline.ChunkRunFixtures.DOOR_TARGET;
import static ua.bookloom.pipeline.ChunkRunFixtures.DRAFT;
import static ua.bookloom.pipeline.ChunkRunFixtures.ECHO1;
import static ua.bookloom.pipeline.ChunkRunFixtures.ECHO2;
import static ua.bookloom.pipeline.ChunkRunFixtures.EDIT;
import static ua.bookloom.pipeline.ChunkRunFixtures.FIX;
import static ua.bookloom.pipeline.ChunkRunFixtures.REVIEW;
import static ua.bookloom.pipeline.ChunkRunFixtures.S0;
import static ua.bookloom.pipeline.ChunkRunFixtures.S2;
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
import static ua.bookloom.pipeline.ChunkRunFixtures.target;
import static ua.bookloom.pipeline.ChunkRunFixtures.userMessage;
import static ua.bookloom.pipeline.TranslationJobTestSupport.await;
import static ua.bookloom.pipeline.TranslationJobTestSupport.awaitPaused;
import static ua.bookloom.pipeline.TranslationJobTestSupport.brief;
import static ua.bookloom.pipeline.TranslationJobTestSupport.counts;
import static ua.bookloom.pipeline.TranslationJobTestSupport.executor;
import static ua.bookloom.pipeline.TranslationJobTestSupport.job;
import static ua.bookloom.pipeline.TranslationJobTestSupport.project;
import static ua.bookloom.pipeline.TranslationJobTestSupport.replies;
import static ua.bookloom.pipeline.TranslationJobTestSupport.report;
import static ua.bookloom.pipeline.TranslationJobTestSupport.stored;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.pipeline.JobReport;
import ua.bookloom.api.pipeline.JobStage;
import ua.bookloom.api.pipeline.JobState;
import ua.bookloom.api.pipeline.PausePoint;
import ua.bookloom.api.pipeline.PauseReason;
import ua.bookloom.api.pipeline.Paused;
import ua.bookloom.api.pipeline.QualityDial;
import ua.bookloom.api.project.SegmentCounts;
import ua.bookloom.api.project.SegmentPath;
import ua.bookloom.api.project.SegmentRecord;
import ua.bookloom.pipeline.TranslationJobTestSupport.TestProject;

/** A run takes each chunk through its drafts, its one reviewer call and its decisions, and keeps them across a pause. */
class TranslationJobChunkTest {

    // Fails only the script check, where the echo fails it and the echo check too.
    private static final String PARTLY_TRANSLATED = "Він відчинив the old door.";
    private static final AppError UNREACHABLE =
            AppError.of(ErrorCode.unreachable, "Provider unreachable", "The provider did not answer.");

    @TempDir
    private Path tempDir;

    @AfterEach
    void cleanUpWorkers() {
        TranslationJobTestSupport.shutdownAll();
    }

    @Test
    void run_threeSegments_announcesPrepThenTranslateAndNoExport() {
        final Path book = TestBooks.txt(tempDir.resolve("Book.txt"), "One.\n\nTwo.\n\nThree.");
        final TranslationJobImpl translation = job(book, replies("Один.", "Два.", "Третій."));
        final List<JobStage> stages = ChunkRunFixtures.stages(translation);

        assertThat(report(translation.run()).end()).isEqualTo(JobState.COMPLETED);

        assertThat(stages).containsExactly(JobStage.PREP, JobStage.TRANSLATE);
        assertThat(tempDir.resolve("Book.uk.txt")).doesNotExist();
    }

    @Test
    void run_balancedTenShortSegments_packs442() {
        final String book = IntStream.rangeClosed(1, 10)
                .mapToObj(index -> "Short line " + index + ".")
                .collect(Collectors.joining("\n\n"));
        final ScriptedChatModel model = replies(IntStream.rangeClosed(1, 10)
                        .mapToObj(index -> "Короткий рядок " + index + ".")
                        .toArray(String[]::new))
                .answerTo(REVIEW, reviewed())
                .answerTo(REVIEW, reviewed())
                .answerTo(REVIEW, reviewed());
        final TestProject project = balanced(TestBooks.markdown(tempDir.resolve("Book.md"), book));

        report(job(project, model).run());

        assertThat(formats(model))
                .containsExactly(
                        DRAFT, DRAFT, DRAFT, DRAFT, REVIEW, DRAFT, DRAFT, DRAFT, DRAFT, REVIEW, DRAFT, DRAFT, REVIEW);
        assertThat(counts(project)).isEqualTo(new SegmentCounts(0, 10, 0, 0, 0));
    }

    @Test
    void run_balancedChunk_draftsEveryPairBeforeDeciding() {
        final ScriptedChatModel model =
                replies(T0, ECHO1, T2, T3, T1).answerTo(REVIEW, reviewed()).answerTo(REVIEW, reviewed());
        final TestProject project = balanced(ChunkRunFixtures.fourParagraphs(tempDir));

        report(job(project, model).run());

        assertThat(formats(model).subList(0, 6)).containsExactly(DRAFT, DRAFT, DRAFT, DRAFT, REVIEW, FIX);
        assertThat(userMessage(model.requests().get(2))).contains(preceding(T0, ECHO1));
        assertThat(counts(project)).isEqualTo(new SegmentCounts(0, 4, 0, 0, 0));
    }

    @Test
    void run_fastChunk_draftsAndDecidesInTurn() {
        final ScriptedChatModel model = replies(ECHO1, T1, T2, T3);
        final Path book = TestBooks.markdown(
                tempDir.resolve("Book.md"),
                String.join("\n\n", ChunkRunFixtures.S1, ChunkRunFixtures.S2, ChunkRunFixtures.S3));
        final TestProject project = project(book, brief("en", "uk"));

        report(job(project, model).run());

        assertThat(formats(model)).containsExactly(DRAFT, FIX, DRAFT, DRAFT);
        assertThat(userMessage(model.requests().get(2))).contains(preceding(T1));
        assertThat(counts(project)).isEqualTo(new SegmentCounts(0, 3, 0, 0, 0));
    }

    @Test
    void run_stopDuringLastDraftOfChunk_sendsNoReviewer() {
        final ScriptedChatModel model = replies(T0, T1, T2, T3).blockNthRequest(4);
        final TestProject project = balanced(ChunkRunFixtures.fourParagraphs(tempDir));
        final TranslationJobImpl translation = job(project, model);

        final Future<Result<JobReport>> run = executor().submit(translation::run);
        model.awaitRequests(4);
        translation.cancel();

        assertThat(report(await(run)).end()).isEqualTo(JobState.CANCELLED);
        assertThat(model.requests()).hasSize(4);
        assertThat(List.of("Book.md:0", "Book.md:1", "Book.md:2", "Book.md:3"))
                .extracting(id -> stored(project, id))
                .allSatisfy(record -> assertThat(record)
                        .extracting(SegmentRecord::status, SegmentRecord::machineTarget)
                        .containsExactly(SegmentStatus.PENDING, null));
        final ScriptedChatModel next = replies(T0, T1, T2, T3).answerTo(REVIEW, reviewed());
        report(job(project, next).run());
        assertThat(userMessage(next.requests().getFirst())).contains(shown(S0));
    }

    @Test
    void run_stopDuringDirectedFix_keepsTheDecidedPrefix() {
        final ScriptedChatModel model =
                replies(T0, T1, ECHO2, T3).answerTo(REVIEW, reviewed()).blockNthRequest(6);
        final TestProject project = balanced(ChunkRunFixtures.fourParagraphs(tempDir));
        final TranslationJobImpl translation = job(project, model);

        final Future<Result<JobReport>> run = executor().submit(translation::run);
        model.awaitRequests(6);
        translation.cancel();

        assertThat(report(await(run)).end()).isEqualTo(JobState.CANCELLED);
        assertThat(formats(model)).containsExactly(DRAFT, DRAFT, DRAFT, DRAFT, REVIEW, FIX);
        assertThat(status(project, "Book.md:0")).isEqualTo(SegmentStatus.ACCEPTED);
        assertThat(status(project, "Book.md:1")).isEqualTo(SegmentStatus.ACCEPTED);
        assertThat(List.of("Book.md:2", "Book.md:3"))
                .extracting(id -> stored(project, id))
                .allSatisfy(record -> assertThat(record)
                        .extracting(SegmentRecord::status, SegmentRecord::machineTarget)
                        .containsExactly(SegmentStatus.PENDING, null));
        final ScriptedChatModel next = replies(T2, T3).answerTo(REVIEW, reviewed());
        report(job(project, next).run());
        assertThat(userMessage(next.requests().getFirst())).contains(shown(S2));
    }

    @Test
    void run_pauseDuringReview_redoesOnlyTheReview() {
        final ScriptedChatModel model =
                replies(T0, T1, T2).answerTo(REVIEW, reviewed()).blockNthRequest(4);
        final TestProject project = balanced(ChunkRunFixtures.threeParagraphs(tempDir));
        final TranslationJobImpl translation = job(project, model);
        final LinkedBlockingQueue<Paused> pauses = pauses(translation);

        final Future<Result<JobReport>> run = executor().submit(translation::run);
        model.awaitRequests(4);
        translation.pause();
        final Paused pause = awaitPaused(pauses);

        assertThat(pause.reason()).isEqualTo(PauseReason.REQUESTED);
        assertThat(pause.progress())
                .extracting(p -> p.accepted(), p -> p.flagged(), p -> p.pending())
                .containsExactly(0, 0, 3);
        assertThat(model.requests()).hasSize(4);
        translation.resume();
        assertThat(report(await(run)).accepted()).isEqualTo(3);
        assertThat(formats(model)).containsExactly(DRAFT, DRAFT, DRAFT, REVIEW, REVIEW);
    }

    @Test
    void run_pauseAfterFirstDecisionOfChunk_commitsPrefixAndKeepsDrafts() {
        final ScriptedChatModel model = replies(T0, T1, T2, T3).answerTo(REVIEW, reviewed());
        final TestProject project = balanced(ChunkRunFixtures.fourParagraphs(tempDir));
        final TranslationJobImpl translation = job(project, model);
        final LinkedBlockingQueue<Paused> pauses = pauses(translation);
        translation.subscribe(event -> ChunkRunFixtures.pauseOn(translation, "Book.md:0", event));

        final Future<Result<JobReport>> run = executor().submit(translation::run);
        awaitPaused(pauses);

        assertThat(status(project, "Book.md:0")).isEqualTo(SegmentStatus.ACCEPTED);
        assertThat(List.of("Book.md:1", "Book.md:2", "Book.md:3"))
                .extracting(id -> status(project, id))
                .containsOnly(SegmentStatus.PENDING);
        assertThat(model.requests()).hasSize(5);
        translation.resume();
        assertThat(report(await(run)).accepted()).isEqualTo(4);
        assertThat(formats(model)).containsExactly(DRAFT, DRAFT, DRAFT, DRAFT, REVIEW);
    }

    @Test
    void run_editDuringMidChunkPause_survivesChunkEnd() {
        final ScriptedChatModel model = replies(T0, T1, T2, T3).answerTo(REVIEW, reviewed());
        final TestProject project = balanced(ChunkRunFixtures.fourParagraphs(tempDir));
        final TranslationJobImpl translation = job(project, model);
        final LinkedBlockingQueue<Paused> pauses = pauses(translation);
        translation.subscribe(event -> ChunkRunFixtures.pauseOn(translation, "Book.md:0", event));

        final Future<Result<JobReport>> run = executor().submit(translation::run);
        awaitPaused(pauses);
        assertThat(model.requests()).hasSize(5);
        project.stores()
                .segments()
                .update(
                        project.id(),
                        "Book.md:0",
                        record -> record.withUserTarget(EDIT, EDIT).withStatus(SegmentStatus.REVISED));
        translation.resume();
        report(await(run));

        assertThat(stored(project, "Book.md:0"))
                .extracting(SegmentRecord::status, SegmentRecord::userTarget)
                .containsExactly(SegmentStatus.REVISED, EDIT);
    }

    // The first round is kept: the second round's fix is sent again, and the repaired target is decided by the checks.
    // The first fix still leaves English words, but fewer blockers than the echo, so it is kept and a second round is
    // needed.
    @Test
    void run_pauseDuringSecondFixRound_continuesAtTheSecondRound() {
        final ScriptedChatModel model =
                replies(DOOR_ECHO, PARTLY_TRANSLATED, DOOR_TARGET).blockNthRequest(3);
        final TestProject project = balanced(ChunkRunFixtures.door(tempDir));
        final TranslationJobImpl translation = job(project, model);
        final LinkedBlockingQueue<Paused> pauses = pauses(translation);

        final Future<Result<JobReport>> run = executor().submit(translation::run);
        model.awaitRequests(3);
        translation.pause();
        awaitPaused(pauses);

        assertThat(formats(model)).containsExactly(DRAFT, FIX, FIX);
        translation.resume();
        report(await(run));
        assertSecondRoundContinued(project, model);
    }

    @Test
    void run_unreachableDuringSecondFixRound_pausesAndContinuesAtTheSecondRound() {
        final ScriptedChatModel model = replies(DOOR_ECHO, PARTLY_TRANSLATED)
                .answer(Result.err(UNREACHABLE))
                .answer(target(DOOR_TARGET));
        final TestProject project = balanced(ChunkRunFixtures.door(tempDir));
        final TranslationJobImpl translation = job(project, model);
        translation.pauseAt(Set.of(PausePoint.ON_ERROR));
        final LinkedBlockingQueue<Paused> pauses = pauses(translation);

        final Future<Result<JobReport>> run = executor().submit(translation::run);
        final Paused pause = awaitPaused(pauses);

        assertThat(pause.reason()).isEqualTo(PauseReason.ON_ERROR);
        assertThat(pause.error()).extracting(AppError::code).isEqualTo(ErrorCode.unreachable);
        assertThat(model.requests()).hasSize(3);
        translation.resume();
        report(await(run));
        assertSecondRoundContinued(project, model);
    }

    // A reviewer that times out or cannot be reached no longer pauses (it flags its segments); a refused key still
    // does.
    @Test
    void run_reviewerUnauthorized_pausesAndRedoesTheReviewerOnly() {
        final ScriptedChatModel model = replies(T0, T1, T2)
                .answerTo(REVIEW, Result.err(AppError.of(ErrorCode.auth, "Rejected", "the key was refused")))
                .answerTo(REVIEW, reviewed());
        final TestProject project = balanced(ChunkRunFixtures.threeParagraphs(tempDir));
        final TranslationJobImpl translation = job(project, model);
        translation.pauseAt(Set.of(PausePoint.ON_ERROR));
        final LinkedBlockingQueue<Paused> pauses = pauses(translation);

        final Future<Result<JobReport>> run = executor().submit(translation::run);
        final Paused pause = awaitPaused(pauses);

        assertThat(pause.error()).extracting(AppError::code).isEqualTo(ErrorCode.auth);
        assertThat(model.requests()).hasSize(4);
        translation.resume();
        assertThat(report(await(run)).accepted()).isEqualTo(3);
        assertThat(formats(model)).containsExactly(DRAFT, DRAFT, DRAFT, REVIEW, REVIEW);
    }

    // The second round's target passes the checks, which alone decide a repaired target: no reviewer call follows.
    private static void assertSecondRoundContinued(final TestProject project, final ScriptedChatModel model) {
        assertThat(formats(model)).containsExactly(DRAFT, FIX, FIX, FIX);
        assertThat(stored(project, "Book.txt:0"))
                .extracting(SegmentRecord::status, SegmentRecord::path, SegmentRecord::repairRounds)
                .containsExactly(SegmentStatus.ACCEPTED, SegmentPath.REPAIRED, 2);
    }

    private static TestProject balanced(final Path book) {
        return project(book, brief("en", "uk", QualityDial.BALANCED));
    }
}
