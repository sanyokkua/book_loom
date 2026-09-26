package ua.bookloom.pipeline;

import java.util.Objects;
import java.util.Set;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppError;
import ua.bookloom.api.pipeline.JobState;
import ua.bookloom.api.pipeline.PausePoint;
import ua.bookloom.api.pipeline.PauseReason;

/** Owns the lock-protected controls that a caller may change from any thread. */
@Slf4j
final class JobControl {

    private final ReentrantLock lock = new ReentrantLock();
    private final Condition changed = lock.newCondition();
    private JobState state = JobState.NEW;
    private Set<PausePoint> pausePoints = Set.of();
    private boolean claimed;
    private boolean pauseRequested;
    private boolean cancelRequested;
    private boolean exportStarted;
    // The thread inside a model call, or null between calls. It is the only thread pause() and cancel() may
    // interrupt, and both read and write it under the lock, so an interrupt can never land outside a call.
    private @Nullable Thread modelCallThread;
    // Set when pause() aborts a call, so that a resume pressed before the job thread notices does not turn the
    // aborted call into a cancellation.
    private boolean pauseAbortedCall;

    boolean claimRun() {
        final boolean claimedNow;
        final JobState observed;
        lock.lock();
        try {
            if (claimed) {
                claimedNow = false;
            } else {
                claimed = true;
                if (state == JobState.NEW) {
                    transition(JobState.RUNNING);
                }
                claimedNow = true;
            }
            observed = state;
        } finally {
            lock.unlock();
        }
        log.debug("Claimed translation job run accepted={} state={}", claimedNow, observed);
        return claimedNow;
    }

    void pause() {
        final JobState observed;
        final boolean ignored;
        final boolean interrupted;
        lock.lock();
        try {
            ignored = isTerminal() || exportStarted;
            if (!ignored) {
                pauseRequested = true;
            }
            interrupted = !ignored && interruptModelCall();
            pauseAbortedCall |= interrupted;
            observed = state;
        } finally {
            lock.unlock();
        }
        log.debug("Requested pause state={} ignored={} interruptedModelCall={}", observed, ignored, interrupted);
    }

    void resume() {
        final JobState observed;
        final boolean ignored;
        lock.lock();
        try {
            ignored = isTerminal();
            if (!ignored) {
                pauseRequested = false;
                if (state == JobState.PAUSED) {
                    transition(JobState.RUNNING);
                    changed.signalAll();
                }
            }
            observed = state;
        } finally {
            lock.unlock();
        }
        log.debug("Resumed translation job state={} ignored={}", observed, ignored);
    }

    void cancel() {
        final JobState observed;
        final boolean ignored;
        final boolean interrupted;
        lock.lock();
        try {
            ignored = isTerminal();
            if (!ignored) {
                cancelRequested = true;
                pauseRequested = false;
                if (state == JobState.NEW || state == JobState.PAUSED) {
                    transition(JobState.CANCELLED);
                    changed.signalAll();
                }
            }
            interrupted = !ignored && interruptModelCall();
            observed = state;
        } finally {
            lock.unlock();
        }
        log.debug(
                "Cancelled translation job state={} ignored={} interruptedModelCall={}",
                observed,
                ignored,
                interrupted);
    }

    /**
     * Claims the calling thread as the one inside a model call, unless a stop or a pause is already requested.
     *
     * <p>The check and the claim are one step under the lock: a pause arriving between two separate calls would
     * neither refuse the call nor interrupt it.
     *
     * @return {@code true} if the call may go ahead and must be closed with {@link #exitModelCall()}
     */
    boolean enterModelCall() {
        final boolean entered;
        final JobState observed;
        lock.lock();
        try {
            entered = !cancelRequested && !pauseRequested;
            if (entered) {
                modelCallThread = Thread.currentThread();
                pauseAbortedCall = false;
            } else {
                pauseAbortedCall |= !cancelRequested;
            }
            observed = state;
        } finally {
            lock.unlock();
        }
        log.debug("Model call entry entered={} state={}", entered, observed);
        return entered;
    }

    /**
     * Ends the claim and clears an interrupt left on the calling thread, which is what stops any later work — the
     * pause wait, the export — from ever seeing the interrupt that aborted a call.
     */
    void exitModelCall() {
        final boolean stale;
        lock.lock();
        try {
            modelCallThread = null;
            stale = Thread.interrupted();
        } finally {
            lock.unlock();
        }
        log.debug("Model call exit staleInterruptCleared={}", stale);
    }

    /**
     * Decides what the job does after a model call was aborted: stop if a stop was asked for, pause if a pause is
     * still asked for, try the same segment again if the pause was withdrawn meanwhile, and otherwise stop because
     * something outside this control interrupted the run.
     */
    BoundaryDecision abortedCallBoundary() {
        final BoundaryDecision decision;
        lock.lock();
        try {
            if (cancelRequested) {
                decision = BoundaryDecision.cancel();
            } else if (pauseRequested) {
                decision = pause(consumeRequestedPause());
            } else {
                decision = pauseAbortedCall ? BoundaryDecision.continueRunning() : BoundaryDecision.cancel();
            }
            pauseAbortedCall = false;
        } finally {
            lock.unlock();
        }
        log.debug("Checked aborted-call boundary decision={}", decision);
        return decision;
    }

    void pauseAt(final Set<PausePoint> points) {
        Objects.requireNonNull(points, "points");
        final JobState observed;
        lock.lock();
        try {
            if (!isTerminal()) {
                pausePoints = Set.copyOf(points);
            }
            observed = state;
        } finally {
            lock.unlock();
        }
        log.debug("Replaced pause points state={} points={}", observed, points);
    }

    JobState state() {
        final JobState observed;
        lock.lock();
        try {
            observed = state;
        } finally {
            lock.unlock();
        }
        log.debug("Read translation job state={}", observed);
        return observed;
    }

    boolean isCancellationRequested() {
        lock.lock();
        try {
            return cancelRequested;
        } finally {
            lock.unlock();
        }
    }

    Set<PausePoint> pausePoints() {
        lock.lock();
        try {
            return pausePoints;
        } finally {
            lock.unlock();
        }
    }

    BoundaryDecision boundary(final boolean afterSegment, final boolean afterSection, final boolean betweenStages) {
        final BoundaryDecision decision;
        lock.lock();
        try {
            if (cancelRequested) {
                decision = BoundaryDecision.cancel();
            } else {
                final PauseReason reason = boundaryReason(afterSegment, afterSection, betweenStages);
                decision = reason == null ? BoundaryDecision.continueRunning() : pause(reason);
            }
        } finally {
            lock.unlock();
        }
        log.debug(
                "Checked boundary segment={} section={} stages={} decision={}",
                afterSegment,
                afterSection,
                betweenStages,
                decision);
        return decision;
    }

    BoundaryDecision failureBoundary(final AppError error) {
        Objects.requireNonNull(error, "error");
        final BoundaryDecision decision;
        lock.lock();
        try {
            if (cancelRequested) {
                decision = BoundaryDecision.cancel();
            } else {
                final PauseReason reason = pauseRequested
                        ? consumeRequestedPause()
                        : pausePoints.contains(PausePoint.ON_ERROR) ? PauseReason.ON_ERROR : null;
                decision = reason == null ? BoundaryDecision.continueRunning() : pause(reason);
            }
        } finally {
            lock.unlock();
        }
        log.debug("Checked failure boundary errorCode={} decision={}", error.code(), decision);
        return decision;
    }

    PauseWait awaitPause() {
        boolean interrupted = false;
        final PauseWait result;
        lock.lock();
        try {
            while (state == JobState.PAUSED && !cancelRequested) {
                try {
                    changed.await();
                } catch (InterruptedException cause) {
                    cancelRequested = true;
                    transition(JobState.CANCELLED);
                    interrupted = true;
                }
            }
            result = cancelRequested ? PauseWait.CANCELLED : PauseWait.RESUMED;
        } finally {
            lock.unlock();
            restoreInterrupt(interrupted);
        }
        log.debug("Completed pause wait result={} interrupted={}", result, interrupted);
        return result;
    }

    void markExportStarted() {
        final JobState observed;
        lock.lock();
        try {
            exportStarted = true;
            observed = state;
        } finally {
            lock.unlock();
        }
        log.debug("Marked export stage started state={}", observed);
    }

    void finish(final JobState terminal) {
        lock.lock();
        try {
            transition(terminal);
            changed.signalAll();
        } finally {
            lock.unlock();
        }
        log.debug("Finished translation job terminal={}", terminal);
    }

    private @Nullable PauseReason boundaryReason(
            final boolean afterSegment, final boolean afterSection, final boolean betweenStages) {
        if (pauseRequested) {
            return consumeRequestedPause();
        }
        if (betweenStages && pausePoints.contains(PausePoint.BETWEEN_STAGES)) {
            return PauseReason.BETWEEN_STAGES;
        }
        if (afterSection && pausePoints.contains(PausePoint.AFTER_SECTION)) {
            return PauseReason.AFTER_SECTION;
        }
        return afterSegment && pausePoints.contains(PausePoint.AFTER_SEGMENT) ? PauseReason.AFTER_SEGMENT : null;
    }

    private PauseReason consumeRequestedPause() {
        pauseRequested = false;
        return PauseReason.REQUESTED;
    }

    private BoundaryDecision pause(final PauseReason reason) {
        transition(JobState.PAUSED);
        return BoundaryDecision.pause(reason);
    }

    private void restoreInterrupt(final boolean interrupted) {
        if (interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private boolean interruptModelCall() {
        if (modelCallThread == null) {
            return false;
        }
        modelCallThread.interrupt();
        return true;
    }

    private boolean isTerminal() {
        return switch (state) {
            case COMPLETED, CANCELLED, FAILED -> true;
            case NEW, RUNNING, PAUSED -> false;
        };
    }

    private void transition(final JobState next) {
        state = next;
    }
}
