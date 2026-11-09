package ua.bookloom.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.pipeline.ChunkRunFixtures.DRAFT;
import static ua.bookloom.pipeline.ChunkRunFixtures.ECHO1;
import static ua.bookloom.pipeline.ChunkRunFixtures.ECHO2;
import static ua.bookloom.pipeline.ChunkRunFixtures.EDIT;
import static ua.bookloom.pipeline.ChunkRunFixtures.REPAIR;
import static ua.bookloom.pipeline.ChunkRunFixtures.S2;
import static ua.bookloom.pipeline.ChunkRunFixtures.T0;
import static ua.bookloom.pipeline.ChunkRunFixtures.T1;
import static ua.bookloom.pipeline.ChunkRunFixtures.T2;
import static ua.bookloom.pipeline.ChunkRunFixtures.T3;
import static ua.bookloom.pipeline.ChunkRunFixtures.formats;
import static ua.bookloom.pipeline.ChunkRunFixtures.pauses;
import static ua.bookloom.pipeline.ChunkRunFixtures.preceding;
import static ua.bookloom.pipeline.ChunkRunFixtures.shown;
import static ua.bookloom.pipeline.ChunkRunFixtures.status;
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
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.pipeline.JobReport;
import ua.bookloom.api.pipeline.JobStage;
import ua.bookloom.api.pipeline.JobState;
import ua.bookloom.api.pipeline.Paused;
import ua.bookloom.api.pipeline.QualityDial;
import ua.bookloom.api.pipeline.SegmentDecided;
import ua.bookloom.api.pipeline.SegmentDrafted;
import ua.bookloom.api.project.SegmentCounts;
import ua.bookloom.api.project.SegmentRecord;
import ua.bookloom.pipeline.TranslationJobTestSupport.TestProject;

/** A run takes each chunk through its drafts and its decisions, in turn, and keeps the decided prefix across a pause. */
class TranslationJobChunkTest {

    // Fails only the script check, where the echo fails it and the echo check too.
    @TempDir
    private Path tempDir;

    @AfterEach
    void cleanUpWorkers() {
        TranslationJobTestSupport.shutdownAll();
    }

    @Test
    void run_threeSegments_announcesPrepTranslateProofreadAndNoExport() {
        final Path book = TestBooks.txt(tempDir.resolve("Book.txt"), "One.\n\nTwo.\n\nThree.");
        final TranslationJobImpl translation = job(book, replies("Один.", "Два.", "Третій."));
        final List<JobStage> stages = ChunkRunFixtures.stages(translation);

        assertThat(report(translation.run()).end()).isEqualTo(JobState.COMPLETED);

        assertThat(stages).containsExactly(JobStage.PREP, JobStage.TRANSLATE, JobStage.PROOFREAD);
        assertThat(tempDir.resolve("Book.uk.txt")).doesNotExist();
    }

    @Test
    void run_balancedTenShortSegments_packOneChunk() {
        final String book = IntStream.rangeClosed(1, 10)
                .mapToObj(index -> "Short line " + index + ".")
                .collect(Collectors.joining("\n\n"));
        final ScriptedChatModel model = replies(IntStream.rangeClosed(1, 10)
                .mapToObj(index -> "Короткий рядок " + index + ".")
                .toArray(String[]::new));
        final TestProject project = balanced(TestBooks.markdown(tempDir.resolve("Book.md"), book));

        report(job(project, model).run());

        assertThat(formats(model))
                .containsExactly(DRAFT, DRAFT, DRAFT, DRAFT, DRAFT, DRAFT, DRAFT, DRAFT, DRAFT, DRAFT);
        assertThat(counts(project)).isEqualTo(new SegmentCounts(0, 10, 0, 0, 0));
    }

    @Test
    void run_balancedChunk_draftsAndDecidesInTurn() {
        final ScriptedChatModel model = replies(T0, ECHO1, T1, T2, T3);
        final TestProject project = balanced(ChunkRunFixtures.fourParagraphs(tempDir));

        report(job(project, model).run());

        assertThat(formats(model)).containsExactly(DRAFT, DRAFT, REPAIR, DRAFT, DRAFT);
        // The echo is repaired before the next draft, which is shown both neighbours decided.
        assertThat(userMessage(model.requests().get(3)))
                .contains(preceding(ChunkRunFixtures.S0, T0, ChunkRunFixtures.S1, T1))
                .doesNotContain(ECHO1);
        assertThat(counts(project)).isEqualTo(new SegmentCounts(0, 4, 0, 0, 0));
    }

    @Test
    void run_fastChunk_draftsAndDecidesInTurn() {
        final ScriptedChatModel model = replies(ECHO1, T2, T3);
        final Path book = TestBooks.markdown(
                tempDir.resolve("Book.md"),
                String.join("\n\n", ChunkRunFixtures.S1, ChunkRunFixtures.S2, ChunkRunFixtures.S3));
        final TestProject project = project(book, brief("en", "uk"));

        report(job(project, model).run());

        // Fast makes no repair: the echo is flagged at once, and the third draft is shown the second one decided.
        assertThat(formats(model)).containsExactly(DRAFT, DRAFT, DRAFT);
        assertThat(userMessage(model.requests().get(2))).contains(preceding(ChunkRunFixtures.S2, T2));
        assertThat(counts(project)).isEqualTo(new SegmentCounts(0, 2, 0, 1, 0));
    }

    // Each segment is decided before the next one is drafted, so the card moves row by row.
    @Test
    void run_fastChunk_announcesEachDecisionBeforeTheNextDraft() {
        final TranslationJobImpl translation =
                job(project(ChunkRunFixtures.fourParagraphs(tempDir), brief("en", "uk")), replies(T0, T1, T2, T3));
        final List<String> events = draftsAndDecisions(translation);

        report(translation.run());

        assertThat(events)
                .containsExactly(
                        "SegmentDrafted",
                        "SegmentDecided",
                        "SegmentDrafted",
                        "SegmentDecided",
                        "SegmentDrafted",
                        "SegmentDecided",
                        "SegmentDrafted",
                        "SegmentDecided");
    }

    @Test
    void run_stopDuringLastDraftOfChunk_keepsTheDecidedPrefix() {
        final ScriptedChatModel model = replies(T0, T1, T2, T3).blockNthRequest(4);
        final TestProject project = balanced(ChunkRunFixtures.fourParagraphs(tempDir));
        final TranslationJobImpl translation = job(project, model);

        final Future<Result<JobReport>> run = executor().submit(translation::run);
        model.awaitRequests(4);
        translation.cancel();

        assertThat(report(await(run)).end()).isEqualTo(JobState.CANCELLED);
        assertThat(model.requests()).hasSize(4);
        assertThat(List.of("Book.md:0", "Book.md:1", "Book.md:2"))
                .extracting(id -> status(project, id))
                .containsOnly(SegmentStatus.ACCEPTED);
        assertThat(stored(project, "Book.md:3"))
                .extracting(SegmentRecord::status, SegmentRecord::machineTarget)
                .containsExactly(SegmentStatus.PENDING, null);
        final ScriptedChatModel next = replies(T3);
        report(job(project, next).run());
        assertThat(userMessage(next.requests().getFirst())).contains(shown(ChunkRunFixtures.S3));
    }

    @Test
    void run_stopDuringRepair_keepsTheDecidedPrefix() {
        final ScriptedChatModel model = replies(T0, T1, ECHO2, T3).blockNthRequest(4);
        final TestProject project = balanced(ChunkRunFixtures.fourParagraphs(tempDir));
        final TranslationJobImpl translation = job(project, model);

        final Future<Result<JobReport>> run = executor().submit(translation::run);
        model.awaitRequests(4);
        translation.cancel();

        assertThat(report(await(run)).end()).isEqualTo(JobState.CANCELLED);
        assertThat(formats(model)).containsExactly(DRAFT, DRAFT, DRAFT, REPAIR);
        assertThat(status(project, "Book.md:0")).isEqualTo(SegmentStatus.ACCEPTED);
        assertThat(status(project, "Book.md:1")).isEqualTo(SegmentStatus.ACCEPTED);
        assertThat(List.of("Book.md:2", "Book.md:3"))
                .extracting(id -> stored(project, id))
                .allSatisfy(record -> assertThat(record)
                        .extracting(SegmentRecord::status, SegmentRecord::machineTarget)
                        .containsExactly(SegmentStatus.PENDING, null));
        final ScriptedChatModel next = replies(T2, T3);
        report(job(project, next).run());
        assertThat(userMessage(next.requests().getFirst())).contains(shown(S2));
    }

    @Test
    void run_pauseAfterFirstDecisionOfChunk_commitsPrefix() {
        final ScriptedChatModel model = replies(T0, T1, T2, T3);
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
        assertThat(model.requests()).hasSize(1);
        translation.resume();
        assertThat(report(await(run)).accepted()).isEqualTo(4);
        assertThat(formats(model)).containsExactly(DRAFT, DRAFT, DRAFT, DRAFT);
    }

    @Test
    void run_editDuringMidChunkPause_survivesChunkEnd() {
        final ScriptedChatModel model = replies(T0, T1, T2, T3);
        final TestProject project = balanced(ChunkRunFixtures.fourParagraphs(tempDir));
        final TranslationJobImpl translation = job(project, model);
        final LinkedBlockingQueue<Paused> pauses = pauses(translation);
        translation.subscribe(event -> ChunkRunFixtures.pauseOn(translation, "Book.md:0", event));

        final Future<Result<JobReport>> run = executor().submit(translation::run);
        awaitPaused(pauses);
        assertThat(model.requests()).hasSize(1);
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

    private static List<String> draftsAndDecisions(final TranslationJobImpl translation) {
        final List<String> names = new java.util.concurrent.CopyOnWriteArrayList<>();
        translation.subscribe(event -> {
            if (event instanceof SegmentDrafted || event instanceof SegmentDecided) {
                names.add(event.getClass().getSimpleName());
            }
        });
        return names;
    }

    private static TestProject balanced(final Path book) {
        return project(book, brief("en", "uk", QualityDial.BALANCED));
    }
}
