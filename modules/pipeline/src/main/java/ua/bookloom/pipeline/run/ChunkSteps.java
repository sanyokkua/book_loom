package ua.bookloom.pipeline.run;

import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.pipeline.StepKind;
import ua.bookloom.api.pipeline.StepResult;
import ua.bookloom.api.project.RollingSummary;
import ua.bookloom.pipeline.dial.DialParameters;
import ua.bookloom.pipeline.heal.DraftOutcome;
import ua.bookloom.pipeline.prompt.StepAnnouncer;

/**
 * The steps a chunk's run announces: the draft steps and check passes planned when the chunk is entered, a draft step
 * per segment, and the summary step of a unit. A step the run stopped inside stays open. Used from the job thread
 * only.
 */
@Slf4j
final class ChunkSteps {

    private final StepAnnouncer steps;
    private final DialParameters dial;

    ChunkSteps(final StepAnnouncer steps, final DialParameters dial) {
        this.steps = steps;
        this.dial = dial;
    }

    /** Plans a chunk's drafts, and its check passes when the dial enables the reviewer. */
    void chunkEntered(final int segments) {
        steps.planned(StepKind.DRAFT, segments, false);
        steps.planned(StepKind.CHECK, dial.reviewPasses(), false);
    }

    /** Plans the summary refresh of a unit about to run, when the dial asks the model for it. */
    void unitEntered() {
        if (dial.llmSummary()) {
            steps.planned(StepKind.SUMMARY, 1, false);
        }
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

    void summarizing(final String unitId) {
        if (dial.llmSummary()) {
            steps.started(StepKind.SUMMARY, "summary", "summary:" + unitId, unitId);
        }
    }

    void summarized(final String unitId, final Optional<RollingSummary> refreshed) {
        if (dial.llmSummary()) {
            log.debug("Summary step unitId={} refreshed={}", unitId, refreshed.isPresent());
            steps.ended(
                    StepKind.SUMMARY,
                    "summary:" + unitId,
                    refreshed.isPresent() ? StepResult.DONE : StepResult.NO_CALL);
        }
    }
}
