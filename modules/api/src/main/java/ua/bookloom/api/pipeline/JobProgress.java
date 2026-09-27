package ua.bookloom.api.pipeline;

import java.util.Objects;

/**
 * A point-in-time count snapshot emitted during a job.
 *
 * @param stage the stage represented by the counts
 * @param section the 1-based position of the current body unit; the auxiliary unit is never counted
 * @param sections the total count of body units; the auxiliary unit is never counted
 * @param accepted number of accepted segments
 * @param flagged number of flagged segments
 * @param pending number of undecided segments
 * @param chunk the 1-based position of the current chunk within the section
 * @param chunks the total chunk count within the section
 * @param autoAccepted number of segments accepted without repair
 * @param repairedAccepted number of segments accepted after repair
 */
public record JobProgress(
        JobStage stage,
        int section,
        int sections,
        int accepted,
        int flagged,
        int pending,
        int chunk,
        int chunks,
        int autoAccepted,
        int repairedAccepted) {

    /** Rejects a progress snapshot without a stage. */
    public JobProgress {
        Objects.requireNonNull(stage, "stage");
    }

    /**
     * Builds a progress snapshot with no chunk or outcome-breakdown figures.
     *
     * @param stage the stage represented by the counts
     * @param section the 1-based position of the current body unit
     * @param sections the total count of body units
     * @param accepted number of accepted segments
     * @param flagged number of flagged segments
     * @param pending number of undecided segments
     */
    public JobProgress(
            final JobStage stage,
            final int section,
            final int sections,
            final int accepted,
            final int flagged,
            final int pending) {
        this(stage, section, sections, accepted, flagged, pending, 0, 0, 0, 0);
    }
}
