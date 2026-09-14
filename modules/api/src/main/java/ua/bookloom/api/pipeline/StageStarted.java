package ua.bookloom.api.pipeline;

import java.util.Objects;

/**
 * Announces that a job has entered a stage.
 *
 * @param stage the stage that started
 * @param progress the snapshot at stage start
 */
public record StageStarted(JobStage stage, JobProgress progress) implements JobEvent {

    /** Rejects an event without a stage or progress snapshot. */
    public StageStarted {
        Objects.requireNonNull(stage, "stage");
        Objects.requireNonNull(progress, "progress");
    }
}
