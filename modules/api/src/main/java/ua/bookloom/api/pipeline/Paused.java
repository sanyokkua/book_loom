package ua.bookloom.api.pipeline;

import java.util.Objects;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppError;

/**
 * Announces that a job is waiting at a safe boundary.
 *
 * @param reason the boundary or request that caused the pause
 * @param error the error being offered for recovery, or null for an ordinary pause
 * @param progress the progress snapshot while paused
 * @param segmentId the segment the pause occurred at, or null when the pause is not tied to one segment
 * @param pauses how many times the failing step has paused the run, this pause included; zero for a pause that is
 *     not on a step's error
 * @param pausesBeforeFlagging how many pauses a failing step may cause before its next failure flags it instead of
 *     pausing again; zero when the step is never flagged for failing
 * @param recoversByItself whether the run waits through this pause by itself and resumes once the provider answers
 *     again; {@code false} for a pause only a person ends — a review pause, a requested one, or an error such as
 *     {@code auth} — which a caller with nobody to ask, such as the command line, stops instead
 */
public record Paused(
        PauseReason reason,
        @Nullable AppError error,
        JobProgress progress,
        @Nullable String segmentId,
        int pauses,
        int pausesBeforeFlagging,
        boolean recoversByItself)
        implements JobEvent {

    /** Rejects a pause event without a reason or progress snapshot, or with negative counts. */
    public Paused {
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(progress, "progress");
        if (pauses < 0 || pausesBeforeFlagging < 0) {
            throw new IllegalArgumentException("pause counts must not be negative");
        }
    }

    /**
     * Builds a pause event only a person ends.
     *
     * @param reason the boundary or request that caused the pause
     * @param error the error being offered for recovery, or null for an ordinary pause
     * @param progress the progress snapshot while paused
     * @param segmentId the segment the pause occurred at, or null when the pause is not tied to one segment
     * @param pauses how many times the failing step has paused the run, this pause included
     * @param pausesBeforeFlagging how many pauses a failing step may cause before its next failure flags it
     */
    public Paused(
            final PauseReason reason,
            @Nullable final AppError error,
            final JobProgress progress,
            @Nullable final String segmentId,
            final int pauses,
            final int pausesBeforeFlagging) {
        this(reason, error, progress, segmentId, pauses, pausesBeforeFlagging, false);
    }

    /**
     * Builds a pause event that counts no step's failures.
     *
     * @param reason the boundary or request that caused the pause
     * @param error the error being offered for recovery, or null for an ordinary pause
     * @param progress the progress snapshot while paused
     * @param segmentId the segment the pause occurred at, or null when the pause is not tied to one segment
     */
    public Paused(
            final PauseReason reason,
            @Nullable final AppError error,
            final JobProgress progress,
            @Nullable final String segmentId) {
        this(reason, error, progress, segmentId, 0, 0);
    }

    /**
     * Builds a pause event not tied to one segment.
     *
     * @param reason the boundary or request that caused the pause
     * @param error the error being offered for recovery, or null for an ordinary pause
     * @param progress the progress snapshot while paused
     */
    public Paused(final PauseReason reason, @Nullable final AppError error, final JobProgress progress) {
        this(reason, error, progress, null);
    }
}
