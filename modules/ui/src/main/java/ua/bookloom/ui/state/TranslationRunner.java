package ua.bookloom.ui.state;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.time.Clock;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicReference;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.pipeline.ExportJob;
import ua.bookloom.api.pipeline.ExportReport;
import ua.bookloom.api.pipeline.ExportRequest;
import ua.bookloom.api.pipeline.ExportService;
import ua.bookloom.api.pipeline.JobReport;
import ua.bookloom.api.pipeline.JobState;
import ua.bookloom.api.pipeline.ReviewDesk;
import ua.bookloom.api.pipeline.Subscription;
import ua.bookloom.api.pipeline.TranslationJob;
import ua.bookloom.ui.BackgroundExecutor;

/**
 * Runs one translation job at a time on the background executor and feeds the {@link StateMirror} from it.
 *
 * <p>A plain submission rather than a JavaFX {@code Task}: {@link TranslationJob#run()} reports failure by
 * <em>returning</em> it, so {@code Task.setOnFailed} would never fire and the failure would still have to be read off
 * the returned {@link Result}. That returned result, never a {@code Finished} event, decides the terminal state,
 * because a run refused before it starts emits no event at all. Every method here is non-blocking and safe to call
 * from the FX thread.
 *
 * <p>Until the window works on a stored project, a run that completes is followed, on the same thread, by the export
 * to the destination the person chose, so a finished run still leaves a book; a failed export ends the run as
 * failed.
 */
@Slf4j
@Singleton
public final class TranslationRunner {

    /** The run the runner is busy with; the job is kept beside its session so a control reaches both. */
    private record ActiveRun(TranslationJob job, RunSession session, RunContext context) {}

    private final StateMirror mirror;
    private final ExecutorService executor;
    private final TickSource ticks;
    private final ExportService exports;
    private final ReviewDesk desk;
    private final Clock clock;
    private final AtomicReference<@Nullable ActiveRun> active = new AtomicReference<>();

    /**
     * Creates the runner with the production 100 ms cadence.
     *
     * @param mirror the mirror every run publishes into
     * @param executor the daemon executor a job runs on, never the FX thread
     * @param exports the port a completed run's book is written through
     * @param desk the review desk the run reads its flagged queue and kept-as-source count from
     */
    @Inject
    public TranslationRunner(
            final StateMirror mirror,
            @BackgroundExecutor final ExecutorService executor,
            final ExportService exports,
            final ReviewDesk desk) {
        this(mirror, executor, new FixedRateTicks(), exports, desk);
    }

    TranslationRunner(
            final StateMirror mirror,
            final ExecutorService executor,
            final TickSource ticks,
            final ExportService exports,
            final ReviewDesk desk) {
        this(mirror, executor, ticks, exports, desk, Clock.systemUTC());
    }

    TranslationRunner(
            final StateMirror mirror,
            final ExecutorService executor,
            final TickSource ticks,
            final ExportService exports,
            final ReviewDesk desk,
            final Clock clock) {
        this.mirror = Objects.requireNonNull(mirror, "mirror");
        this.executor = Objects.requireNonNull(executor, "executor");
        this.ticks = Objects.requireNonNull(ticks, "ticks");
        this.exports = Objects.requireNonNull(exports, "exports");
        this.desk = Objects.requireNonNull(desk, "desk");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * Starts a run unless one is already active.
     *
     * <p>The mirror is reset to running before this returns, and the listener is subscribed before the job is
     * submitted, so the first events of a fast job are never lost.
     *
     * @param job the job to run on the background executor; not started before this call
     * @param context the project the job runs over, and what is shown and logged about the run
     * @return {@code true} if the run began, {@code false} if another run is active or the run could not be started
     */
    public boolean start(final TranslationJob job, final RunContext context) {
        Objects.requireNonNull(job, "job");
        Objects.requireNonNull(context, "context");
        final ActiveRun run = new ActiveRun(job, new RunSession(mirror, clock, context, desk, executor), context);
        if (!active.compareAndSet(null, run)) {
            log.warn("refusing to start a run: another run is active");
            return false;
        }
        log.info(
                "run starting: project {}, book {}, review mode {}, provider {}, model {}",
                context.projectId(),
                context.fileName(),
                context.reviewMode(),
                context.selection().providerId(),
                context.selection().modelId());
        try {
            mirror.publishRunStarted(context.fileName());
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
        final Result<JobReport> result = exportIfCompleted(run, runGuarded(run.job()));
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

    private Result<JobReport> exportIfCompleted(final ActiveRun run, final Result<JobReport> result) {
        final JobReport report = result.data();
        if (report == null || report.end() != JobState.COMPLETED) {
            return result;
        }
        final Result<ExportReport> exported = export(run);
        if (exported.isOk()) {
            mirror.publishExportedFile(
                    Objects.requireNonNull(exported.data(), "export report").destination());
            return result;
        }
        final AppError error = Objects.requireNonNull(exported.error(), "export error");
        log.warn("the completed run could not be exported: code {}", error.code());
        return Result.ok(new JobReport(
                report.format(),
                JobState.FAILED,
                report.segments(),
                report.accepted(),
                report.flagged(),
                report.flaggedSegments(),
                error));
    }

    private Result<ExportReport> export(final ActiveRun run) {
        try {
            final InterimRunRequest request = run.context().interimExport();
            final String projectId = run.context().projectId();
            log.info("the run completed; exporting project {} to {}", projectId, request.destination());
            final Result<ExportJob> created = exports.newExport(
                    new ExportRequest(projectId, request.destination(), request.overwrite(), Set.of(), false), null);
            if (created.isErr()) {
                return Result.err(Objects.requireNonNull(created.error(), "error"));
            }
            return Objects.requireNonNull(created.data(), "export job").run();
        } catch (Throwable thrown) {
            log.error("the export threw instead of returning a result", thrown);
            return Result.err(AppError.of(
                    ErrorCode.internal,
                    "Unexpected error",
                    "The book could not be written because of an unexpected error.",
                    null,
                    thrown));
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
