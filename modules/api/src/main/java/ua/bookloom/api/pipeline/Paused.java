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
 */
public record Paused(PauseReason reason, @Nullable AppError error, JobProgress progress) implements JobEvent {

    /** Rejects a pause event without a reason or progress snapshot. */
    public Paused {
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(progress, "progress");
    }
}
