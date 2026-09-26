package ua.bookloom.ui.state;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.pipeline.Finished;
import ua.bookloom.api.pipeline.JobEvent;
import ua.bookloom.api.pipeline.JobListener;
import ua.bookloom.api.pipeline.JobProgress;
import ua.bookloom.api.pipeline.JobReport;
import ua.bookloom.api.pipeline.JobStage;
import ua.bookloom.api.pipeline.ModelCallStarted;
import ua.bookloom.api.pipeline.Paused;
import ua.bookloom.api.pipeline.Resumed;
import ua.bookloom.api.pipeline.SegmentDecided;
import ua.bookloom.api.pipeline.StageStarted;

/**
 * Everything one run knows between the job thread and the mirror: the newest snapshot, the queued log lines, and
 * whether a stop or a pause was asked for.
 *
 * <p>The job thread only records into an {@link AtomicReference} and a queue, so an engine emitting thousands of
 * events never touches the FX queue; the cadence tick and the terminal publish drain them. One lock orders every
 * publish, so a late tick cannot overwrite the terminal flush and a {@code Paused} event cannot overwrite a stop that
 * was requested a moment earlier.
 */
@Slf4j
final class RunSession implements JobListener {

    private static final String STAGE_STARTED = "stageStarted";
    private static final String PAUSED = "paused";
    private static final String RESUMED = "resumed";
    private static final String FINISHED = "finished";
    /** A request answered within this many seconds is normal for a local model and is not worth a notice. */
    private static final long WAIT_NOTICE_THRESHOLD_SECONDS = 10;

    /** How a returned result maps onto the mirror. */
    private record Outcome(
            RunState state,
            @Nullable JobReport report,
            @Nullable AppError error) {}

    /** The counts an outcome line reports. */
    private record Counts(int segments, int accepted, int flagged) {}

    private final StateMirror mirror;
    private final Clock clock;
    private final AtomicReference<@Nullable JobProgress> latest = new AtomicReference<>();
    private final AtomicReference<@Nullable JobProgress> lastSeen = new AtomicReference<>();
    private final ConcurrentLinkedQueue<LogEntry> pending = new ConcurrentLinkedQueue<>();
    private final ReentrantLock publishLock = new ReentrantLock();
    // The four flags below are read and written only while publishLock is held, so a request and an engine event
    // can never each decide on a state the other has just changed.
    private boolean stopRequested;
    private boolean pauseRequested;
    private boolean pauseReached;
    private boolean exporting;
    private boolean terminal;
    // The two fields below are guarded by publishLock too: when the request now outstanding was sent, and the second
    // count the banner currently shows for it.
    private @Nullable Instant callStartedAt;
    private int shownWaitSeconds = StateMirror.NOT_WAITING;

    RunSession(final StateMirror mirror, final Clock clock) {
        this.mirror = Objects.requireNonNull(mirror, "mirror");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public void onEvent(final JobEvent event) {
        try {
            dispatch(event);
        } catch (RuntimeException failure) {
            // A listener must never break the job that called it; the returned result still decides the outcome.
            log.warn("dropping a job event the run session could not handle", failure);
        }
    }

    /** One cadence period: publishes what the job thread recorded since the last one. */
    void tick() {
        try {
            publishLock.lock();
            try {
                publishWaitLocked();
                flushLocked();
            } finally {
                publishLock.unlock();
            }
        } catch (RuntimeException failure) {
            log.warn("a cadence tick failed; the next tick or the terminal flush will publish", failure);
        }
    }

    /**
     * Publishes that a pause is pending.
     *
     * <p>Ignored once the run is over, a stop is in flight, a pause is pending or reached, or the export stage began:
     * the engine honours no pause in export, so showing one would promise a state that never arrives.
     *
     * @return {@code true} if the job should now be asked to pause, {@code false} if the request changes nothing
     */
    boolean requestPause() {
        publishLock.lock();
        try {
            if (terminal || stopRequested || pauseRequested || pauseReached || exporting) {
                log.debug(
                        "pause request ignored: terminal {}, stop requested {}, pause pending {}, pause reached {},"
                                + " exporting {}",
                        terminal,
                        stopRequested,
                        pauseRequested,
                        pauseReached,
                        exporting);
                return false;
            }
            log.debug("pause requested, publishing PAUSING");
            pauseRequested = true;
            clearWaitLocked("a pause was requested");
            mirror.publishRunState(RunState.PAUSING);
            return true;
        } finally {
            publishLock.unlock();
        }
    }

    /**
     * Withdraws a pending pause: the engine clears its pause request on resume, so no event will follow, and the
     * mirror would otherwise stay at PAUSING for the rest of the run.
     *
     * @return {@code true} if the job should be asked to resume, {@code false} once the run is over
     */
    boolean requestResume() {
        publishLock.lock();
        try {
            if (terminal) {
                log.debug("resume request ignored: the run is over");
                return false;
            }
            if (pauseRequested && !pauseReached && !stopRequested) {
                log.debug("resume requested while a pause is pending, publishing RUNNING");
                pauseRequested = false;
                mirror.publishRunState(RunState.RUNNING);
            }
            return true;
        } finally {
            publishLock.unlock();
        }
    }

    /**
     * Publishes that a stop is pending and makes every later pause or resume event leave the state alone.
     *
     * @return {@code true} if the job should now be asked to cancel, {@code false} once the run is over
     */
    boolean requestStop() {
        publishLock.lock();
        try {
            if (terminal) {
                log.debug("stop request ignored: the run is over");
                return false;
            }
            log.debug("stop requested, publishing STOPPING");
            stopRequested = true;
            clearWaitLocked("a stop was requested");
            mirror.publishRunState(RunState.STOPPING);
            return true;
        } finally {
            publishLock.unlock();
        }
    }

    /**
     * Publishes the terminal state decided by the returned result, after flushing everything still queued.
     *
     * <p>From here on no request publishes anything: a Stop pressed between this call and the runner becoming
     * startable again must not leave the mirror at STOPPING after COMPLETED.
     *
     * @param result what {@code run()} returned; the only authority on the outcome, because a run refused before it
     *     starts sends no {@code Finished} event
     * @param release runs on the FX thread just before the state changes, to make the runner startable again
     */
    void finish(final Result<JobReport> result, final Runnable release) {
        Objects.requireNonNull(result, "result");
        Objects.requireNonNull(release, "release");
        final Outcome outcome = outcomeOf(result);
        logOutcome(outcome);
        publishLock.lock();
        try {
            terminal = true;
            clearWaitLocked("the run ended");
            if (outcome.state() == RunState.COMPLETED) {
                pending.add(milestone(FINISHED));
            }
            flushLocked();
            mirror.publishOutcome(outcome.state(), outcome.report(), outcome.error(), release);
        } finally {
            publishLock.unlock();
        }
    }

    private void dispatch(final JobEvent event) {
        switch (event) {
            case SegmentDecided decided -> onSegment(decided);
            case StageStarted started -> onStage(started);
            case Paused paused -> onPausedOrResumed(paused.progress(), PAUSED, RunState.PAUSED, true);
            case Resumed resumed -> onPausedOrResumed(resumed.progress(), RESUMED, RunState.RUNNING, false);
            case ModelCallStarted started -> onModelCall(started);
            case Finished finished -> log.debug("ignoring the Finished event; the returned result decides the outcome");
        }
    }

    private void onModelCall(final ModelCallStarted started) {
        log.trace("model call started for segment {}", started.segmentId());
        publishLock.lock();
        try {
            clearWaitLocked("a new request started");
            callStartedAt = clock.instant();
        } finally {
            publishLock.unlock();
        }
    }

    // Both clocks stop with the request: a decision, a pause or the end of the run means nothing is outstanding.
    private void clearWaitLocked(final String why) {
        callStartedAt = null;
        if (shownWaitSeconds != StateMirror.NOT_WAITING) {
            log.debug("waiting notice cleared after {} s because {}", shownWaitSeconds, why);
            shownWaitSeconds = StateMirror.NOT_WAITING;
            mirror.publishWaitingSeconds(StateMirror.NOT_WAITING);
        }
    }

    private void publishWaitLocked() {
        final Instant started = callStartedAt;
        if (started == null) {
            return;
        }
        final long waited = Duration.between(started, clock.instant()).toSeconds();
        if (waited < WAIT_NOTICE_THRESHOLD_SECONDS || waited == shownWaitSeconds) {
            return;
        }
        if (shownWaitSeconds == StateMirror.NOT_WAITING) {
            log.debug("a model request has been outstanding for {} s, showing the waiting notice", waited);
        }
        shownWaitSeconds = Math.toIntExact(waited);
        mirror.publishWaitingSeconds(shownWaitSeconds);
    }

    private void onSegment(final SegmentDecided decided) {
        record(decided.progress());
        publishLock.lock();
        try {
            clearWaitLocked("a segment was decided");
        } finally {
            publishLock.unlock();
        }
        log.trace("segment {} decided {}", decided.segmentId(), decided.status());
        switch (decided.status()) {
            case ACCEPTED -> pending.add(new LogEntry(LogKind.ACCEPTED, List.of(decided.segmentId())));
            case FLAGGED -> {
                pending.add(new LogEntry(LogKind.SEGMENT_ERROR, List.of(decided.segmentId())));
                log.warn("segment {} was flagged, reason {}", decided.segmentId(), decided.reason());
            }
            case PENDING, REVISED -> log.trace("segment {} is not a decision the log reports", decided.segmentId());
        }
    }

    private void onStage(final StageStarted started) {
        record(started.progress());
        pending.add(milestone(STAGE_STARTED));
        log.debug("stage {} started", started.stage());
        if (started.stage() == JobStage.EXPORT) {
            publishLock.lock();
            try {
                exporting = true;
                clearWaitLocked("the export stage began");
                dropPendingPauseLocked();
            } finally {
                publishLock.unlock();
            }
        }
    }

    // The engine honours no pause once export began, so a pause still pending here will never be reached: no Paused
    // event follows and the mirror would otherwise stay at PAUSING until the book was written.
    private void dropPendingPauseLocked() {
        if (pauseRequested && !pauseReached && !stopRequested && !terminal) {
            log.debug("export began while a pause was pending; the request is dropped, publishing RUNNING");
            pauseRequested = false;
            mirror.publishRunState(RunState.RUNNING);
        }
    }

    private void onPausedOrResumed(
            final JobProgress progress, final String token, final RunState reached, final boolean nowPaused) {
        record(progress);
        pending.add(milestone(token));
        publishLock.lock();
        try {
            pauseReached = nowPaused;
            pauseRequested = false;
            clearWaitLocked("the engine paused or resumed");
            if (terminal || stopRequested) {
                log.debug("engine reported {} but the run is stopping or over; the state is left alone", token);
            } else {
                log.debug("engine reported {}, publishing {}", token, reached);
                mirror.publishRunState(reached);
            }
        } finally {
            publishLock.unlock();
        }
    }

    private void record(final JobProgress progress) {
        latest.set(progress);
        lastSeen.set(progress);
    }

    private void flushLocked() {
        final JobProgress snapshot = latest.getAndSet(null);
        if (snapshot != null) {
            mirror.publishProgress(snapshot);
        }
        final List<LogEntry> batch = new ArrayList<>();
        LogEntry next = pending.poll();
        while (next != null) {
            batch.add(next);
            next = pending.poll();
        }
        if (!batch.isEmpty()) {
            mirror.publishLogEntries(batch);
        }
    }

    private static LogEntry milestone(final String token) {
        return new LogEntry(LogKind.MILESTONE, List.of(token));
    }

    private static Outcome outcomeOf(final Result<JobReport> result) {
        final AppError refusal = result.error();
        if (refusal != null) {
            // A stop the person chose can come back as an error result too; it is the stopped state, not a failure.
            return refusal.code() == ErrorCode.cancelled
                    ? new Outcome(RunState.STOPPED, null, null)
                    : new Outcome(RunState.FAILED, null, refusal);
        }
        final JobReport report = Objects.requireNonNull(result.data(), "successful result data");
        return switch (report.end()) {
            case COMPLETED -> new Outcome(RunState.COMPLETED, report, null);
            case CANCELLED -> new Outcome(RunState.STOPPED, report, null);
            case FAILED -> new Outcome(RunState.FAILED, report, report.error());
            // JobReport's constructor rejects a non-terminal end, so no report can carry one.
            case NEW, RUNNING, PAUSED -> throw new IllegalStateException("a job report carried a non-final state");
        };
    }

    private void logOutcome(final Outcome outcome) {
        final Counts counts = countsOf(outcome.report());
        final AppError error = outcome.error();
        log.info(
                "run ended {}: {} segments, {} accepted, {} flagged, error code {}",
                outcome.state(),
                counts.segments(),
                counts.accepted(),
                counts.flagged(),
                error == null ? "none" : error.code());
    }

    private Counts countsOf(final @Nullable JobReport report) {
        if (report != null) {
            return new Counts(report.segments(), report.accepted(), report.flagged());
        }
        final JobProgress seen = lastSeen.get();
        if (seen == null) {
            return new Counts(0, 0, 0);
        }
        final RunFigures figures = RunFigures.from(seen);
        return new Counts(figures.total(), figures.accepted(), figures.flagged());
    }
}
