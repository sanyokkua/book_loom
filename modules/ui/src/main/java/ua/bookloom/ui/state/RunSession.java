package ua.bookloom.ui.state;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import javafx.application.Platform;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppError;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.pipeline.Finished;
import ua.bookloom.api.pipeline.JobEvent;
import ua.bookloom.api.pipeline.JobListener;
import ua.bookloom.api.pipeline.JobProgress;
import ua.bookloom.api.pipeline.JobReport;
import ua.bookloom.api.pipeline.MemoryUpdated;
import ua.bookloom.api.pipeline.ModelCallFinished;
import ua.bookloom.api.pipeline.ModelCallStarted;
import ua.bookloom.api.pipeline.PauseReason;
import ua.bookloom.api.pipeline.Paused;
import ua.bookloom.api.pipeline.Resumed;
import ua.bookloom.api.pipeline.ReviewDesk;
import ua.bookloom.api.pipeline.SegmentDecided;
import ua.bookloom.api.pipeline.SegmentDrafted;
import ua.bookloom.api.pipeline.SegmentStarted;
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

    private final StateMirror mirror;
    private final Clock clock;
    private final LiveChunkState liveChunks;
    private final ThroughputMeter throughputMeter = new ThroughputMeter();
    private final RunClock runClock;
    private final ActivityLogFeed feed = new ActivityLogFeed();
    private final ReviewDeskReads deskReads;
    private final WaitNotice waitNotice;
    private final AtomicReference<@Nullable JobProgress> latest = new AtomicReference<>();
    private final AtomicReference<@Nullable JobProgress> lastSeen = new AtomicReference<>();
    private final ConcurrentLinkedQueue<LogEntry> pending = new ConcurrentLinkedQueue<>();
    private final ReentrantLock publishLock = new ReentrantLock();
    // The four flags below are read and written only while publishLock is held, so a request and an engine event
    // can never each decide on a state the other has just changed.
    private boolean stopRequested;
    private boolean pauseRequested;
    private boolean pauseReached;
    private boolean terminal;
    // Both are guarded by publishLock too: the live rows changed since the last publish, and the pace figures last
    // published, so a tick publishes only what moved.
    private boolean liveRowsChanged;
    private Throughput shownThroughput = Throughput.EMPTY;

    RunSession(
            final StateMirror mirror,
            final Clock clock,
            final RunContext context,
            final ReviewDesk desk,
            final Executor executor) {
        this.mirror = Objects.requireNonNull(mirror, "mirror");
        this.clock = Objects.requireNonNull(clock, "clock");
        Objects.requireNonNull(context, "context");
        this.liveChunks = new LiveChunkState(context.dial());
        this.waitNotice = new WaitNotice(mirror, clock);
        this.runClock = new RunClock(clock.instant());
        this.deskReads = new ReviewDeskReads(desk, executor, mirror.live(), context.projectId());
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
                waitNotice.publish();
                flushLocked();
                publishLiveLocked();
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
     * <p>Ignored once the run is over, a stop is in flight, or a pause is pending or reached.
     *
     * @return {@code true} if the job should now be asked to pause, {@code false} if the request changes nothing
     */
    boolean requestPause() {
        publishLock.lock();
        try {
            if (terminal || stopRequested || pauseRequested || pauseReached) {
                log.debug(
                        "pause request ignored: terminal {}, stop requested {}, pause pending {}, pause reached {}",
                        terminal,
                        stopRequested,
                        pauseRequested,
                        pauseReached);
                return false;
            }
            log.debug("pause requested, publishing PAUSING");
            pauseRequested = true;
            waitNotice.clear("a pause was requested");
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
            waitNotice.clear("a stop was requested");
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
        final RunOutcomes.Outcome outcome = RunOutcomes.outcomeOf(result);
        logOutcome(outcome);
        publishSourceKept();
        publishLock.lock();
        try {
            terminal = true;
            runClock.ended(clock.instant());
            waitNotice.clear("the run ended");
            if (outcome.state() == RunState.COMPLETED) {
                pending.add(feed.finished());
            }
            flushLocked();
            publishLiveLocked();
            mirror.publishOutcome(outcome.state(), outcome.report(), outcome.error(), release);
        } finally {
            publishLock.unlock();
        }
    }

    private void dispatch(final JobEvent event) {
        switch (event) {
            case SegmentDecided decided -> onSegment(decided);
            case StageStarted started -> onStage(started);
            case Paused paused -> onPaused(paused);
            case Resumed resumed -> onResumed(resumed);
            case ModelCallStarted started -> onModelCall(started);
            case SegmentStarted started -> onSegmentStarted(started);
            case SegmentDrafted drafted -> onSegmentDrafted(drafted);
            case ModelCallFinished finished -> onModelCallFinished(finished);
            case MemoryUpdated updated -> onMemory(updated);
            case Finished finished -> log.debug("ignoring the Finished event; the returned result decides the outcome");
        }
    }

    private void onModelCall(final ModelCallStarted started) {
        log.trace("model call started for segment {}", started.segmentId() == null ? "none" : started.segmentId());
        locked(waitNotice::callStarted);
    }

    private void onSegment(final SegmentDecided decided) {
        record(decided.progress());
        locked(() -> {
            waitNotice.clear("a segment was decided");
            feed.decided(decided).ifPresent(pending::add);
            liveChunks.decided(decided);
            liveRowsChanged = true;
            if (decided.status() == SegmentStatus.ACCEPTED || decided.status() == SegmentStatus.FLAGGED) {
                runClock.decided(clock.instant());
            }
        });
        if (decided.status() == SegmentStatus.FLAGGED) {
            deskReads.refreshFlaggedQueue();
        }
    }

    private void onSegmentStarted(final SegmentStarted started) {
        locked(() -> {
            feed.segmentStarted(started);
            liveChunks.started(started);
            liveRowsChanged = true;
        });
    }

    private void onSegmentDrafted(final SegmentDrafted drafted) {
        locked(() -> {
            liveChunks.drafted(drafted);
            liveRowsChanged = true;
        });
    }

    private void onModelCallFinished(final ModelCallFinished finished) {
        locked(() -> {
            throughputMeter.finished(finished);
            feed.modelCall(finished).ifPresent(pending::add);
        });
    }

    private void onMemory(final MemoryUpdated updated) {
        log.debug("memory updated: {}", updated.kind());
        locked(() -> pending.add(feed.memory(updated)));
    }

    private void onStage(final StageStarted started) {
        record(started.progress());
        locked(() -> pending.add(feed.stageStarted()));
        log.debug("stage {} started", started.stage());
    }

    private void onPaused(final Paused paused) {
        record(paused.progress());
        locked(() -> {
            runClock.paused(clock.instant());
            if (settleLocked(feed.paused(paused), RunState.PAUSED, true)) {
                publishPauseDetailsLocked(paused);
            }
        });
    }

    // Every pause on an error is the provider-error state whatever its code, so a model that was unloaded and is
    // reported as a validation failure reaches it too.
    private void publishPauseDetailsLocked(final Paused paused) {
        final AppError error = paused.error();
        final String segmentId = paused.segmentId();
        log.debug("paused for {} at segment {}", paused.reason(), segmentId);
        if (paused.reason() == PauseReason.ON_ERROR && error != null) {
            log.debug("pause on error {}: publishing the provider error", error.code());
            mirror.review().publishProviderError(error);
        } else if (segmentId != null
                && (paused.reason() == PauseReason.ON_FLAGGED || paused.reason() == PauseReason.AFTER_SEGMENT)) {
            mirror.review().publishReviewPauseSegment(segmentId);
        }
    }

    private void onResumed(final Resumed resumed) {
        record(resumed.progress());
        locked(() -> {
            runClock.resumed(clock.instant());
            settleLocked(feed.resumed(), RunState.RUNNING, false);
            mirror.review().publishResumed();
        });
    }

    private void locked(final Runnable action) {
        publishLock.lock();
        try {
            action.run();
        } finally {
            publishLock.unlock();
        }
    }

    private boolean settleLocked(final LogEntry entry, final RunState reached, final boolean nowPaused) {
        pending.add(entry);
        pauseReached = nowPaused;
        pauseRequested = false;
        waitNotice.clear("the engine paused or resumed");
        if (terminal || stopRequested) {
            log.debug("engine reported {} but the run is stopping or over; the state is left alone", reached);
            return false;
        }
        log.debug("engine reported {}, publishing it", reached);
        mirror.publishRunState(reached);
        return true;
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

    private void publishLiveLocked() {
        if (liveRowsChanged) {
            liveRowsChanged = false;
            mirror.live().publishLiveRows(liveChunks.rows());
        }
        final JobProgress seen = lastSeen.get();
        final Throughput figures = throughputMeter.snapshot(
                runClock.timeLeft(seen == null ? 0 : seen.pending()), runClock.elapsed(clock.instant()));
        if (!figures.equals(shownThroughput)) {
            shownThroughput = figures;
            mirror.live().publishThroughput(figures);
        }
    }

    // A run ends off the FX thread; the one exception is a start that failed on the caller's own thread, which has no
    // decisions to count and must not read the desk there.
    private void publishSourceKept() {
        if (Platform.isFxApplicationThread()) {
            log.debug("kept-as-source count not read: the run ended on the FX thread");
            return;
        }
        deskReads.publishSourceKept();
    }

    private void logOutcome(final RunOutcomes.Outcome outcome) {
        final RunOutcomes.Counts counts = RunOutcomes.countsOf(outcome.report(), lastSeen.get());
        final AppError error = outcome.error();
        log.info(
                "run ended {}: {} segments, {} accepted, {} flagged, error code {}",
                outcome.state(),
                counts.segments(),
                counts.accepted(),
                counts.flagged(),
                error == null ? "none" : error.code());
    }
}
