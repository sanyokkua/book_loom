package ua.bookloom.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.pipeline.BatchDraftingJobTest.BATCH;
import static ua.bookloom.pipeline.BatchDraftingJobTest.batchReply;
import static ua.bookloom.pipeline.BatchDraftingJobTest.entry;
import static ua.bookloom.pipeline.BatchDraftingJobTest.items;
import static ua.bookloom.pipeline.BatchedJobs.batchedJob;
import static ua.bookloom.pipeline.ChunkRunFixtures.S0;
import static ua.bookloom.pipeline.ChunkRunFixtures.S2;
import static ua.bookloom.pipeline.ChunkRunFixtures.T0;
import static ua.bookloom.pipeline.ChunkRunFixtures.T1;
import static ua.bookloom.pipeline.ChunkRunFixtures.T2;
import static ua.bookloom.pipeline.ChunkRunFixtures.T3;
import static ua.bookloom.pipeline.TranslationJobTestSupport.await;
import static ua.bookloom.pipeline.TranslationJobTestSupport.awaitPaused;
import static ua.bookloom.pipeline.TranslationJobTestSupport.brief;
import static ua.bookloom.pipeline.TranslationJobTestSupport.executor;
import static ua.bookloom.pipeline.TranslationJobTestSupport.project;
import static ua.bookloom.pipeline.TranslationJobTestSupport.replies;
import static ua.bookloom.pipeline.TranslationJobTestSupport.report;
import static ua.bookloom.pipeline.TranslationJobTestSupport.stored;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.LinkedBlockingQueue;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.api.pipeline.PausePoint;
import ua.bookloom.api.pipeline.Paused;
import ua.bookloom.api.pipeline.PromptSection;
import ua.bookloom.api.pipeline.PromptSection.Origin;
import ua.bookloom.api.project.ContextSnapshot;
import ua.bookloom.api.project.PromptFrame;
import ua.bookloom.pipeline.TranslationJobTestSupport.TestProject;

/**
 * A segment's record keeps the key to the run's fixed frame, stored once per project, and only its call's user-message
 * sections: a batch-adopted one the sections that batch really sent, one drafted alone its own, one no call was made
 * for none.
 */
class BatchSentSectionsJobTest {

    private static final String ITEM_ONE = "<s id=\"1\">" + S0 + "</s>";
    private static final String ALL_FOUR = items(entry("1", T0), entry("2", T1), entry("3", T2), entry("4", T3));

    @TempDir
    private Path tempDir;

    @AfterEach
    void cleanUpWorkers() {
        TranslationJobTestSupport.shutdownAll();
    }

    private TestProject fourParagraphs() {
        return project(ChunkRunFixtures.fourParagraphs(tempDir), brief("en", "uk"));
    }

    private static ContextSnapshot snapshot(final TestProject project, final int index) {
        return Objects.requireNonNull(stored(project, "Book.md:" + index).context(), "snapshot");
    }

    private static List<PromptSection> sections(final TestProject project, final int index) {
        return snapshot(project, index).sections();
    }

    private static String text(final List<PromptSection> sections) {
        return sections.stream()
                .flatMap(section -> section.lines().stream())
                .reduce("", (left, right) -> left + "\n" + right);
    }

    // IF every segment kept the rules and the style sheet, THEN a book would store them thousands of times; the run
    // stores the frame once and each record names it, holding only the user message's sections.
    @Test
    void run_fourBatchedParagraphs_storeOneFrameAndOnlyTheirUserSections() {
        final TestProject project = fourParagraphs();

        report(batchedJob(project, new ScriptedChatModel().answerTo(BATCH, batchReply(ALL_FOUR)))
                .run());

        final List<PromptFrame> frames = Objects.requireNonNull(
                project.stores().promptFrames().all(project.id()).data());
        assertThat(frames).singleElement().satisfies(frame -> {
            assertThat(frame.sections()).isNotEmpty().allMatch(section -> section.origin() == Origin.SYSTEM);
            assertThat(snapshot(project, 0).frameHash()).isEqualTo(frame.hash());
            assertThat(snapshot(project, 3).frameHash()).isEqualTo(frame.hash());
        });
        assertThat(sections(project, 0)).isNotEmpty().allMatch(section -> section.origin() == Origin.USER);
    }

    // IF four segments are drafted in one batch, THEN each stored context holds the same sections, with the items.
    @Test
    void run_fastFourParagraphs_storesTheSentSectionsOnEveryBatchAdoptedRecord() {
        final TestProject project = fourParagraphs();

        report(batchedJob(project, new ScriptedChatModel().answerTo(BATCH, batchReply(ALL_FOUR)))
                .run());

        assertThat(sections(project, 0)).isNotEmpty();
        assertThat(text(sections(project, 0))).contains(ITEM_ONE);
        assertThat(sections(project, 1)).isEqualTo(sections(project, 0));
        assertThat(sections(project, 2)).isEqualTo(sections(project, 0));
        assertThat(sections(project, 3)).isEqualTo(sections(project, 0));
    }

    // IF the reply lacks id 2, THEN the three adopted keep the batch's sections and the segment drafted alone its own.
    @Test
    void run_replyMissingOneId_batchAdoptedHaveTheBatchSectionsAndTheAloneDraftItsOwn() {
        final TestProject project = fourParagraphs();
        final ScriptedChatModel model =
                replies(T1).answerTo(BATCH, batchReply(items(entry("1", T0), entry("3", T2), entry("4", T3))));

        report(batchedJob(project, model).run());

        assertThat(sections(project, 0)).isNotEmpty();
        assertThat(sections(project, 1)).isNotEmpty().isNotEqualTo(sections(project, 0));
        assertThat(text(sections(project, 1))).doesNotContain(ITEM_ONE);
        assertThat(snapshot(project, 1).frameHash())
                .isEqualTo(snapshot(project, 0).frameHash());
        assertThat(sections(project, 2)).isEqualTo(sections(project, 0));
        assertThat(sections(project, 3)).isEqualTo(sections(project, 0));
    }

    // IF ids 2 and 3 are asked again, THEN they record the first call's sections, not the re-ask's.
    @Test
    void run_reaskedIds_carryTheFirstBatchSections() {
        final TestProject project = fourParagraphs();
        final ScriptedChatModel model = new ScriptedChatModel()
                .answerTo(BATCH, batchReply(items(entry("1", T0), entry("4", T3))))
                .answerTo(BATCH, batchReply(items(entry("1", T1), entry("2", T2))));

        report(batchedJob(project, model).run());

        assertThat(sections(project, 1)).isNotEmpty().isEqualTo(sections(project, 0));
        assertThat(text(sections(project, 1))).contains(ITEM_ONE);
        assertThat(sections(project, 2)).isEqualTo(sections(project, 0));
    }

    // IF a number-only segment is kept without a call, THEN it stores no frame and no sections while its neighbours do.
    @Test
    void run_numberOnlySegment_keptVerbatim_storesNoCall() {
        final Path book = TestBooks.markdown(tempDir.resolve("Book.md"), String.join("\n\n", S0, "1881", S2));
        final TestProject project = project(book, brief("en", "uk"));
        final ScriptedChatModel model =
                new ScriptedChatModel().answerTo(BATCH, batchReply(items(entry("1", T0), entry("2", T2))));

        report(batchedJob(project, model).run());

        assertThat(snapshot(project, 1)).isEqualTo(ContextSnapshot.noCall());
        assertThat(sections(project, 0)).isNotEmpty();
        assertThat(sections(project, 2)).isNotEmpty();
    }

    // IF the run pauses after the batch's first segment, THEN the segments finished after the resume still hold the
    // sections.
    @Test
    void run_pauseAfterFirstSegmentOfABatch_resumeKeepsSectionsOfReadySegments() {
        final TestProject project = fourParagraphs();
        final TranslationJobImpl translation =
                batchedJob(project, new ScriptedChatModel().answerTo(BATCH, batchReply(ALL_FOUR)));
        final LinkedBlockingQueue<Paused> pauses = ChunkRunFixtures.pauses(translation);
        translation.pauseAt(Set.of(PausePoint.AFTER_SEGMENT));

        final var run = executor().submit(translation::run);
        awaitPaused(pauses);
        translation.pauseAt(Set.of());
        translation.resume();
        report(await(run));

        assertThat(sections(project, 0)).isNotEmpty();
        assertThat(sections(project, 3)).isEqualTo(sections(project, 0));
    }
}
