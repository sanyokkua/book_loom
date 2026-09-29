package ua.bookloom.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.pipeline.TranslationJobTestSupport.await;
import static ua.bookloom.pipeline.TranslationJobTestSupport.awaitPaused;
import static ua.bookloom.pipeline.TranslationJobTestSupport.brief;
import static ua.bookloom.pipeline.TranslationJobTestSupport.capturePaused;
import static ua.bookloom.pipeline.TranslationJobTestSupport.counts;
import static ua.bookloom.pipeline.TranslationJobTestSupport.executor;
import static ua.bookloom.pipeline.TranslationJobTestSupport.job;
import static ua.bookloom.pipeline.TranslationJobTestSupport.project;
import static ua.bookloom.pipeline.TranslationJobTestSupport.replies;
import static ua.bookloom.pipeline.TranslationJobTestSupport.report;
import static ua.bookloom.pipeline.TranslationJobTestSupport.shutdown;
import static ua.bookloom.pipeline.TranslationJobTestSupport.stored;

import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.FinishReason;
import ua.bookloom.api.pipeline.FlaggedSegment;
import ua.bookloom.api.pipeline.JobReport;
import ua.bookloom.api.pipeline.JobState;
import ua.bookloom.api.pipeline.PausePoint;
import ua.bookloom.api.pipeline.PauseReason;
import ua.bookloom.api.pipeline.Paused;
import ua.bookloom.api.project.SegmentCounts;
import ua.bookloom.api.project.SegmentRecord;
import ua.bookloom.pipeline.TranslationJobTestSupport.TestProject;

/** Every error a model call answers is routed by the table: pause, flag, cancel or fail the run. */
class TranslationJobProviderErrorTest {

    private static final Pattern SOURCE_TEXT = Pattern.compile("<Text>\\n(.*?)\\n</Text>", Pattern.DOTALL);

    @TempDir
    private Path tempDir;

    @AfterEach
    void cleanUpWorkers() {
        TranslationJobTestSupport.shutdownAll();
    }

    // Advancing past the failed segment would leave Two out of the repeated calls in this exact order.
    @Test
    void run_unreachableWithOnError_pausesThenRedoesTheSecondCallAfterResume() {
        final ScriptedChatModel model = replies("ONE.")
                .answer(Result.err(error(ErrorCode.unreachable)))
                .answer(Result.ok(response("TWO.")))
                .answer(Result.ok(response("THREE.")));
        final TestProject project = project(book(), brief("en", "uk"));
        final TranslationJobImpl translation = job(project, model);
        final LinkedBlockingQueue<Paused> pauses = new LinkedBlockingQueue<>();
        translation.subscribe(event -> capturePaused(pauses, event));
        translation.pauseAt(Set.of(PausePoint.ON_ERROR));
        final ExecutorService workers = executor();

        final Future<Result<JobReport>> run = workers.submit(translation::run);
        final Paused pause = awaitPaused(pauses);
        translation.resume();

        assertThat(pause.reason()).isEqualTo(PauseReason.ON_ERROR);
        assertThat(pause.error()).extracting(AppError::code).isEqualTo(ErrorCode.unreachable);
        assertThat(pause.progress())
                .extracting(p -> p.accepted(), p -> p.flagged(), p -> p.pending())
                .containsExactly(1, 0, 2);
        assertThat(report(await(run)))
                .extracting(JobReport::end, JobReport::accepted, JobReport::flagged)
                .containsExactly(JobState.COMPLETED, 3, 0);
        assertThat(sources(model)).containsExactly("One.", "Two.", "Two.", "Three.");
        shutdown(workers);
    }

    // Failing the run where pausing is not enabled must still leave the interrupted segment for a later run.
    @Test
    void run_unreachableWithoutOnError_endsFailedWithTwoPending() {
        final ScriptedChatModel model = replies("ONE.").answer(Result.err(error(ErrorCode.unreachable)));
        final TestProject project = project(book(), brief("en", "uk"));

        final JobReport result = finished(job(project, model));

        assertThat(result.end()).isEqualTo(JobState.FAILED);
        assertThat(result.error()).extracting(AppError::code).isEqualTo(ErrorCode.unreachable);
        assertThat(counts(project)).isEqualTo(new SegmentCounts(2, 1, 0, 0, 0));
    }

    // A code the provider answers is recoverable by the person, so none of them may flag or end the run.
    @ParameterizedTest
    @EnumSource(
            value = ErrorCode.class,
            names = {
                "unreachable",
                "timeout",
                "auth",
                "rateLimited",
                "upstream",
                "modelNotFound",
                "modelUnavailable",
                "missingCredential",
                "validation"
            })
    void run_providerErrorWithOnError_pausesWithItsCodeAndFlagsNothing(final ErrorCode code) {
        final ScriptedChatModel model = replies("ONE.").answer(Result.err(error(code)));
        final TestProject project = project(book(), brief("en", "uk"));
        final TranslationJobImpl translation = job(project, model);
        final LinkedBlockingQueue<Paused> pauses = new LinkedBlockingQueue<>();
        translation.subscribe(event -> capturePaused(pauses, event));
        translation.pauseAt(Set.of(PausePoint.ON_ERROR));
        final ExecutorService workers = executor();

        final Future<Result<JobReport>> run = workers.submit(translation::run);
        final Paused pause = awaitPaused(pauses);
        translation.cancel();

        assertThat(pause.reason()).isEqualTo(PauseReason.ON_ERROR);
        assertThat(pause.error()).extracting(AppError::code).isEqualTo(code);
        assertThat(report(await(run)).flagged()).isZero();
        assertThat(counts(project)).isEqualTo(new SegmentCounts(2, 1, 0, 0, 0));
        shutdown(workers);
    }

    // Pausing on an application bug would offer Retry now for something retrying cannot fix.
    @Test
    void run_internalWithOnError_endsFailedInternalWithoutPausing() {
        final ScriptedChatModel model = replies("ONE.").answer(Result.err(error(ErrorCode.internal)));

        final JobReport result = finishedWithOnError(job(project(book(), brief("en", "uk")), model));

        assertThat(result.end()).isEqualTo(JobState.FAILED);
        assertThat(result.error()).extracting(AppError::code).isEqualTo(ErrorCode.internal);
    }

    @Test
    void run_thrownCallWithOnError_endsFailedInternalWithoutPausing() {
        final ScriptedChatModel model = replies("ONE.").throwFailure(new IllegalStateException("model broke"));

        final JobReport result = finishedWithOnError(job(project(book(), brief("en", "uk")), model));

        assertThat(result.end()).isEqualTo(JobState.FAILED);
        assertThat(result.error()).extracting(AppError::code).isEqualTo(ErrorCode.internal);
    }

    // A run makes no discovery call, so this arriving is a bug that must not look like a provider error.
    @ParameterizedTest
    @EnumSource(
            value = ErrorCode.class,
            names = {"discoveryFailed", "busy"})
    void run_codeARunCannotSeeWithOnError_endsFailedInternalAndKeepsTheSegmentPending(final ErrorCode code) {
        final ScriptedChatModel model = replies("ONE.").answer(Result.err(error(code)));
        final TestProject project = project(book(), brief("en", "uk"));

        final JobReport result = finishedWithOnError(job(project, model));

        assertThat(result.end()).isEqualTo(JobState.FAILED);
        assertThat(result.error())
                .isNotNull()
                .satisfies(failure -> assertThat(failure.code()).isEqualTo(ErrorCode.internal))
                .satisfies(failure -> assertThat(failure.cause()).isNotNull());
        assertThat(stored(project, "Book.txt:1").status()).isEqualTo(SegmentStatus.PENDING);
    }

    @Test
    void run_cancelledAnswer_endsCancelled() {
        final ScriptedChatModel model = replies("ONE.").answer(Result.err(error(ErrorCode.cancelled)));

        final JobReport result = finishedWithOnError(job(project(book(), brief("en", "uk")), model));

        assertThat(result.end()).isEqualTo(JobState.CANCELLED);
    }

    // Flagging at once keeps the rest of the book going: the next segment must still be sent.
    @Test
    void run_contextWindowForFirstSegment_flagsItAndSendsTheNext() {
        final ScriptedChatModel model = new ScriptedChatModel()
                .answer(Result.err(error(ErrorCode.contextWindow)))
                .answer(Result.ok(response("TWO.")))
                .answer(Result.ok(response("THREE.")));
        final TestProject project = project(book(), brief("en", "uk"));

        final JobReport result = finished(job(project, model));

        assertThat(result.end()).isEqualTo(JobState.COMPLETED);
        assertThat(result.flaggedSegments()).containsExactly(new FlaggedSegment("Book.txt:0", ErrorCode.contextWindow));
        assertThat(stored(project, "Book.txt:0"))
                .extracting(SegmentRecord::status, SegmentRecord::machineTarget)
                .containsExactly(SegmentStatus.FLAGGED, null);
        assertThat(sources(model)).containsExactly("One.", "Two.", "Three.");
    }

    @Test
    void run_replyOfSpacesAndLineFeed_flagsEmptyCompletionAfterOneCall() {
        final ScriptedChatModel model =
                new ScriptedChatModel().answer(Result.ok(new ChatResponse("  \n", FinishReason.STOP)));
        final TestProject project = project(TestBooks.txt(tempDir.resolve("Book.txt"), "One."), brief("en", "uk"));

        final JobReport result = finished(job(project, model));

        assertThat(result.flaggedSegments())
                .containsExactly(new FlaggedSegment("Book.txt:0", ErrorCode.emptyCompletion));
        assertThat(model.requests()).hasSize(1);
    }

    // A length cut is final: repairing a truncated reply would only ask the model to cut off again.
    @Test
    void run_replyCutByLength_flagsValidationWithoutARepairRequest() {
        final ScriptedChatModel model = new ScriptedChatModel().answer(TranslationJobTestSupport.cutOffReply());
        final TestProject project = project(TestBooks.txt(tempDir.resolve("Book.txt"), "One."), brief("en", "uk"));

        final JobReport result = finished(job(project, model));

        assertThat(result.flaggedSegments()).containsExactly(new FlaggedSegment("Book.txt:0", ErrorCode.validation));
        assertThat(model.requests()).hasSize(1);
    }

    private JobReport finishedWithOnError(final TranslationJobImpl translation) {
        translation.pauseAt(Set.of(PausePoint.ON_ERROR));
        return finished(translation);
    }

    // A run that wrongly paused would block here, so the bounded await fails the test instead of hanging it.
    private JobReport finished(final TranslationJobImpl translation) {
        return report(await(executor().submit(translation::run)));
    }

    private Path book() {
        return TestBooks.txt(tempDir.resolve("Book.txt"), "One.\n\nTwo.\n\nThree.");
    }

    private static java.util.List<String> sources(final ScriptedChatModel model) {
        return model.requests().stream()
                .map(request -> SOURCE_TEXT
                        .matcher(request.messages().get(1).content())
                        .results()
                        .findFirst()
                        .map(match -> match.group(1))
                        .orElseThrow(() -> new AssertionError("draft prompt did not contain source text")))
                .toList();
    }

    private static ChatResponse response(final String content) {
        return new ChatResponse(TranslationJobTestSupport.targetReply(content), FinishReason.STOP);
    }

    private static AppError error(final ErrorCode code) {
        return AppError.of(code, "Model error", "The model answered an error.");
    }
}
