package ua.bookloom.pipeline;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppError;
import ua.bookloom.api.pipeline.JobState;
import ua.bookloom.api.pipeline.PausePoint;
import ua.bookloom.api.pipeline.PauseReason;
import ua.bookloom.pipeline.run.PauseDecider;

/** Owns the lock-protected controls that a caller may change from any thread. */
@Slf4j
final class JobControl {

    private final ReentrantLock lock = new ReentrantLock();
    private final Condition changed = lock.newCondition();
    private JobState state = JobState.NEW;
    private Set<PausePoint> pausePoints;
    private boolean claimed;
    private boolean pauseRequested;
    private boolean cancelRequested;
    // The thread inside a model call, or null between calls. It is the only thread pause() and cancel() may
    // interrupt, and both read and write it under the lock, so an interrupt can never land outside a call.
    private @Nullable Thread modelCallThread;
    // Set when pause() aborts a call, so that a resume pressed before the job thread notices does not turn the
    // aborted call into a cancellation.
    private boolean pauseAbortedCall;
    // Set only when this control interrupted the model-call thread, so exitModelCall clears that interrupt and
    // never one somebody else sent, such as an executor shutdown, which must still end the run.
    private boolean interruptSent;
    // Set while a pause on an error recovers by itself; a person's pause clears it, which holds the run for them.
    private boolean recovering;

    /**
     * Creates the control of one job.
     *
     * @param pausePoints the non-null points the job pauses at until {@link #pauseAt(Set)} replaces them
     */
    JobControl(final Set<PausePoint> pausePoints) {
        this.pausePoints = Set.copyOf(Objects.requireNonNull(pausePoints, "pausePoints"));
    }

    boolean claimRun() {
        final boolean claimedNow = locked(() -> {
            if (claimed) {
                return false;
            }
            claimed = true;
            if (state == JobState.NEW) {
                transition(JobState.RUNNING);
            }
            return true;
        });
        log.debug("Claimed translation job run accepted={} state={}", claimedNow, state());
        return claimedNow;
    }

    void pause() {
        final boolean interrupted = locked(() -> {
            if (isTerminal()) {
                return false;
            }
            pauseRequested = true;
            holdRecovery();
            final boolean sent = interruptModelCall();
            pauseAbortedCall |= sent;
            return sent;
        });
        log.debug("Requested pause state={} interruptedModelCall={}", state(), interrupted);
    }

    void resume() {
        locked(() -> {
            if (!isTerminal()) {
                pauseRequested = false;
                if (state == JobState.PAUSED) {
                    transition(JobState.RUNNING);
                    changed.signalAll();
                }
            }
            return state;
        });
        log.debug("Resumed translation job state={}", state());
    }

    void cancel() {
        final boolean interrupted = locked(() -> {
            if (isTerminal()) {
                return false;
            }
            cancelRequested = true;
            pauseRequested = false;
            if (state == JobState.NEW || state == JobState.PAUSED) {
                transition(JobState.CANCELLED);
                changed.signalAll();
            }
            return interruptModelCall();
        });
        log.debug("Cancelled translation job state={} interruptedModelCall={}", state(), interrupted);
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
        final boolean entered = locked(() -> {
            final boolean free = !cancelRequested && !pauseRequested;
            if (free) {
                modelCallThread = Thread.currentThread();
                pauseAbortedCall = false;
            } else {
                pauseAbortedCall |= !cancelRequested;
            }
            return free;
        });
        log.debug("Model call entry entered={}", entered);
        return entered;
    }

    /**
     * Ends the claim and clears the interrupt this control sent to the calling thread, which is what stops the pause
     * wait from ever seeing the interrupt that aborted a call. An interrupt from anywhere else is left in place.
     */
    void exitModelCall() {
        final boolean cleared = locked(() -> {
            modelCallThread = null;
            final boolean sent = interruptSent;
            interruptSent = false;
            if (sent) {
                Thread.interrupted();
            }
            return sent;
        });
        log.debug("Model call exit interruptCleared={}", cleared);
    }

    /**
     * Decides what the job does after a model call was aborted: stop if a stop was asked for, pause if a pause is
     * still asked for, try the same segment again if the pause was withdrawn meanwhile, and otherwise stop because
     * something outside this control interrupted the run.
     */
    BoundaryDecision abortedCallBoundary() {
        final BoundaryDecision decision = locked(() -> {
            final BoundaryDecision decided;
            if (cancelRequested) {
                decided = BoundaryDecision.cancel();
            } else if (pauseRequested) {
                decided = pause(consumeRequestedPause());
            } else {
                decided = pauseAbortedCall ? BoundaryDecision.continueRunning() : BoundaryDecision.cancel();
            }
            pauseAbortedCall = false;
            return decided;
        });
        log.debug("Checked aborted-call boundary decision={}", decision);
        return decision;
    }

    /**
     * Interrupts the model call in flight for the stall watchdog, so the provider client gives it up and releases the
     * gate. Unlike a pause, it leaves no request behind: the call's answer is what the run acts on.
     *
     * @return {@code true} if a call was in flight and was interrupted, {@code false} otherwise
     */
    boolean interruptStalledCall() {
        final boolean interrupted = locked(() -> !isTerminal() && interruptModelCall());
        log.debug("Stall watchdog interrupt interruptedModelCall={}", interrupted);
        return interrupted;
    }

    /** Marks the pause just entered as one that recovers by itself until the person pauses, resumes or stops. */
    void beginRecovery() {
        final boolean began =
                locked(() -> recovering = state == JobState.PAUSED && !cancelRequested && !pauseRequested);
        log.debug("Began automatic recovery began={}", began);
    }

    /** Waits up to {@code nanos} (zero or less returns at once) for the person while the run recovers by itself. */
    RecoveryWake awaitWake(final long nanos) {
        boolean interrupted = false;
        final RecoveryWake wake;
        lock.lock();
        try {
            long left = nanos;
            while (state == JobState.PAUSED && !cancelRequested && recovering && left > 0) {
                try {
                    left = changed.awaitNanos(left);
                } catch (InterruptedException cause) {
                    cancelRequested = true;
                    transition(JobState.CANCELLED);
                    interrupted = true;
                }
            }
            wake = wakeOf();
        } finally {
            lock.unlock();
            restoreInterrupt(interrupted);
        }
        log.debug("Recovery wait ended wake={} interrupted={}", wake, interrupted);
        return wake;
    }

    /** Resumes a recovering run after a good probe unless the person acted meanwhile; {@code true} if it did. */
    boolean resumeAutomatically() {
        final boolean resumed = locked(() -> {
            final boolean due = state == JobState.PAUSED && !cancelRequested && recovering;
            if (due) {
                recovering = false;
                transition(JobState.RUNNING);
                changed.signalAll();
            }
            return due;
        });
        log.debug("Automatic resume resumed={}", resumed);
        return resumed;
    }

    /** Ends the recovery: whatever the wait ends in, a later pause is an ordinary one again. */
    void endRecovery() {
        locked(() -> recovering = false);
    }

    void pauseAt(final Set<PausePoint> points) {
        Objects.requireNonNull(points, "points");
        final JobState observed = locked(() -> {
            if (!isTerminal()) {
                pausePoints = Set.copyOf(points);
            }
            return state;
        });
        log.debug("Replaced pause points state={} points={}", observed, points);
    }

    // Silent on purpose: a screen reads the state as often as it draws, and a line per read would bury the run's own.
    JobState state() {
        return locked(() -> state);
    }

    boolean isCancellationRequested() {
        return locked(() -> cancelRequested);
    }

    Set<PausePoint> pausePoints() {
        return locked(() -> pausePoints);
    }

    /**
     * Answers the boundary after a segment was decided: a stop if one was asked for, else the pause the decider
     * chooses over the points in force now, so points replaced during a run or a pause apply from the next boundary.
     */
    BoundaryDecision boundary(final boolean flagged, final boolean endsSection, final boolean endsStage) {
        final BoundaryDecision decision;
        lock.lock();
        try {
            if (cancelRequested) {
                decision = BoundaryDecision.cancel();
            } else {
                final Optional<PauseReason> reason =
                        PauseDecider.boundary(pausePoints, pauseRequested, flagged, endsSection, endsStage);
                // A requested pause always wins the boundary it reaches, so reaching one consumes the request.
                pauseRequested = false;
                decision = reason.map(this::pause).orElseGet(BoundaryDecision::continueRunning);
            }
        } finally {
            lock.unlock();
        }
        log.debug(
                "Checked boundary flagged={} section={} stage={} decision={}",
                flagged,
                endsSection,
                endsStage,
                decision);
        return decision;
    }

    BoundaryDecision failureBoundary(final AppError error) {
        Objects.requireNonNull(error, "error");
        final BoundaryDecision decision = locked(() -> {
            if (cancelRequested) {
                return BoundaryDecision.cancel();
            }
            final PauseReason reason = pauseRequested
                    ? consumeRequestedPause()
                    : pausePoints.contains(PausePoint.ON_ERROR) ? PauseReason.ON_ERROR : null;
            return reason == null ? BoundaryDecision.continueRunning() : pause(reason);
        });
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

    void finish(final JobState terminal) {
        locked(() -> {
            transition(terminal);
            changed.signalAll();
            return terminal;
        });
        log.debug("Finished translation job terminal={}", terminal);
    }

    private <T> T locked(final Supplier<T> action) {
        lock.lock();
        try {
            return action.get();
        } finally {
            lock.unlock();
        }
    }

    private RecoveryWake wakeOf() {
        if (cancelRequested) {
            return RecoveryWake.CANCELLED;
        }
        if (state != JobState.PAUSED) {
            return RecoveryWake.RESUMED;
        }
        return recovering ? RecoveryWake.DUE : RecoveryWake.HELD;
    }

    // A person's pause while the run recovers by itself takes the run over: no wake resumes it any more.
    private void holdRecovery() {
        if (recovering && state == JobState.PAUSED) {
            recovering = false;
            changed.signalAll();
        }
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
        interruptSent = true;
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
