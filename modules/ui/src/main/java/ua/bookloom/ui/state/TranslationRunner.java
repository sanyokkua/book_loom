package ua.bookloom.ui.state;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.time.Clock;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicReference;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ModelSelection;
import ua.bookloom.api.pipeline.JobReport;
import ua.bookloom.api.pipeline.Subscription;
import ua.bookloom.api.pipeline.TranslationJob;
import ua.bookloom.api.pipeline.TranslationRequest;
import ua.bookloom.ui.BackgroundExecutor;

/**
 * Runs one translation job at a time on the background executor and feeds the {@link StateMirror} from it.
 *
 * <p>A plain submission rather than a JavaFX {@code Task}: {@link TranslationJob#run()} reports failure by
 * <em>returning</em> it, so {@code Task.setOnFailed} would never fire and the failure would still have to be read off
 * the returned {@link Result}. That returned result, never a {@code Finished} event, decides the terminal state,
 * because a run refused before it starts emits no event at all. Every method here is non-blocking and safe to call
 * from the FX thread.
 */
@Slf4j
@Singleton
public final class TranslationRunner {

    /** The run the runner is busy with; the job is kept beside its session so a control reaches both. */
    private record ActiveRun(TranslationJob job, RunSession session) {}

    private final StateMirror mirror;
    private final ExecutorService executor;
    private final TickSource ticks;
    private final Clock clock;
    private final AtomicReference<@Nullable ActiveRun> active = new AtomicReference<>();

    /**
     * Creates the runner with the production 100 ms cadence.
     *
     * @param mirror the mirror every run publishes into
     * @param executor the daemon executor a job runs on, never the FX thread
     */
    @Inject
    public TranslationRunner(final StateMirror mirror, @BackgroundExecutor final ExecutorService executor) {
        this(mirror, executor, new FixedRateTicks());
    }

    TranslationRunner(final StateMirror mirror, final ExecutorService executor, final TickSource ticks) {
        this(mirror, executor, ticks, Clock.systemUTC());
    }

    TranslationRunner(
            final StateMirror mirror, final ExecutorService executor, final TickSource ticks, final Clock clock) {
        this.mirror = Objects.requireNonNull(mirror, "mirror");
        this.executor = Objects.requireNonNull(executor, "executor");
        this.ticks = Objects.requireNonNull(ticks, "ticks");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * Starts a run unless one is already active.
     *
     * <p>The mirror is reset to running before this returns, and the listener is subscribed before the job is
     * submitted, so the first events of a fast job are never lost.
     *
     * @param job the job to run on the background executor; not started before this call
     * @param request what is being translated, for the log only
     * @param selection the provider and model, for the log only
     * @return {@code true} if the run began, {@code false} if another run is active or the run could not be started
     */
    public boolean start(final TranslationJob job, final TranslationRequest request, final ModelSelection selection) {
        Objects.requireNonNull(job, "job");
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(selection, "selection");
        final ActiveRun run = new ActiveRun(job, new RunSession(mirror, clock));
        if (!active.compareAndSet(null, run)) {
            log.warn("refusing to start a run: another run is active");
            return false;
        }
        log.info(
                "run starting: book {}, target language {}, provider {}, model {}",
                request.source().getFileName(),
                request.targetLanguage(),
                selection.providerId(),
                selection.modelId());
        try {
            mirror.publishRunStarted();
            return launch(run);
        } catch (Throwable cause) {
            return abandon(run, cause);
        }
    }

    /** Publishes that a pause is pending, then asks the job to pause; the paused state waits for the engine. */
    public void pause() {
        final ActiveRun run = active.get();
        if (run == null) {
            log.debug("pause ignored: no run is active");
        } else if (run.session().requestPause()) {
            delegate("pause", run.job()::pause);
        }
    }

    /** Asks the job to resume; the running state is published when the engine reports it resumed. */
    public void resume() {
        final ActiveRun run = active.get();
        if (run == null) {
            log.debug("resume ignored: no run is active");
        } else if (run.session().requestResume()) {
            delegate("resume", run.job()::resume);
        }
    }

    /** Publishes that a stop is pending, then asks the job to cancel; the stopped state waits for the run to return. */
    public void cancel() {
        final ActiveRun run = active.get();
        if (run == null) {
            log.debug("cancel ignored: no run is active");
        } else if (run.session().requestStop()) {
            delegate("cancel", run.job()::cancel);
        }
    }

    private boolean launch(final ActiveRun run) {
        final Subscription subscription = run.job().subscribe(run.session());
        try {
            final Runnable stopTicks = ticks.start(run.session()::tick);
            return submit(run, stopTicks, subscription);
        } catch (Throwable cause) {
            subscription.unsubscribe();
            throw cause;
        }
    }

    private boolean submit(final ActiveRun run, final Runnable stopTicks, final Subscription subscription) {
        try {
            executor.execute(() -> execute(run, stopTicks, subscription));
            return true;
        } catch (Throwable cause) {
            stopTicks.run();
            throw cause;
        }
    }

    private void execute(final ActiveRun run, final Runnable stopTicks, final Subscription subscription) {
        log.debug("run executing on {}", Thread.currentThread().getName());
        final Result<JobReport> result = runGuarded(run.job());
        try {
            stopTicks.run();
        } catch (RuntimeException failure) {
            log.warn("the cadence could not be stopped; publishing the outcome anyway", failure);
        } finally {
            detachAndPublish(run, subscription, result);
        }
    }

    private void detachAndPublish(
            final ActiveRun run, final Subscription subscription, final Result<JobReport> result) {
        try {
            subscription.unsubscribe();
        } catch (RuntimeException failure) {
            log.warn("the job listener could not be unsubscribed; publishing the outcome anyway", failure);
        } finally {
            publishTerminal(run, result);
        }
    }

    private void publishTerminal(final ActiveRun run, final Result<JobReport> result) {
        try {
            run.session().finish(result, () -> release(run));
        } finally {
            release(run);
        }
    }

    private static Result<JobReport> runGuarded(final TranslationJob job) {
        try {
            return job.run();
        } catch (Throwable thrown) {
            log.error("the job threw instead of returning a result", thrown);
            return Result.err(AppError.of(
                    ErrorCode.internal,
                    "Unexpected error",
                    "The run stopped because of an unexpected error.",
                    null,
                    thrown));
        }
    }

    private boolean abandon(final ActiveRun run, final Throwable cause) {
        log.error("the run could not be started", cause);
        final AppError error =
                AppError.of(ErrorCode.internal, "Unexpected error", "The run could not be started.", null, cause);
        try {
            run.session().finish(Result.err(error), () -> release(run));
        } catch (RuntimeException failure) {
            log.warn("the failed start could not be published", failure);
        } finally {
            release(run);
        }
        if (cause instanceof Error fatal) {
            throw fatal;
        }
        return false;
    }

    private void release(final ActiveRun run) {
        if (active.compareAndSet(run, null)) {
            log.debug("run released; the runner accepts a new run");
        }
    }

    private static void delegate(final String control, final Runnable action) {
        try {
            action.run();
        } catch (RuntimeException failure) {
            log.warn("the job failed to accept {}", control, failure);
        }
    }
}
