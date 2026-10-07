package ua.bookloom.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
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
import static ua.bookloom.pipeline.ChunkRunFixtures.formats;
import static ua.bookloom.pipeline.ChunkRunFixtures.shown;
import static ua.bookloom.pipeline.ChunkRunFixtures.userMessage;
import static ua.bookloom.pipeline.TranslationJobTestSupport.await;
import static ua.bookloom.pipeline.TranslationJobTestSupport.awaitPaused;
import static ua.bookloom.pipeline.TranslationJobTestSupport.brief;
import static ua.bookloom.pipeline.TranslationJobTestSupport.counts;
import static ua.bookloom.pipeline.TranslationJobTestSupport.executor;
import static ua.bookloom.pipeline.TranslationJobTestSupport.project;
import static ua.bookloom.pipeline.TranslationJobTestSupport.replies;
import static ua.bookloom.pipeline.TranslationJobTestSupport.report;
import static ua.bookloom.pipeline.TranslationJobTestSupport.stored;

import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.FinishReason;
import ua.bookloom.api.pipeline.JobReport;
import ua.bookloom.api.pipeline.JobState;
import ua.bookloom.api.pipeline.PausePoint;
import ua.bookloom.api.pipeline.Paused;
import ua.bookloom.api.pipeline.ReviewMode;
import ua.bookloom.api.pipeline.RunRequest;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.SegmentCounts;
import ua.bookloom.api.project.TermType;
import ua.bookloom.pipeline.TranslationJobTestSupport.TestProject;
import ua.bookloom.pipeline.glossary.GlossaryIds;

/**
 * A chunk's segments that need a model call are drafted in one batch call, and an id the reply gets wrong falls back
 * to a single-segment draft for that id alone. The model is scripted at the call seam, so the call counts are exact.
 */
class BatchDraftingJobTest {

    private static final String BATCH = "draft-batch-json";
    private static final SegmentCounts ALL_FOUR_ACCEPTED = new SegmentCounts(0, 4, 0, 0, 0);

    @TempDir
    private Path tempDir;

    @AfterEach
    void cleanUpWorkers() {
        TranslationJobTestSupport.shutdownAll();
    }

    private static Result<ChatResponse> batchReply(final String json) {
        return Result.ok(new ChatResponse(json, FinishReason.STOP));
    }

    private static String entry(final String id, final String target) {
        return "{\"id\":\"" + id + "\",\"target\":\"" + target + "\"}";
    }

    private static String items(final String... entries) {
        return "{\"items\":[" + String.join(",", entries) + "]}";
    }

    private TestProject fourParagraphs() {
        return project(ChunkRunFixtures.fourParagraphs(tempDir), brief("en", "uk"));
    }

    @Test
    void run_fastFourParagraphs_draftsAllInOneBatchCall() {
        final ScriptedChatModel model = new ScriptedChatModel()
                .answerTo(BATCH, batchReply(items(entry("1", T0), entry("2", T1), entry("3", T2), entry("4", T3))));
        final TestProject project = fourParagraphs();

        final JobReport run = report(batchedJob(project, model).run());

        assertThat(run.end()).isEqualTo(JobState.COMPLETED);
        assertThat(formats(model)).containsExactly(BATCH);
        assertThat(userMessage(model.requests().getFirst()))
                .contains("<s id=\"1\">" + S0 + "</s>", "<s id=\"4\">" + S3 + "</s>");
        assertThat(counts(project)).isEqualTo(ALL_FOUR_ACCEPTED);
    }

    @Test
    void run_replyMissingOneId_redraftsOnlyThatSegmentAlone() {
        final ScriptedChatModel model =
                replies(T1).answerTo(BATCH, batchReply(items(entry("1", T0), entry("3", T2), entry("4", T3))));
        final TestProject project = fourParagraphs();

        report(batchedJob(project, model).run());

        assertThat(formats(model)).containsExactly(BATCH, DRAFT);
        assertThat(userMessage(model.requests().get(1))).contains(shown(S1));
        assertThat(counts(project)).isEqualTo(ALL_FOUR_ACCEPTED);
    }

    @Test
    void run_replyRepeatingAnId_redraftsOnlyThatSegmentAlone() {
        final ScriptedChatModel model = replies(T1)
                .answerTo(
                        BATCH,
                        batchReply(
                                items(entry("1", T0), entry("2", T1), entry("2", T1), entry("3", T2), entry("4", T3))));
        final TestProject project = fourParagraphs();

        report(batchedJob(project, model).run());

        assertThat(formats(model)).containsExactly(BATCH, DRAFT);
        assertThat(userMessage(model.requests().get(1))).contains(shown(S1));
        assertThat(counts(project)).isEqualTo(ALL_FOUR_ACCEPTED);
    }

    @Test
    void run_replyMergingTwoItems_redraftsBothAlone() {
        final ScriptedChatModel model = replies(T1, T2)
                .answerTo(BATCH, batchReply(items(entry("1", T0), entry("2", T1 + " " + T2), entry("4", T3))));
        final TestProject project = fourParagraphs();

        report(batchedJob(project, model).run());

        assertThat(formats(model)).containsExactly(BATCH, DRAFT, DRAFT);
        assertThat(userMessage(model.requests().get(1))).contains(shown(S1));
        assertThat(userMessage(model.requests().get(2))).contains(shown(S2));
        assertThat(counts(project)).isEqualTo(ALL_FOUR_ACCEPTED);
    }

    @Test
    void run_replyWithAnExtraId_takesEveryExpectedIdFromTheBatch() {
        final ScriptedChatModel model = new ScriptedChatModel()
                .answerTo(
                        BATCH,
                        batchReply(items(
                                entry("1", T0), entry("2", T1), entry("3", T2), entry("4", T3), entry("9", "Зайве."))));
        final TestProject project = fourParagraphs();

        report(batchedJob(project, model).run());

        assertThat(formats(model)).containsExactly(BATCH);
        assertThat(counts(project)).isEqualTo(ALL_FOUR_ACCEPTED);
    }

    @Test
    void run_unreadableRefusal_redraftsEverySegmentAlone() {
        final ScriptedChatModel model = replies(T0, T1, T2, T3)
                .answerTo(BATCH, batchReply("I'm sorry, but I can't help with translating this text."));
        final TestProject project = fourParagraphs();

        report(batchedJob(project, model).run());

        assertThat(formats(model)).containsExactly(BATCH, DRAFT, DRAFT, DRAFT, DRAFT);
        assertThat(counts(project)).isEqualTo(ALL_FOUR_ACCEPTED);
    }

    @Test
    void run_itemThatDropsItsTokens_redraftsOnlyThatSegmentAlone() {
        final Path book = TestBooks.markdown(
                tempDir.resolve("Book.md"), String.join("\n\n", S0, "The lighthouse *stood* on the cliff.", S2));
        final ScriptedChatModel model = replies("Маяк ⟦g0⟧стояв⟦g1⟧ на прибережній скелі.")
                .answerTo(BATCH, batchReply(items(entry("1", T0), entry("2", T1), entry("3", T2))));
        final TestProject project = project(book, brief("en", "uk"));

        report(batchedJob(project, model).run());

        assertThat(formats(model)).containsExactly(BATCH, DRAFT);
        assertThat(userMessage(model.requests().get(1))).contains("lighthouse");
        assertThat(counts(project)).isEqualTo(new SegmentCounts(0, 3, 0, 0, 0));
    }

    @Test
    void run_numberOnlySegment_isKeptWithoutCallAndLeftOutOfTheBatch() {
        final Path book = TestBooks.markdown(tempDir.resolve("Book.md"), String.join("\n\n", S0, "1881", S2));
        final ScriptedChatModel model =
                new ScriptedChatModel().answerTo(BATCH, batchReply(items(entry("1", T0), entry("2", T2))));
        final TestProject project = project(book, brief("en", "uk"));

        report(batchedJob(project, model).run());

        assertThat(formats(model)).containsExactly(BATCH);
        final String user = userMessage(model.requests().getFirst());
        assertThat(user).contains("<s id=\"1\">" + S0 + "</s>", "<s id=\"2\">" + S2 + "</s>");
        assertThat(user).doesNotContain("1881</s>");
        assertThat(counts(project)).isEqualTo(new SegmentCounts(0, 3, 0, 0, 0));
    }

    @Test
    void run_missingIdInTheFirstBatch_halvesTheSizeOfTheNextBatches() {
        final String book = IntStream.rangeClosed(1, 16)
                .mapToObj(index -> "Short line " + index + ".")
                .collect(Collectors.joining("\n\n"));
        final ScriptedChatModel model = replies("Короткий рядок 3.")
                .answerTo(BATCH, batchReply(numbered(1, 8, 3)))
                .answerTo(BATCH, batchReply(numbered(9, 4, 0)))
                .answerTo(BATCH, batchReply(numbered(13, 4, 0)));
        final TestProject project = project(TestBooks.markdown(tempDir.resolve("Book.md"), book), brief("en", "uk"));

        report(batchedJob(project, model).run());

        assertThat(formats(model)).containsExactly(BATCH, DRAFT, BATCH, BATCH);
        assertThat(counts(project)).isEqualTo(new SegmentCounts(0, 16, 0, 0, 0));
    }

    private static String numbered(final int first, final int count, final int missingLocalId) {
        return items(IntStream.range(0, count)
                .filter(index -> index + 1 != missingLocalId)
                .mapToObj(index -> entry(Integer.toString(index + 1), "Короткий рядок " + (first + index) + "."))
                .toArray(String[]::new));
    }

    @Test
    void run_pauseAfterTheFirstSegmentOfABatch_doesNotCallTheModelAgainOnResume() {
        final ScriptedChatModel model = new ScriptedChatModel()
                .answerTo(BATCH, batchReply(items(entry("1", T0), entry("2", T1), entry("3", T2), entry("4", T3))));
        final TestProject project = fourParagraphs();
        final TranslationJobImpl translation = batchedJob(project, model);
        final LinkedBlockingQueue<Paused> pauses = ChunkRunFixtures.pauses(translation);
        translation.pauseAt(Set.of(PausePoint.AFTER_SEGMENT));
        final ExecutorService workers = executor();

        final Future<Result<JobReport>> run = workers.submit(translation::run);
        final Paused first = awaitPaused(pauses);
        final int callsWhilePaused = model.requests().size();
        translation.pauseAt(Set.of());
        translation.resume();

        assertThat(report(await(run)).end()).isEqualTo(JobState.COMPLETED);
        assertThat(first.progress().accepted()).isEqualTo(1);
        assertThat(callsWhilePaused).isEqualTo(1);
        assertThat(formats(model)).containsExactly(BATCH);
        assertThat(counts(project)).isEqualTo(ALL_FOUR_ACCEPTED);
    }

    @Test
    void run_detectedWindowOf4096_isSentAsTheContextOfEveryCall() {
        final ScriptedChatModel model = new ScriptedChatModel()
                .answerTo(BATCH, batchReply(items(entry("1", T0), entry("2", T1), entry("3", T2), entry("4", T3))));
        final TestProject project = fourParagraphs();

        report(batchedJob(project, model, new RunRequest(project.id(), ReviewMode.UNATTENDED, 4096))
                .run());

        assertThat(model.requests())
                .extracting(request -> request.contextWindow())
                .containsOnly(4096);
    }

    @Test
    void run_noDetectedWindow_sendsTheDefaultWindow() {
        final ScriptedChatModel model = new ScriptedChatModel()
                .answerTo(BATCH, batchReply(items(entry("1", T0), entry("2", T1), entry("3", T2), entry("4", T3))));
        final TestProject project = fourParagraphs();

        report(batchedJob(project, model).run());

        assertThat(model.requests())
                .extracting(request -> request.contextWindow())
                .containsOnly(16_384);
    }

    private TestProject lockedNellBook() {
        final TestProject project = project(
                TestBooks.markdown(
                        tempDir.resolve("Book.md"),
                        String.join(
                                "\n\n",
                                "Nell opened the old door.",
                                "She walked to the harbour.",
                                "Nell said nothing for a long while.")),
                brief("en", "uk"));
        final GlossaryEntry nell = new GlossaryEntry(
                GlossaryIds.of(project.id(), "Nell"),
                project.id(),
                "Nell",
                "Нелл",
                TermType.CHARACTER,
                Gender.FEMALE,
                true);
        project.stores().glossary().add(nell);
        return project;
    }

    @Test
    void run_lockedName_isHiddenPerItemAndWrittenBackFromItsOwnToken() {
        final ScriptedChatModel model = new ScriptedChatModel()
                .answerTo(
                        BATCH,
                        batchReply(items(
                                entry("1", "⟦g0⟧ відчинила старі двері."),
                                entry("2", "Вона пішла до гавані."),
                                entry("3", "⟦g0⟧ довго нічого не казала."))));
        final TestProject project = lockedNellBook();

        report(batchedJob(project, model).run());

        assertThat(formats(model)).containsExactly(BATCH);
        assertThat(userMessage(model.requests().getFirst())).contains("1: ⟦g0⟧ → Нелл", "3: ⟦g0⟧ → Нелл");
        assertThat(stored(project, "Book.md:0").maskedMachineTarget()).startsWith("Нелл відчинила");
        assertThat(stored(project, "Book.md:2").maskedMachineTarget()).startsWith("Нелл довго");
    }

    @Test
    void run_lockedNameWrittenOutInsteadOfItsToken_redraftsOnlyThatSegmentAlone() {
        final ScriptedChatModel model = replies("⟦g0⟧ довго нічого не казала.")
                .answerTo(
                        BATCH,
                        batchReply(items(
                                entry("1", "⟦g0⟧ відчинила старі двері."),
                                entry("2", "Вона пішла до гавані."),
                                entry("3", "Нелл довго нічого не казала."))));
        final TestProject project = lockedNellBook();

        report(batchedJob(project, model).run());

        assertThat(formats(model)).containsExactly(BATCH, DRAFT);
        assertThat(stored(project, "Book.md:2").maskedMachineTarget()).startsWith("Нелл довго");
    }

    @Test
    void run_noteMarkerDroppedByTheBatch_redraftsThatSegmentAloneAndKeepsTheMarker() {
        final Path book = TestBooks.txt(
                tempDir.resolve("Book.txt"),
                String.join("\n\n", S0, "The lighthouse stood on the cliff.[3] The wind rose.", S2));
        final ScriptedChatModel model = replies("Маяк стояв на прибережній скелі.[3] Вітер здійнявся.")
                .answerTo(
                        BATCH,
                        batchReply(items(
                                entry("1", T0),
                                entry("2", "Маяк стояв на прибережній скелі. Вітер здійнявся."),
                                entry("3", T2))));
        final TestProject project = project(book, brief("en", "uk"));

        report(batchedJob(project, model).run());

        assertThat(formats(model)).containsExactly(BATCH, DRAFT);
        assertThat(stored(project, "Book.txt:1").maskedMachineTarget()).contains("[3]");
    }
}
