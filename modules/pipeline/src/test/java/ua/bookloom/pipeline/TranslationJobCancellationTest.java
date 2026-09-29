package ua.bookloom.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.pipeline.TranslationJobTestSupport.await;
import static ua.bookloom.pipeline.TranslationJobTestSupport.awaitPaused;
import static ua.bookloom.pipeline.TranslationJobTestSupport.capturePaused;
import static ua.bookloom.pipeline.TranslationJobTestSupport.executor;
import static ua.bookloom.pipeline.TranslationJobTestSupport.job;
import static ua.bookloom.pipeline.TranslationJobTestSupport.replies;
import static ua.bookloom.pipeline.TranslationJobTestSupport.report;
import static ua.bookloom.pipeline.TranslationJobTestSupport.shutdown;

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

/** Proves cooperative cancellation preserves the decisions already stored. */
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
                        JobReport::end, JobReport::segments, JobReport::accepted, JobReport::flagged, JobReport::error)
                .containsExactly(JobState.CANCELLED, 3, 1, 0, null);
        assertThat(events).noneMatch(Resumed.class::isInstance);
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
        shutdown(workers);
    }

    // Checking cancellation before preserving a completed answer would leave accepted at zero.
    @Test
    void cancel_duringModelCall_decidesCompletedAnswerThenStopsAtBoundary() {
        final ScriptedChatModel scripted = replies("ONE.", "TWO.", "THREE.");
        final BlockingModels.BlockingChatModel model = new BlockingModels.BlockingChatModel(scripted);
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
        shutdown(workers);
    }

    // Allowing ON_ERROR to win this race would produce a Paused event instead of cancellation.
    @Test
    void cancel_duringFailingModelCall_winsOverPauseOnError() {
        final AppError unreachable = AppError.of(ErrorCode.unreachable, "Offline", "The model is unreachable.");
        final ScriptedChatModel scripted = replies().answer(Result.err(unreachable));
        final BlockingModels.BlockingChatModel model = new BlockingModels.BlockingChatModel(scripted);
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

    private TranslationJobImpl markdownJob(final ua.bookloom.api.llm.ChatModel model, final String content) {
        return job(markdown(content), model);
    }

    private Path markdown(final String content) {
        return TestBooks.markdown(tempDir.resolve("Book.md"), content);
    }

    private static void captureAndRecord(
            final LinkedBlockingQueue<Paused> pauses, final List<JobEvent> events, final JobEvent event) {
        events.add(event);
        capturePaused(pauses, event);
    }

    private static InterruptObservation runAndObserveInterrupt(
            final TranslationJobImpl translation, final AtomicReference<Thread> workerThread) {
        workerThread.set(Thread.currentThread());
        final Result<JobReport> result = translation.run();
        return new InterruptObservation(result, Thread.currentThread().isInterrupted());
    }

    private record InterruptObservation(Result<JobReport> result, boolean interrupted) {}
}
