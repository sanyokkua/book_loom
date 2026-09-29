package ua.bookloom.pipeline.run;

import java.util.Objects;
import java.util.Optional;
import ua.bookloom.api.AppError;
import ua.bookloom.api.pipeline.JobProgress;

/**
 * The job's side of the places a run may pause or stop. The chunk runner knows where it stands in the book; the job
 * owns the controls, the pause wait and the events, so each boundary is answered by the job after it waited through
 * any pause it decided on.
 */
public interface RunBoundaries {

    /**
     * Where a decided segment leaves the run, which is all a pause point needs to know.
     *
     * @param segmentId the non-null id of the segment just decided, named by a pause about it
     * @param flagged whether it was decided FLAGGED
     * @param endsSection whether it was the last of its section to decide
     * @param endsRun whether it was the last segment of the run
     */
    record Decision(String segmentId, boolean flagged, boolean endsSection, boolean endsRun) {

        /** Rejects a decision that names no segment. */
        public Decision {
            Objects.requireNonNull(segmentId, "segmentId");
        }
    }

    /**
     * Answers the boundary after a segment was decided and its decision recorded.
     *
     * @param decision the non-null place the decision leaves the run
     * @param progress the non-null progress after the decision
     * @return empty to go on, or how the run ends
     */
    Optional<RunEnd> afterDecision(Decision decision, JobProgress progress);

    /**
     * Answers the boundary after a model call was refused or interrupted — by a pause, a stop, or neither.
     *
     * @param progress the non-null progress as it stands
     * @return empty to make the aborted call again, or how the run ends
     */
    Optional<RunEnd> afterAbortedCall(JobProgress progress);

    /**
     * Answers the boundary after a call answered an error the routing table sends to a pause, or to a failure where
     * pausing on an error is off.
     *
     * @param error the non-null error the call answered
     * @param progress the non-null progress as it stands
     * @return empty to make the call again, or how the run ends
     */
    Optional<RunEnd> afterRoutedError(AppError error, JobProgress progress);
}
