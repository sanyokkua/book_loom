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

import java.nio.file.CopyOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.pipeline.JobEvent;
import ua.bookloom.api.pipeline.JobReport;
import ua.bookloom.api.pipeline.JobState;
import ua.bookloom.api.pipeline.PausePoint;
import ua.bookloom.api.pipeline.Paused;
import ua.bookloom.api.pipeline.Resumed;
import ua.bookloom.api.pipeline.SegmentDecided;
import ua.bookloom.api.pipeline.TranslationRequest;

/** Proves cooperative cancellation preserves decisions and reaches the exporter final check. */
class TranslationJobCancellationTest {

    @TempDir
    private Path tempDir;

    @AfterEach
    void cleanUpWorkers() {
        TranslationJobTestSupport.shutdownAll();
    }

    // Leaving the pause predicate asleep after cancel would hang this run instead of returning its partial report.
    @Test
    void cancel_whilePaused_wakesAndPreservesDecisions() {
        final TranslationJobImpl translation = markdownJob(replies("ONE.", "TWO.", "THREE."), "One.\n\nTwo.\n\nThree.");
        final LinkedBlockingQueue<Paused> pauses = new LinkedBlockingQueue<>();
        final List<JobEvent> events = new ArrayList<>();
        translation.subscribe(event -> captureAndRecord(pauses, events, event));
        translation.pauseAt(Set.of(PausePoint.AFTER_SEGMENT));
        final ExecutorService workers = executor();

        final Future<Result<JobReport>> run = workers.submit(translation::run);
        awaitPaused(pauses);
        translation.cancel();

        assertThat(report(await(run)))
                .extracting(
                        JobReport::end,
                        JobReport::segments,
                        JobReport::accepted,
                        JobReport::flagged,
                        JobReport::written,
                        JobReport::error)
                .containsExactly(JobState.CANCELLED, 3, 1, 0, null, null);
        assertThat(events).noneMatch(Resumed.class::isInstance);
        assertThat(Files.exists(tempDir.resolve("Book.uk.md"))).isFalse();
        shutdown(workers);
    }

    // Dropping interrupt restoration would leave this observation false after the paused job returns.
    @Test
    void run_interruptedWhilePaused_cancelsAndRestoresInterrupt() {
        final TranslationJobImpl translation = markdownJob(replies("ONE."), "One.");
        final LinkedBlockingQueue<Paused> pauses = new LinkedBlockingQueue<>();
        final AtomicReference<Thread> workerThread = new AtomicReference<>();
        translation.subscribe(event -> capturePaused(pauses, event));
        translation.pauseAt(Set.of(PausePoint.AFTER_SEGMENT));
        final ExecutorService workers = executor();

        final Future<InterruptObservation> run =
                workers.submit(() -> runAndObserveInterrupt(translation, workerThread));
        awaitPaused(pauses);
        Objects.requireNonNull(workerThread.get(), "paused worker").interrupt();

        final InterruptObservation observed = await(run);
        assertThat(report(observed.result()).end()).isEqualTo(JobState.CANCELLED);
        assertThat(observed.interrupted()).isTrue();
        assertThat(Files.exists(tempDir.resolve("Book.uk.md"))).isFalse();
        shutdown(workers);
    }

    // Checking cancellation before preserving a completed answer would leave accepted at zero.
    @Test
    void cancel_duringModelCall_decidesCompletedAnswerThenStopsAtBoundary() {
        final ScriptedChatModel scripted = replies("ONE.", "TWO.", "THREE.");
        final TranslationJobTestSupport.BlockingChatModel model =
                new TranslationJobTestSupport.BlockingChatModel(scripted);
        final TranslationJobImpl translation = markdownJob(model, "One.\n\nTwo.\n\nThree.");
        final List<JobEvent> events = new ArrayList<>();
        translation.subscribe(events::add);
        final ExecutorService workers = executor();

        final Future<Result<JobReport>> run = workers.submit(translation::run);
        model.awaitEntered();
        translation.cancel();
        model.release();

        assertThat(report(await(run)))
                .extracting(JobReport::end, JobReport::accepted)
                .containsExactly(JobState.CANCELLED, 1);
        assertThat(events).filteredOn(SegmentDecided.class::isInstance).hasSize(1);
        assertThat(scripted.requests()).hasSize(1);
        assertThat(Files.exists(tempDir.resolve("Book.uk.md"))).isFalse();
        shutdown(workers);
    }

    // Allowing ON_ERROR to win this race would produce a Paused event instead of cancellation.
    @Test
    void cancel_duringFailingModelCall_winsOverPauseOnError() {
        final AppError unreachable = AppError.of(ErrorCode.unreachable, "Offline", "The model is unreachable.");
        final ScriptedChatModel scripted = replies().answer(Result.err(unreachable));
        final TranslationJobTestSupport.BlockingChatModel model =
                new TranslationJobTestSupport.BlockingChatModel(scripted);
        final TranslationJobImpl translation = markdownJob(model, "One.");
        final List<JobEvent> events = new ArrayList<>();
        translation.subscribe(events::add);
        translation.pauseAt(Set.of(PausePoint.ON_ERROR));
        final ExecutorService workers = executor();

        final Future<Result<JobReport>> run = workers.submit(translation::run);
        model.awaitEntered();
        translation.cancel();
        model.release();

        assertThat(report(await(run)))
                .extracting(JobReport::end, JobReport::accepted)
                .containsExactly(JobState.CANCELLED, 0);
        assertThat(events).noneMatch(Paused.class::isInstance);
        assertThat(scripted.requests()).hasSize(1);
        shutdown(workers);
    }

    // Omitting the exporter's final cancellation supplier would publish this destination after cancellation.
    @Test
    void cancel_beforePublication_isObservedByExporterFinalCheck() {
        final Path source = markdown("One.");
        final Path destination = tempDir.resolve("Book.uk.md");
        final TranslationJobTestSupport.BlockingClosePort port =
                new TranslationJobTestSupport.BlockingClosePort(documents(), 3);
        final TranslationJobImpl translation = job(port, source, destination, replies("ONE."));
        translation.pauseAt(Set.of(PausePoint.ON_ERROR));
        final ExecutorService workers = executor();

        final Future<Result<JobReport>> run = workers.submit(translation::run);
        port.awaitBlockingClose();
        translation.cancel();
        port.releaseClose();

        assertThat(report(await(run)))
                .extracting(JobReport::end, JobReport::written)
                .containsExactly(JobState.CANCELLED, null);
        assertThat(Files.exists(destination)).isFalse();
        assertThat(Files.exists(tempDir.resolve(".Book.uk.md"))).isFalse();
        shutdown(workers);
    }

    // Cancellation after the real publication move must not replace the successful terminal report.
    @Test
    void cancel_afterPublication_keepsCompletedReportAndWrittenBook() {
        final Path source = markdown("One.");
        final Path destination = tempDir.resolve("Book.uk.md");
        final AtomicReference<TranslationJobImpl> reference = new AtomicReference<>();
        final ExportMoveOperation moves =
                (temporary, published, options) -> publishThenCancel(reference, temporary, published, options);
        final TranslationJobImpl translation = new TranslationJobImpl(
                documents(), new TranslationRequest(source, destination, "uk", "en", false), replies("ONE."), moves);
        reference.set(translation);

        final JobReport result = report(translation.run());

        assertThat(result)
                .extracting(JobReport::end, JobReport::written)
                .containsExactly(JobState.COMPLETED, destination);
        assertThat(translation.state()).isEqualTo(JobState.COMPLETED);
        assertThat(Files.exists(destination)).isTrue();
    }

    private TranslationJobImpl markdownJob(final ua.bookloom.api.llm.ChatModel model, final String content) {
        return job(documents(), markdown(content), tempDir.resolve("Book.uk.md"), model);
    }

    private Path markdown(final String content) {
        return TestBooks.markdown(tempDir.resolve("Book.md"), content);
    }

    private static void captureAndRecord(
            final LinkedBlockingQueue<Paused> pauses, final List<JobEvent> events, final JobEvent event) {
        events.add(event);
        capturePaused(pauses, event);
    }

    private static Path publishThenCancel(
            final AtomicReference<TranslationJobImpl> reference,
            final Path temporary,
            final Path destination,
            final CopyOption... options) {
        final Path published = ExportMoveOperation.nio().move(temporary, destination, options);
        Objects.requireNonNull(reference.get(), "translation job").cancel();
        return published;
    }

    private static InterruptObservation runAndObserveInterrupt(
            final TranslationJobImpl translation, final AtomicReference<Thread> workerThread) {
        workerThread.set(Thread.currentThread());
        final Result<JobReport> result = translation.run();
        return new InterruptObservation(result, Thread.currentThread().isInterrupted());
    }

    private record InterruptObservation(Result<JobReport> result, boolean interrupted) {}
}
