package ua.bookloom.pipeline.run;

import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
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
     * Which step a provider error stopped, as a pause on it names it to the person.
     *
     * @param segmentId the segment the failing step is for — the first of a chunk for a chunk's judge call — or null
     *     for a step of no segment
     * @param pauses how many times this step has paused the run, the pause about to happen included; zero when the
     *     step is never flagged for failing
     * @param pausesBeforeFlagging how many pauses the step may cause before its next failure flags it; zero when the
     *     step is never flagged for failing
     * @param automatic whether the pause may resume by itself once a probe finds the provider answering, rather than
     *     waiting for the person
     */
    record FailingStep(@Nullable String segmentId, int pauses, int pausesBeforeFlagging, boolean automatic) {

        /** A step of no segment that is never flagged for failing and waits for the person. */
        public static final FailingStep NONE = new FailingStep(null, 0, 0, false);
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
     * @param step the non-null step that failed, which a pause names
     * @return empty to make the call again, or how the run ends
     */
    Optional<RunEnd> afterRoutedError(AppError error, JobProgress progress, FailingStep step);

    /**
     * Takes the person's request, made while the run was paused on a provider error, to flag the failing segment and go
     * on instead of sending its call again. Asked only after such a pause ended in a resume.
     *
     * @return {@code true} once for each skip asked during the pause just ended
     */
    boolean takeSkipRequest();

    /**
     * Tells the boundaries a model call answered, which ends any provider outage the run was recovering from, so the
     * next failure starts its wait schedule afresh.
     */
    void callAnswered();
}
