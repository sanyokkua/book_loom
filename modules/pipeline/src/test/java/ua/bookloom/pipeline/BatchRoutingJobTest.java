package ua.bookloom.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.pipeline.BatchedJobs.batchedJob;
import static ua.bookloom.pipeline.ChunkRunFixtures.DRAFT;
import static ua.bookloom.pipeline.ChunkRunFixtures.T1;
import static ua.bookloom.pipeline.ChunkRunFixtures.T2;
import static ua.bookloom.pipeline.ChunkRunFixtures.T3;
import static ua.bookloom.pipeline.ChunkRunFixtures.formats;
import static ua.bookloom.pipeline.TranslationJobTestSupport.await;
import static ua.bookloom.pipeline.TranslationJobTestSupport.awaitPaused;
import static ua.bookloom.pipeline.TranslationJobTestSupport.brief;
import static ua.bookloom.pipeline.TranslationJobTestSupport.executor;
import static ua.bookloom.pipeline.TranslationJobTestSupport.project;
import static ua.bookloom.pipeline.TranslationJobTestSupport.replies;
import static ua.bookloom.pipeline.TranslationJobTestSupport.report;

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
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.FinishReason;
import ua.bookloom.api.pipeline.FlaggedSegment;
import ua.bookloom.api.pipeline.JobReport;
import ua.bookloom.api.pipeline.PausePoint;
import ua.bookloom.api.pipeline.Paused;
import ua.bookloom.api.pipeline.ReviewMode;
import ua.bookloom.api.pipeline.RunRequest;
import ua.bookloom.pipeline.TranslationJobTestSupport.TestProject;

/**
 * How a batch call that gets no reply from the model is routed: it never costs the batch size, and a Skip from a pause
 * skips the step's first segment alone. The model is scripted at the call seam, so the call counts are exact.
 */
class BatchRoutingJobTest {

    private static final String BATCH = "draft-batch-json";

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

    private static String numbered(final int first, final int count) {
        return items(IntStream.range(0, count)
                .mapToObj(index -> entry(Integer.toString(index + 1), "Короткий рядок " + (first + index) + "."))
                .toArray(String[]::new));
    }

    private static Result<ChatResponse> failure(final ErrorCode code) {
        return Result.err(AppError.of(code, "Provider failure", "The scripted provider failed."));
    }

    @Test
    void run_threeBatchCallsAnsweredWithAContextWindowError_keepsBatchingAtTheMinimumSize() {
        final String book = IntStream.rangeClosed(1, 8)
                .mapToObj(index -> "Short line " + index + ".")
                .collect(Collectors.joining("\n\n"));
        final ScriptedChatModel model = replies(
                        "Короткий рядок 1.",
                        "Короткий рядок 2.",
                        "Короткий рядок 3.",
                        "Короткий рядок 4.",
                        "Короткий рядок 5.",
                        "Короткий рядок 6.")
                .answerTo(BATCH, failure(ErrorCode.contextWindow))
                .answerTo(BATCH, failure(ErrorCode.contextWindow))
                .answerTo(BATCH, failure(ErrorCode.contextWindow))
                .answerTo(BATCH, batchReply(numbered(7, 2)));
        final TestProject project = project(TestBooks.markdown(tempDir.resolve("Book.md"), book), brief("en", "uk"));

        report(batchedJob(project, model, new RunRequest(project.id(), ReviewMode.UNATTENDED), 2)
                .run());

        assertThat(formats(model))
                .containsExactly(BATCH, DRAFT, DRAFT, BATCH, DRAFT, DRAFT, BATCH, DRAFT, DRAFT, BATCH);
    }

    @Test
    void skipSegment_whilePausedOnABatchCall_flagsOnlyTheFirstSegmentAndBatchesTheRest() {
        final ScriptedChatModel model = new ScriptedChatModel()
                .answerTo(BATCH, failure(ErrorCode.upstream))
                .answerTo(BATCH, batchReply(items(entry("1", T1), entry("2", T2), entry("3", T3))));
        final TestProject project = fourParagraphs();
        final TranslationJobImpl translation = batchedJob(project, model);
        final LinkedBlockingQueue<Paused> pauses = ChunkRunFixtures.pauses(translation);
        translation.pauseAt(Set.of(PausePoint.ON_ERROR));
        final ExecutorService workers = executor();

        final Future<Result<JobReport>> run = workers.submit(translation::run);
        awaitPaused(pauses);
        translation.skipSegment();
        final JobReport report = report(await(run));

        assertThat(report.flaggedSegments()).containsExactly(new FlaggedSegment("Book.md:0", ErrorCode.upstream));
        assertThat(formats(model)).containsExactly(BATCH, BATCH);
        assertThat(report.accepted()).isEqualTo(3);
    }

    private TestProject fourParagraphs() {
        return project(ChunkRunFixtures.fourParagraphs(tempDir), brief("en", "uk"));
    }
}
