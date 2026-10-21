package ua.bookloom.ui.state;

import java.time.Duration;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.pipeline.RecoveryWaiting;

/**
 * The sixteen run states of the README's "Run states" table, one per row, each a project is in exactly one of.
 *
 * <p>The run's own {@link RunState} has eight values; the rows split them by what else is known: stored progress,
 * a missing input, the wait for the AI program, a call past a minute, and what is left to review.
 */
public enum RunRow {
    /** Nothing translated yet, and a start is possible. */
    READY(false),
    /** A reopened project with stored progress. */
    READY_TO_CONTINUE(false),
    /** A start lacks something: no AI model, or the start was refused. */
    CANNOT_START(false),
    /** The run sends requests. */
    TRANSLATING(true),
    /** A pause waits for the run's next boundary. */
    PAUSING(true),
    /** Paused by the person or for review. */
    PAUSED(false),
    /** Paused on an error, waiting for the AI program by itself. */
    WAITING(true),
    /** Paused on an error, the wait held by the person or never started. */
    PAUSED_AFTER_ERROR(false),
    /** Paused on an error the run stopped waiting for by itself. */
    STOPPED_TRYING(false),
    /** Paused because the chosen model is not available. */
    MODEL_MISSING(false),
    /** The run sends requests and one attempt has had no answer for a minute. */
    STUCK(true),
    /** A stop waits for the run to return. */
    STOPPING(true),
    /** Stopped by the person; Continue translation picks up later. */
    STOPPED(false),
    /** Every segment decided, some of them to review. */
    FINISHED_TO_REVIEW(false),
    /** Every segment decided, none to review. */
    FINISHED_NONE(false),
    /** The run ended on an error. */
    FAILED(false);

    /** How long an attempt may go unanswered before its call counts as stuck. */
    static final Duration STUCK_AFTER = Duration.ofSeconds(WaitNotice.STUCK_SECONDS);

    private final boolean atWork;

    RunRow(final boolean atWork) {
        this.atWork = atWork;
    }

    /**
     * Whether BookLoom has a request out or sends one by itself in this state, so every other model action waits.
     *
     * @return {@code true} in Translating, Pausing, Waiting for the AI model, Stuck call and Stopping, {@code false}
     *     otherwise
     */
    public boolean isAtWork() {
        return atWork;
    }

    /**
     * The row the inputs put a project in.
     *
     * @param in what is known about the run
     * @return the row; never null
     */
    static RunRow of(final RunStateInputs in) {
        Objects.requireNonNull(in, "in");
        return switch (in.state()) {
            case IDLE -> idle(in);
            case RUNNING -> isStuck(in.waiting()) ? STUCK : TRANSLATING;
            case PAUSING -> PAUSING;
            case PAUSED -> paused(in);
            case STOPPING -> STOPPING;
            case STOPPED -> STOPPED;
            case COMPLETED -> finished(in);
            // A start refused before any model call is told where it was refused, not as a failed translation.
            case FAILED -> in.notice() instanceof RunNotice.Refused ? CANNOT_START : FAILED;
        };
    }

    private static boolean isStuck(final @Nullable WaitingCall waiting) {
        return waiting != null && waiting.attemptWaited().compareTo(STUCK_AFTER) >= 0;
    }

    private static RunRow finished(final RunStateInputs in) {
        return in.toReview() > 0 ? FINISHED_TO_REVIEW : FINISHED_NONE;
    }

    private static RunRow paused(final RunStateInputs in) {
        final RecoveryState recovery = in.recovery();
        if (recovery != null && recovery.isWaiting()) {
            return WAITING;
        }
        if (isModelMissing(in.pausedOn())) {
            return MODEL_MISSING;
        }
        if (recovery != null && recovery.status() == RecoveryWaiting.Status.GAVE_UP) {
            return STOPPED_TRYING;
        }
        return recovery != null || in.pausedOn() != null ? PAUSED_AFTER_ERROR : PAUSED;
    }

    private static boolean isModelMissing(final @Nullable ErrorCode code) {
        return code == ErrorCode.modelUnavailable || code == ErrorCode.modelNotFound;
    }

    private static RunRow idle(final RunStateInputs in) {
        if (in.notice() != null || !in.model().chosen()) {
            return CANNOT_START;
        }
        if (in.total() > 0 && in.decided() >= in.total()) {
            return finished(in);
        }
        return in.decided() > 0 ? READY_TO_CONTINUE : READY;
    }
}
