package ua.bookloom.api.pipeline;

import java.util.Objects;

/**
 * A point-in-time count snapshot emitted during a job.
 *
 * @param stage the stage represented by the counts
 * @param section the current section index
 * @param sections the total section count
 * @param accepted number of accepted segments
 * @param flagged number of flagged segments
 * @param pending number of undecided segments
 */
public record JobProgress(JobStage stage, int section, int sections, int accepted, int flagged, int pending) {

    /** Rejects a progress snapshot without a stage. */
    public JobProgress {
        Objects.requireNonNull(stage, "stage");
    }
}
