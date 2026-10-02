package ua.bookloom.api.pipeline;

import java.time.Instant;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;

/**
 * Announces where a run paused on a provider error stands in its automatic recovery: when it will try the provider
 * again, or that it no longer will and waits for the person. Sent after the {@link Paused} event it belongs to, and
 * again after each probe that found the provider still unable to answer.
 *
 * @param status whether a wake is scheduled, or why none is
 * @param error the error the run paused on
 * @param attempt the wake about to happen, counted from one, or the last wake made when none is scheduled
 * @param downSince when the first failure of this outage happened
 * @param nextTryAt when the next wake probes the provider, or {@code null} when none is scheduled
 * @param probeFailure the code the last probe answered, or {@code null} before the first probe
 * @param progress the progress while waiting
 */
public record RecoveryWaiting(
        Status status,
        AppError error,
        int attempt,
        Instant downSince,
        @Nullable Instant nextTryAt,
        @Nullable ErrorCode probeFailure,
        JobProgress progress)
        implements JobEvent {

    /** Where the recovery stands. */
    public enum Status {
        /** A wake is scheduled at {@code nextTryAt}. */
        WAITING,
        /** The provider has been down longer than the run waits by itself; only the person resumes it now. */
        GAVE_UP,
        /** The person paused the run during the wait; only the person resumes it now. */
        HELD
    }

    /** Rejects a missing part, an attempt below zero, or a schedule that disagrees with the status. */
    public RecoveryWaiting {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(error, "error");
        Objects.requireNonNull(downSince, "downSince");
        Objects.requireNonNull(progress, "progress");
        if (attempt < 0) {
            throw new IllegalArgumentException("attempt must not be negative");
        }
        if ((status == Status.WAITING) != (nextTryAt != null)) {
            throw new IllegalArgumentException("a next try is scheduled exactly while waiting");
        }
    }
}
