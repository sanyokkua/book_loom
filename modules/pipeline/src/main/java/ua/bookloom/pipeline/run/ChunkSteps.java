package ua.bookloom.pipeline.run;

import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.pipeline.StepKind;
import ua.bookloom.api.pipeline.StepResult;
import ua.bookloom.pipeline.heal.DraftOutcome;
import ua.bookloom.pipeline.prompt.StepAnnouncer;

/**
 * The steps a chunk's run announces: the draft steps and check passes planned when the chunk is entered, and a draft
 * step per segment. A step the run stopped inside stays open. Used from the job thread
 * only.
 */
@Slf4j
final class ChunkSteps {

    private final StepAnnouncer steps;

    ChunkSteps(final StepAnnouncer steps) {
        this.steps = steps;
    }

    /** Plans a chunk's drafts. */
    void chunkEntered(final int segments) {
        steps.planned(StepKind.DRAFT, segments, false);
    }

    void drafting(final String segmentId) {
        steps.started(StepKind.DRAFT, "draft", segmentId, segmentId);
    }

    /** Ends the draft step of a segment decided without a model call. */
    void drafted(final String segmentId) {
        steps.ended(StepKind.DRAFT, segmentId, StepResult.NO_CALL);
    }

    void drafted(final String segmentId, final DraftOutcome outcome) {
        steps.ended(
                StepKind.DRAFT,
                segmentId,
                outcome instanceof DraftOutcome.FlaggedAtOnce ? StepResult.FAILED : StepResult.DONE);
    }
}
