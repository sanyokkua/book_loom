package ua.bookloom.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static ua.bookloom.pipeline.TranslationJobTestSupport.await;
import static ua.bookloom.pipeline.TranslationJobTestSupport.awaitPaused;
import static ua.bookloom.pipeline.TranslationJobTestSupport.capturePaused;
import static ua.bookloom.pipeline.TranslationJobTestSupport.documents;
import static ua.bookloom.pipeline.TranslationJobTestSupport.executor;
import static ua.bookloom.pipeline.TranslationJobTestSupport.job;
import static ua.bookloom.pipeline.TranslationJobTestSupport.replies;
import static ua.bookloom.pipeline.TranslationJobTestSupport.report;
import static ua.bookloom.pipeline.TranslationJobTestSupport.shutdown;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.api.Result;
import ua.bookloom.api.pipeline.JobEvent;
import ua.bookloom.api.pipeline.JobReport;
import ua.bookloom.api.pipeline.JobStage;
import ua.bookloom.api.pipeline.JobState;
import ua.bookloom.api.pipeline.PausePoint;
import ua.bookloom.api.pipeline.PauseReason;
import ua.bookloom.api.pipeline.Paused;
import ua.bookloom.api.pipeline.Resumed;
import ua.bookloom.api.pipeline.SegmentDecided;
import ua.bookloom.api.pipeline.StageStarted;

/** Proves changing controls affects the next boundary without reusing the current one. */
class TranslationJobPauseControlTest {

    @TempDir
    private Path tempDir;

    @AfterEach
    void cleanUpWorkers() {
        TranslationJobTestSupport.shutdownAll();
    }

    // Ignoring the manual request would pick BETWEEN_STAGES at this coincident boundary.
    @Test
    void pause_requestedAtCoincidentBoundary_winsPriority() {
        final TranslationJobImpl translation = markdownJob(replies("ONE."), "One.");
        final LinkedBlockingQueue<Paused> pauses = new LinkedBlockingQueue<>();
        translation.subscribe(event -> requestAndCapture(translation, pauses, event));
        translation.pauseAt(Set.of(PausePoint.AFTER_SEGMENT, PausePoint.AFTER_SECTION, PausePoint.BETWEEN_STAGES));
        final ExecutorService workers = executor();

        final Future<Result<JobReport>> run = workers.submit(translation::run);
        final Paused pause = awaitPaused(pauses);
        translation.resume();

        assertThat(pause.reason()).isEqualTo(PauseReason.REQUESTED);
        assertThat(report(await(run)).end()).isEqualTo(JobState.COMPLETED);
        shutdown(workers);
    }

    // Reversing section and segment priority would name the two end-of-section pauses incorrectly.
    @Test
    void pauseAt_afterSectionAndSegment_usesSectionAtSectionEnd() {
        final Path source =
                TestBooks.epub(tempDir.resolve("Book.epub"), List.of(List.of("One.", "Two."), List.of("Three.")), "en");
        final TranslationJobImpl translation =
                job(documents(), source, tempDir.resolve("Book.uk.epub"), replies("ONE.", "TWO.", "THREE."));
        final LinkedBlockingQueue<Paused> pauses = new LinkedBlockingQueue<>();
        translation.subscribe(event -> capturePaused(pauses, event));
        translation.pauseAt(Set.of(PausePoint.AFTER_SEGMENT, PausePoint.AFTER_SECTION));
        final ExecutorService workers = executor();

        final Future<Result<JobReport>> run = workers.submit(translation::run);
        final Paused first = awaitPaused(pauses);
        translation.resume();
        final Paused second = awaitPaused(pauses);
        translation.resume();
        final Paused third = awaitPaused(pauses);
        translation.resume();

        assertThat(first.reason()).isEqualTo(PauseReason.AFTER_SEGMENT);
        assertThat(second.reason()).isEqualTo(PauseReason.AFTER_SECTION);
        assertThat(third.reason()).isEqualTo(PauseReason.AFTER_SECTION);
        assertThat(report(await(run)).end()).isEqualTo(JobState.COMPLETED);
        shutdown(workers);
    }

    // Re-reading the current boundary after a point change would add a second first-segment pause.
    @Test
    void pauseAt_clearedWhilePaused_appliesFromNextBoundary() {
        final TranslationJobImpl translation = markdownJob(replies("ONE.", "TWO.", "THREE."), "One.\n\nTwo.\n\nThree.");
        final LinkedBlockingQueue<Paused> pauses = new LinkedBlockingQueue<>();
        translation.subscribe(event -> capturePaused(pauses, event));
        translation.pauseAt(Set.of(PausePoint.AFTER_SEGMENT));
        final ExecutorService workers = executor();

        final Future<Result<JobReport>> run = workers.submit(translation::run);
        awaitPaused(pauses);
        translation.pauseAt(Set.of());
        translation.resume();

        assertThat(report(await(run)).end()).isEqualTo(JobState.COMPLETED);
        assertThat(pauses).isEmpty();
        shutdown(workers);
    }

    // Reading pause points before the model returns would miss this live AFTER_SEGMENT update.
    @Test
    void pauseAt_enabledDuringModelCall_appliesAtNextBoundary() {
        final TranslationJobTestSupport.BlockingChatModel model =
                new TranslationJobTestSupport.BlockingChatModel(replies("ONE.", "TWO.", "THREE."));
        final TranslationJobImpl translation = markdownJob(model, "One.\n\nTwo.\n\nThree.");
        final LinkedBlockingQueue<Paused> pauses = new LinkedBlockingQueue<>();
        translation.subscribe(event -> capturePaused(pauses, event));
        final ExecutorService workers = executor();

        final Future<Result<JobReport>> run = workers.submit(translation::run);
        model.awaitEntered();
        translation.pauseAt(Set.of(PausePoint.AFTER_SEGMENT));
        model.release();
        final Paused pause = awaitPaused(pauses);
        translation.pauseAt(Set.of());
        translation.resume();

        assertThat(pause.progress())
                .extracting(p -> p.accepted(), p -> p.pending())
                .containsExactly(1, 2);
        assertThat(pause.reason()).isEqualTo(PauseReason.AFTER_SEGMENT);
        assertThat(report(await(run)).end()).isEqualTo(JobState.COMPLETED);
        shutdown(workers);
    }

    // Applying a replacement to the consumed boundary would pause after segment one a second time.
    @Test
    void pauseAt_changedWhilePaused_usesReplacementAtFollowingBoundary() {
        final TranslationJobImpl translation = markdownJob(replies("ONE.", "TWO.", "THREE."), "One.\n\nTwo.\n\nThree.");
        final LinkedBlockingQueue<Paused> pauses = new LinkedBlockingQueue<>();
        translation.subscribe(event -> capturePaused(pauses, event));
        translation.pauseAt(Set.of(PausePoint.AFTER_SEGMENT));
        final ExecutorService workers = executor();

        final Future<Result<JobReport>> run = workers.submit(translation::run);
        final Paused first = awaitPaused(pauses);
        translation.pauseAt(Set.of(PausePoint.BETWEEN_STAGES));
        translation.resume();
        final Paused second = awaitPaused(pauses);
        translation.resume();

        assertThat(first.reason()).isEqualTo(PauseReason.AFTER_SEGMENT);
        assertThat(second.reason()).isEqualTo(PauseReason.BETWEEN_STAGES);
        assertThat(second.progress().pending()).isZero();
        assertThat(report(await(run)).end()).isEqualTo(JobState.COMPLETED);
        shutdown(workers);
    }

    // Waiting without a predicate would lose the resume signal sent by this callback.
    @Test
    void resume_insidePausedCallback_doesNotLoseSignal() {
        final TranslationJobImpl translation = markdownJob(replies("ONE."), "One.");
        final List<JobEvent> events = new ArrayList<>();
        translation.subscribe(event -> resumeAndRecord(translation, events, event));
        translation.pauseAt(Set.of(PausePoint.AFTER_SEGMENT));

        final Result<JobReport> result = assertTimeoutPreemptively(Duration.ofSeconds(5), translation::run);

        assertThat(report(result).end()).isEqualTo(JobState.COMPLETED);
        assertThat(events)
                .extracting(event -> event.getClass().getSimpleName())
                .containsSubsequence("Paused", "Resumed");
    }

    // Moving delivery off the run thread would make this callback thread differ from the submitting worker.
    @Test
    void resume_fromAnotherThread_wakesPausedRunAndKeepsCallbacksOnRunThread() {
        final TranslationJobImpl translation = markdownJob(replies("ONE."), "One.");
        final LinkedBlockingQueue<Paused> pauses = new LinkedBlockingQueue<>();
        final AtomicReference<Thread> runThread = new AtomicReference<>();
        final List<Thread> callbackThreads = new ArrayList<>();
        translation.subscribe(event -> recordThreads(pauses, callbackThreads, event));
        translation.pauseAt(Set.of(PausePoint.AFTER_SEGMENT));
        final ExecutorService workers = executor();

        final Future<Result<JobReport>> run = workers.submit(() -> runOnCurrentThread(translation, runThread));
        awaitPaused(pauses);
        translation.resume();

        assertThat(report(await(run)).end()).isEqualTo(JobState.COMPLETED);
        assertThat(callbackThreads).allMatch(thread -> thread.equals(runThread.get()));
        shutdown(workers);
    }

    // Leaving pause armed after export begins would add a Paused event during this stage callback.
    @Test
    @Timeout(10)
    void pause_afterExportStageStarts_isIgnored() {
        final TranslationJobImpl translation = markdownJob(replies("ONE."), "One.");
        final List<JobEvent> events = new ArrayList<>();
        translation.subscribe(event -> pauseAtExportStart(translation, events, event));

        final Result<JobReport> result = translation.run();

        assertThat(report(result).end()).isEqualTo(JobState.COMPLETED);
        assertThat(events).noneMatch(Paused.class::isInstance);
        assertThat(events).noneMatch(Resumed.class::isInstance);
    }

    private TranslationJobImpl markdownJob(final ua.bookloom.api.llm.ChatModel model, final String content) {
        final Path source = TestBooks.markdown(tempDir.resolve("Book.md"), content);
        return job(documents(), source, tempDir.resolve("Book.uk.md"), model);
    }

    private static void requestAndCapture(
            final TranslationJobImpl translation, final LinkedBlockingQueue<Paused> pauses, final JobEvent event) {
        capturePaused(pauses, event);
        if (event instanceof SegmentDecided) {
            translation.pause();
        }
    }

    private static void resumeAndRecord(
            final TranslationJobImpl translation, final List<JobEvent> events, final JobEvent event) {
        events.add(event);
        if (event instanceof Paused) {
            translation.resume();
        }
    }

    private static void recordThreads(
            final LinkedBlockingQueue<Paused> pauses, final List<Thread> callbackThreads, final JobEvent event) {
        callbackThreads.add(Thread.currentThread());
        capturePaused(pauses, event);
    }

    private static Result<JobReport> runOnCurrentThread(
            final TranslationJobImpl translation, final AtomicReference<Thread> runThread) {
        runThread.set(Thread.currentThread());
        return translation.run();
    }

    private static void pauseAtExportStart(
            final TranslationJobImpl translation, final List<JobEvent> events, final JobEvent event) {
        events.add(event);
        if (event instanceof StageStarted started && started.stage() == JobStage.EXPORT) {
            translation.pause();
        }
    }
}
