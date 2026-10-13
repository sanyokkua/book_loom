package ua.bookloom.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.pipeline.BatchDraftingJobTest.BATCH;
import static ua.bookloom.pipeline.BatchDraftingJobTest.batchReply;
import static ua.bookloom.pipeline.BatchDraftingJobTest.entry;
import static ua.bookloom.pipeline.BatchDraftingJobTest.items;
import static ua.bookloom.pipeline.BatchedJobs.batchedJob;
import static ua.bookloom.pipeline.ChunkRunFixtures.DRAFT;
import static ua.bookloom.pipeline.ChunkRunFixtures.S0;
import static ua.bookloom.pipeline.ChunkRunFixtures.S1;
import static ua.bookloom.pipeline.ChunkRunFixtures.S2;
import static ua.bookloom.pipeline.ChunkRunFixtures.S3;
import static ua.bookloom.pipeline.ChunkRunFixtures.T0;
import static ua.bookloom.pipeline.ChunkRunFixtures.T1;
import static ua.bookloom.pipeline.ChunkRunFixtures.T2;
import static ua.bookloom.pipeline.ChunkRunFixtures.T3;
import static ua.bookloom.pipeline.ChunkRunFixtures.userMessage;
import static ua.bookloom.pipeline.TranslationJobTestSupport.brief;
import static ua.bookloom.pipeline.TranslationJobTestSupport.counts;
import static ua.bookloom.pipeline.TranslationJobTestSupport.project;
import static ua.bookloom.pipeline.TranslationJobTestSupport.report;
import static ua.bookloom.pipeline.TranslationJobTestSupport.stored;

import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.pipeline.JobState;
import ua.bookloom.api.project.SegmentCounts;
import ua.bookloom.pipeline.TranslationJobTestSupport.TestProject;

/**
 * A whole job in each arm of the batch format (15i.X1), selected by the {@code bookloom.batch.format} property the
 * way the eval selects it: a clean reply decides the same four targets in every arm, with one batch call, and no
 * marker reaches a stored target. The model is scripted at the call seam; the marked arm sends no response format,
 * so its calls take the unnamed outcomes.
 */
class BatchFormatJobTest {

    private static final String PROPERTY = "bookloom.batch.format";
    private static final SegmentCounts ALL_FOUR_ACCEPTED = new SegmentCounts(0, 4, 0, 0, 0);

    @TempDir
    private Path tempDir;

    @AfterEach
    void cleanUp() {
        System.clearProperty(PROPERTY);
        TranslationJobTestSupport.shutdownAll();
    }

    private TestProject fourParagraphs() {
        return project(ChunkRunFixtures.fourParagraphs(tempDir), brief("en", "uk"));
    }

    private static String marked(final String... targets) {
        final StringBuilder reply = new StringBuilder();
        for (int i = 0; i < targets.length; i++) {
            reply.append(i == 0 ? "" : "\n\n")
                    .append("⟦s")
                    .append(i + 1)
                    .append("⟧ ")
                    .append(targets[i]);
        }
        return reply.toString();
    }

    private static void assertFourTargetsStored(final TestProject project) {
        assertThat(counts(project)).isEqualTo(ALL_FOUR_ACCEPTED);
        assertThat(stored(project, "Book.md:0").maskedMachineTarget()).isEqualTo(T0);
        assertThat(stored(project, "Book.md:1").maskedMachineTarget()).isEqualTo(T1);
        assertThat(stored(project, "Book.md:2").maskedMachineTarget()).isEqualTo(T2);
        assertThat(stored(project, "Book.md:3").maskedMachineTarget()).isEqualTo(T3);
    }

    // Arm (a): the default, with the property unset.
    @Test
    void run_noFormatSet_sendsTheJsonBatchAndDecidesTheFourTargets() {
        final ScriptedChatModel model = new ScriptedChatModel()
                .answerTo(BATCH, batchReply(items(entry("1", T0), entry("2", T1), entry("3", T2), entry("4", T3))));
        final TestProject project = fourParagraphs();

        final var run = report(batchedJob(project, model).run());

        assertThat(run.end()).isEqualTo(JobState.COMPLETED);
        assertThat(model.requests()).singleElement().satisfies(request -> {
            assertThat(request.responseFormat()).isNotNull();
            assertThat(request.responseFormat().name()).isEqualTo(BATCH);
        });
        assertFourTargetsStored(project);
    }

    // Arm (b): one marked passage out, one marked plain-text reply back, no schema, the same four targets decided.
    @Test
    void run_markersFormat_sendsAMarkedPassageWithNoSchemaAndDecidesTheSameTargets() {
        System.setProperty(PROPERTY, "markers");
        final ScriptedChatModel model = new ScriptedChatModel().answer(batchReply(marked(T0, T1, T2, T3)));
        final TestProject project = fourParagraphs();

        final var run = report(batchedJob(project, model).run());

        assertThat(run.end()).isEqualTo(JobState.COMPLETED);
        assertThat(model.requests()).singleElement().satisfies(request -> {
            assertThat(request.responseFormat()).isNull();
            assertThat(userMessage(request))
                    .containsSubsequence("⟦s1⟧ " + S0, "⟦s2⟧ " + S1, "⟦s3⟧ " + S2, "⟦s4⟧ " + S3);
        });
        assertFourTargetsStored(project);
    }

    // Arm (c): the passage as context beside the numbered items, the JSON reply of today, the same four targets.
    @Test
    void run_dualFormat_sendsThePassageAndTheNumberedItemsAndDecidesTheSameTargets() {
        System.setProperty(PROPERTY, "dual");
        final ScriptedChatModel model = new ScriptedChatModel()
                .answerTo(BATCH, batchReply(items(entry("1", T0), entry("2", T1), entry("3", T2), entry("4", T3))));
        final TestProject project = fourParagraphs();

        final var run = report(batchedJob(project, model).run());

        assertThat(run.end()).isEqualTo(JobState.COMPLETED);
        final ChatRequest request = model.requests().getFirst();
        assertThat(model.requests()).hasSize(1);
        assertThat(userMessage(request)).contains("<s id=\"1\">" + S0 + "</s>", "<s id=\"4\">" + S3 + "</s>");
        assertThat(userMessage(request).split(java.util.regex.Pattern.quote(S2), -1))
                .hasSize(3);
        assertFourTargetsStored(project);
    }

    // IF the marked reply stops after two items, THEN one re-ask for the other two keeps the run at two calls.
    @Test
    void run_markersFormatReplyEndingAfterTwoItems_reasksTheLastTwoOnceAndDecidesAllFour() {
        System.setProperty(PROPERTY, "markers");
        final ScriptedChatModel model = new ScriptedChatModel()
                .answer(batchReply(marked(T0, T1)))
                .answer(batchReply("⟦s3⟧ " + T2 + "\n\n⟦s4⟧ " + T3));
        final TestProject project = fourParagraphs();

        report(batchedJob(project, model).run());

        assertThat(model.requests()).hasSize(2);
        assertThat(userMessage(model.requests().get(1)))
                .contains("⟦s3⟧ " + S2, "⟦s4⟧ " + S3)
                .doesNotContain("⟦s1⟧", "⟦s2⟧");
        assertFourTargetsStored(project);
    }

    // IF one marker is gone from the middle, THEN that segment (and the one that swallowed it) fall back to single
    // drafts.
    @Test
    void run_markersFormatReplyMissingTheThirdMarker_redraftsTheMergedAndTheMissingSegmentsAlone() {
        System.setProperty(PROPERTY, "markers");
        final ScriptedChatModel model = new ScriptedChatModel()
                .answer(batchReply("⟦s1⟧ " + T0 + "\n\n⟦s2⟧ " + T1 + " " + T2 + "\n\n⟦s4⟧ " + T3))
                .answerTo(DRAFT, batchReply(TranslationJobTestSupport.targetReply(T1)))
                .answerTo(DRAFT, batchReply(TranslationJobTestSupport.targetReply(T2)));
        final TestProject project = fourParagraphs();

        report(batchedJob(project, model).run());

        assertThat(model.requests()).hasSize(3);
        assertThat(model.requests().get(1).responseFormat()).isNotNull();
        assertFourTargetsStored(project);
    }

    // IF the model leaves a stray marker in its answer, THEN the stored target is clean text, never the marker.
    @Test
    void run_markersFormatWithStrayMarkerSpelling_storesNoMarkerInAnyTarget() {
        System.setProperty(PROPERTY, "markers");
        final ScriptedChatModel model = new ScriptedChatModel()
                .answer(batchReply("⟦s1⟧" + T0 + "\n⟦S2⟧ " + T1 + "\n⟦s3⟧ " + T2 + "\n⟦ s4 ⟧ " + T3 + "\n"));
        final TestProject project = fourParagraphs();

        report(batchedJob(project, model).run());

        assertFourTargetsStored(project);
    }
}
