package ua.bookloom.ui.state;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.Result;
import ua.bookloom.api.pipeline.JobEvent;
import ua.bookloom.api.pipeline.JobListener;
import ua.bookloom.api.pipeline.JobReport;
import ua.bookloom.api.pipeline.JobState;
import ua.bookloom.api.pipeline.PausePoint;
import ua.bookloom.api.pipeline.Subscription;
import ua.bookloom.api.pipeline.TranslationJob;

/**
 * A hand-written {@link TranslationJob} whose {@code run()} does exactly what a test queues for it, on the thread the
 * runner gave it, so events reach the listener from the job thread the way the real engine delivers them.
 *
 * <p>Public so the screen tests in other packages can hand it to the scripted engine and read what the buttons did.
 *
 * <p>It records the thread {@code run()} executed on, whether a listener was already subscribed when {@code run()}
 * began, and every control call, so a test can assert the runner's ordering without a mock framework.
 */
public final class RecordingJob implements TranslationJob {

    private static final long WAIT_SECONDS = 10;

    /** One thing the job thread does; a non-null result ends {@code run()} with it. */
    @FunctionalInterface
    private interface Step {
        @Nullable
        Result<JobReport> execute();
    }

    private final LinkedBlockingQueue<Step> steps = new LinkedBlockingQueue<>();
    private final List<String> calls = new CopyOnWriteArrayList<>();
    private final AtomicReference<JobListener> listener = new AtomicReference<>();
    private final CountDownLatch started = new CountDownLatch(1);
    private volatile @Nullable Thread runThread;
    private volatile boolean subscribedBeforeRun;
    private volatile @Nullable RuntimeException subscribeFailure;
    private volatile Runnable onControl = () -> {};

    @Override
    public Result<JobReport> run() {
        calls.add("run");
        runThread = Thread.currentThread();
        subscribedBeforeRun = listener.get() != null;
        started.countDown();
        return runQueuedSteps();
    }

    private Result<JobReport> runQueuedSteps() {
        try {
            while (true) {
                final Step step = steps.poll(WAIT_SECONDS, TimeUnit.SECONDS);
                if (step == null) {
                    throw new IllegalStateException("the test queued nothing for the fake job to do");
                }
                final Result<JobReport> outcome = step.execute();
                if (outcome != null) {
                    return outcome;
                }
            }
        } catch (InterruptedException cause) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("the fake job was interrupted", cause);
        }
    }

    /** Queues an event for the job thread to deliver to the subscribed listener. */
    public void emit(final JobEvent event) {
        steps.add(() -> {
            Objects.requireNonNull(listener.get(), "a listener must be subscribed")
                    .onEvent(event);
            return null;
        });
    }

    /** Queues the end of {@code run()} with the given returned result. */
    public void finish(final Result<JobReport> result) {
        steps.add(() -> result);
    }

    /** Queues the end of {@code run()} by throwing. */
    void finishThrowing(final RuntimeException thrown) {
        steps.add(() -> {
            throw thrown;
        });
    }

    /** Queues the end of {@code run()} by throwing an {@link Error}. */
    void finishThrowing(final Error thrown) {
        steps.add(() -> {
            throw thrown;
        });
    }

    /** Blocks the caller until the job thread has processed everything queued so far. */
    public void drain() throws InterruptedException {
        final CountDownLatch processed = new CountDownLatch(1);
        steps.add(() -> {
            processed.countDown();
            return null;
        });
        if (!processed.await(WAIT_SECONDS, TimeUnit.SECONDS)) {
            throw new IllegalStateException("the fake job did not process its queue");
        }
    }

    /** Blocks the caller until {@code run()} has begun. */
    public void awaitRunStarted() throws InterruptedException {
        if (!started.await(WAIT_SECONDS, TimeUnit.SECONDS)) {
            throw new IllegalStateException("run() was never called");
        }
    }

    /** Runs {@code action} inside every {@code pause()} and {@code cancel()}, to observe what the runner did first. */
    void onControl(final Runnable action) {
        onControl = action;
    }

    public List<String> calls() {
        return List.copyOf(calls);
    }

    Thread runThread() {
        return Objects.requireNonNull(runThread, "run() has not started");
    }

    boolean wasSubscribedBeforeRun() {
        return subscribedBeforeRun;
    }

    @Override
    public void pause() {
        calls.add("pause");
        onControl.run();
    }

    @Override
    public void resume() {
        calls.add("resume");
    }

    @Override
    public void cancel() {
        calls.add("cancel");
        onControl.run();
    }

    @Override
    public void pauseAt(final Set<PausePoint> points) {
        calls.add("pauseAt");
    }

    @Override
    public JobState state() {
        return JobState.RUNNING;
    }

    /** Makes the next {@code subscribe} throw, the way a job that cannot accept a listener would. */
    void failOnSubscribe(final RuntimeException failure) {
        subscribeFailure = failure;
    }

    @Override
    public Subscription subscribe(final JobListener subscriber) {
        calls.add("subscribe");
        final RuntimeException failure = subscribeFailure;
        if (failure != null) {
            throw failure;
        }
        listener.set(subscriber);
        return () -> calls.add("unsubscribe");
    }
}
