package ua.bookloom.api.pipeline;

import java.util.Objects;

/**
 * Announces that a paused job has resumed.
 *
 * @param progress the progress snapshot at resume
 */
public record Resumed(JobProgress progress) implements JobEvent {

    /** Rejects a resume event without a progress snapshot. */
    public Resumed {
        Objects.requireNonNull(progress, "progress");
    }
}
