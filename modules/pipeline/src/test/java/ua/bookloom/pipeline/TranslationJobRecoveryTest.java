package ua.bookloom.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.pipeline.TranslationJobTestSupport.await;
import static ua.bookloom.pipeline.TranslationJobTestSupport.awaitPaused;
import static ua.bookloom.pipeline.TranslationJobTestSupport.capturePaused;
import static ua.bookloom.pipeline.TranslationJobTestSupport.documents;
import static ua.bookloom.pipeline.TranslationJobTestSupport.executor;
import static ua.bookloom.pipeline.TranslationJobTestSupport.job;
import static ua.bookloom.pipeline.TranslationJobTestSupport.replies;
import static ua.bookloom.pipeline.TranslationJobTestSupport.report;
import static ua.bookloom.pipeline.TranslationJobTestSupport.shutdown;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.FinishReason;
import ua.bookloom.api.pipeline.JobEvent;
import ua.bookloom.api.pipeline.JobReport;
import ua.bookloom.api.pipeline.JobStage;
import ua.bookloom.api.pipeline.JobState;
import ua.bookloom.api.pipeline.PausePoint;
import ua.bookloom.api.pipeline.PauseReason;
import ua.bookloom.api.pipeline.Paused;
import ua.bookloom.api.pipeline.StageStarted;

/** Proves recoverable errors retry only the failed model or export step. */
class TranslationJobRecoveryTest {

    @TempDir
    private Path tempDir;

    @AfterEach
    void cleanUpWorkers() {
        TranslationJobTestSupport.shutdownAll();
    }

    // Turning a model error into Result.err would lose this partial successful FAILED report.
    @Test
    void run_unreachableWithoutOnError_returnsSuccessfulFailedReport() {
        final AppError unreachable = unreachable();
        final ScriptedChatModel model = replies("ONE.").answer(Result.err(unreachable));
        final Path destination = tempDir.resolve("Book.uk.md");

        final Result<JobReport> result =
                markdownJob(model, destination, "One.\n\nTwo.\n\nThree.").run();

        assertThat(result.isOk()).isTrue();
        assertThat(report(result))
                .extracting(
                        JobReport::end, JobReport::segments, JobReport::accepted, JobReport::flagged, JobReport::error)
                .containsExactly(JobState.FAILED, 3, 1, 0, unreachable);
        assertThat(model.requests())
                .extracting(request -> request.messages().get(1).content())
                .containsExactly("One.", "Two.");
        assertThat(Files.exists(destination)).isFalse();
    }

    // Advancing the cursor after failure would omit the repeated Two request in this exact sequence.
    @Test
    void pauseAt_modelError_retriesFailedSegmentOnly() {
        final AppError unreachable = unreachable();
        final ScriptedChatModel model = replies("ONE.")
                .answer(Result.err(unreachable))
                .answer(Result.ok(response("TWO.")))
                .answer(Result.ok(response("THREE.")));
        final TranslationJobImpl translation =
                markdownJob(model, tempDir.resolve("Book.uk.md"), "One.\n\nTwo.\n\nThree.");
        final LinkedBlockingQueue<Paused> pauses = new LinkedBlockingQueue<>();
        translation.subscribe(event -> capturePaused(pauses, event));
        translation.pauseAt(Set.of(PausePoint.ON_ERROR));
        final ExecutorService workers = executor();

        final Future<Result<JobReport>> run = workers.submit(translation::run);
        final Paused pause = awaitPaused(pauses);
        translation.resume();

        assertThat(pause).extracting(Paused::reason, Paused::error).containsExactly(PauseReason.ON_ERROR, unreachable);
        assertThat(pause.progress())
                .extracting(p -> p.accepted(), p -> p.flagged(), p -> p.pending())
                .containsExactly(1, 0, 2);
        assertThat(report(await(run)))
                .extracting(JobReport::end, JobReport::accepted, JobReport::flagged)
                .containsExactly(JobState.COMPLETED, 3, 0);
        assertThat(model.requests())
                .extracting(request -> request.messages().get(1).content())
                .containsExactly("One.", "Two.", "Two.", "Three.");
        shutdown(workers);
    }

    // Letting ON_ERROR outrank a manual pause would change the reason and discard its original error.
    @Test
    void pause_requestedDuringFailingCall_winsAndRetainsError() {
        final AppError unreachable = unreachable();
        final ScriptedChatModel scripted = replies("ONE.")
                .answer(Result.err(unreachable))
                .answer(Result.ok(response("TWO.")))
                .answer(Result.ok(response("THREE.")));
        final TranslationJobTestSupport.SecondBlockingChatModel model =
                new TranslationJobTestSupport.SecondBlockingChatModel(scripted);
        final TranslationJobImpl translation =
                markdownJob(model, tempDir.resolve("Book.uk.md"), "One.\n\nTwo.\n\nThree.");
        final LinkedBlockingQueue<Paused> pauses = new LinkedBlockingQueue<>();
        translation.subscribe(event -> capturePaused(pauses, event));
        translation.pauseAt(Set.of(PausePoint.ON_ERROR));
        final ExecutorService workers = executor();

        final Future<Result<JobReport>> run = workers.submit(translation::run);
        model.awaitSecondCall();
        translation.pause();
        model.releaseSecondCall();
        final Paused pause = awaitPaused(pauses);
        translation.resume();

        assertThat(pause).extracting(Paused::reason, Paused::error).containsExactly(PauseReason.REQUESTED, unreachable);
        assertThat(report(await(run)).end()).isEqualTo(JobState.COMPLETED);
        assertThat(scripted.requests())
                .extracting(request -> request.messages().get(1).content())
                .containsExactly("One.", "Two.", "Two.", "Three.");
        shutdown(workers);
    }

    // Re-emitting EXPORT StageStarted on retry would make this list contain two export starts.
    @Test
    void pauseAt_exportError_retriesExportWithoutRepeatingTranslation() throws Exception {
        final Path source = markdown("One.\n\nTwo.");
        final Path output = Files.createDirectory(tempDir.resolve("output"));
        final Path destination = output.resolve("Book.uk.md");
        final ScriptedChatModel model = replies("ONE.", "TWO.");
        final TranslationJobImpl translation = job(documents(), source, destination, model);
        final LinkedBlockingQueue<Paused> pauses = new LinkedBlockingQueue<>();
        final List<JobEvent> events = new ArrayList<>();
        translation.subscribe(event -> deleteOutputOnExportStart(output, pauses, events, event));
        translation.pauseAt(Set.of(PausePoint.ON_ERROR));
        final ExecutorService workers = executor();

        final Future<Result<JobReport>> run = workers.submit(translation::run);
        final Paused pause = awaitPaused(pauses);
        Files.createDirectory(output);
        translation.resume();
        final JobReport completed = report(await(run));

        assertThat(pause.reason()).isEqualTo(PauseReason.ON_ERROR);
        assertThat(pause.error()).extracting(AppError::code).isEqualTo(ErrorCode.internal);
        assertThat(pause.progress())
                .extracting(p -> p.stage(), p -> p.pending())
                .containsExactly(JobStage.EXPORT, 0);
        assertThat(events)
                .filteredOn(StageStarted.class::isInstance)
                .extracting(event -> ((StageStarted) event).stage())
                .containsExactly(JobStage.TRANSLATE, JobStage.EXPORT);
        assertThat(model.requests()).hasSize(2);
        assertThat(completed.end()).isEqualTo(JobState.COMPLETED);
        assertThat(Files.readString(destination)).isEqualTo("ONE.\n\nTWO.");
        shutdown(workers);
    }

    // Publishing a partial book before the failed export is retried would leave a destination while the job waits.
    @Test
    void pauseAt_exportError_leavesNoDestinationWhilePaused() throws Exception {
        final Path source = markdown("One.");
        final Path output = Files.createDirectory(tempDir.resolve("output"));
        final Path destination = output.resolve("Book.uk.md");
        final TranslationJobImpl translation = job(documents(), source, destination, replies("ONE."));
        final LinkedBlockingQueue<Paused> pauses = new LinkedBlockingQueue<>();
        final List<JobEvent> events = new ArrayList<>();
        translation.subscribe(event -> deleteOutputOnExportStart(output, pauses, events, event));
        translation.pauseAt(Set.of(PausePoint.ON_ERROR));
        final ExecutorService workers = executor();

        final Future<Result<JobReport>> run = workers.submit(translation::run);
        awaitPaused(pauses);
        final boolean destinationWhilePaused = Files.exists(destination);
        translation.cancel();

        assertThat(destinationWhilePaused).isFalse();
        assertThat(report(await(run)).end()).isEqualTo(JobState.CANCELLED);
        assertThat(destination).doesNotExist();
        shutdown(workers);
    }

    // Retaining a pause request received during export would relabel this failure REQUESTED.
    @Test
    void pause_duringExportDoesNotChangeExportErrorReason() throws Exception {
        final Path source = markdown("One.");
        final Path output = Files.createDirectory(tempDir.resolve("output"));
        final TranslationJobImpl translation = job(documents(), source, output.resolve("Book.uk.md"), replies("ONE."));
        final LinkedBlockingQueue<Paused> pauses = new LinkedBlockingQueue<>();
        translation.subscribe(event -> pauseAndDeleteAtExportStart(translation, output, pauses, event));
        translation.pauseAt(Set.of(PausePoint.ON_ERROR));
        final ExecutorService workers = executor();

        final Future<Result<JobReport>> run = workers.submit(translation::run);
        final Paused pause = awaitPaused(pauses);
        Files.createDirectory(output);
        translation.resume();

        assertThat(pause.reason()).isEqualTo(PauseReason.ON_ERROR);
        assertThat(pause.error()).extracting(AppError::code).isEqualTo(ErrorCode.internal);
        assertThat(report(await(run)).end()).isEqualTo(JobState.COMPLETED);
        shutdown(workers);
    }

    // Keeping ON_ERROR from the consumed pause would create a second pause instead of this failed report.
    @Test
    void pauseAt_removedDuringErrorPause_failedRetryEndsFailed() {
        final AppError unreachable = unreachable();
        final ScriptedChatModel model =
                replies().answer(Result.err(unreachable)).answer(Result.err(unreachable));
        final TranslationJobImpl translation = markdownJob(model, tempDir.resolve("Book.uk.md"), "One.");
        final LinkedBlockingQueue<Paused> pauses = new LinkedBlockingQueue<>();
        translation.subscribe(event -> capturePaused(pauses, event));
        translation.pauseAt(Set.of(PausePoint.ON_ERROR));
        final ExecutorService workers = executor();

        final Future<Result<JobReport>> run = workers.submit(translation::run);
        awaitPaused(pauses);
        translation.pauseAt(Set.of());
        translation.resume();

        assertThat(report(await(run)))
                .extracting(JobReport::end, JobReport::error)
                .containsExactly(JobState.FAILED, unreachable);
        assertThat(pauses).isEmpty();
        assertThat(model.requests())
                .extracting(request -> request.messages().get(1).content())
                .containsExactly("One.", "One.");
        shutdown(workers);
    }

    private TranslationJobImpl markdownJob(
            final ua.bookloom.api.llm.ChatModel model, final Path destination, final String content) {
        return job(documents(), markdown(content), destination, model);
    }

    private Path markdown(final String content) {
        return TestBooks.markdown(tempDir.resolve("Book.md"), content);
    }

    private static ChatResponse response(final String content) {
        return new ChatResponse(content, FinishReason.STOP);
    }

    private static AppError unreachable() {
        return AppError.of(ErrorCode.unreachable, "Offline", "The model is unreachable.");
    }

    private static void deleteOutputOnExportStart(
            final Path output,
            final LinkedBlockingQueue<Paused> pauses,
            final List<JobEvent> events,
            final JobEvent event) {
        events.add(event);
        capturePaused(pauses, event);
        if (event instanceof StageStarted started && started.stage() == JobStage.EXPORT) {
            delete(output);
        }
    }

    private static void pauseAndDeleteAtExportStart(
            final TranslationJobImpl translation,
            final Path output,
            final LinkedBlockingQueue<Paused> pauses,
            final JobEvent event) {
        capturePaused(pauses, event);
        if (event instanceof StageStarted started && started.stage() == JobStage.EXPORT) {
            translation.pause();
            delete(output);
        }
    }

    private static void delete(final Path output) {
        try {
            Files.delete(output);
        } catch (IOException cause) {
            throw new UncheckedIOException(cause);
        }
    }
}
