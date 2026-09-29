package ua.bookloom.pipeline.run;

import java.util.Objects;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppError;
import ua.bookloom.api.pipeline.JobState;

/**
 * How a run's translation ended: completed, cancelled, or failed with the error the report carries.
 *
 * @param state the terminal state
 * @param error the error of a failed run, or null
 */
public record RunEnd(JobState state, @Nullable AppError error) {

    /** Rejects a missing state. */
    public RunEnd {
        Objects.requireNonNull(state, "state");
    }
}
