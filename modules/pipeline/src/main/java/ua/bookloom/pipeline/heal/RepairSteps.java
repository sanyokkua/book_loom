package ua.bookloom.pipeline.heal;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.pipeline.StepKind;
import ua.bookloom.api.pipeline.StepResult;
import ua.bookloom.pipeline.prompt.ModelCalls;

/**
 * The repair steps of the heal paths: the one repair and the reviewer's fix of a refused edit are each one step added
 * when it starts, so a segment that needs no repair adds none. A call that paused the run on a model error stays
 * open for its retry.
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class RepairSteps {

    static final String FIX_SUFFIX = ":fix";
    static final String REPAIR_SUFFIX = ":repair";

    /** Adds and starts the step; returns its key for {@link #end}. */
    static String begin(final ModelCalls calls, final String segmentId, final String suffix, final String label) {
        final String key = segmentId + suffix;
        log.debug("Repair step segment={} label={}", segmentId, label);
        calls.steps().added(StepKind.REPAIR, label, key, segmentId);
        return key;
    }

    /** Ends the step as the call went: left open on a step error, FAILED when the segment is flagged now. */
    static void end(final ModelCalls calls, final String key, final RepairOutcome outcome) {
        switch (outcome) {
            case RepairOutcome.StepError _ -> log.debug("Repair step {} stays open for the retry", key);
            case RepairOutcome.FlagNow _ -> calls.steps().ended(StepKind.REPAIR, key, StepResult.FAILED);
            case RepairOutcome.Failed _ -> calls.steps().ended(StepKind.REPAIR, key, StepResult.DONE);
            case RepairOutcome.Evaluated _ -> calls.steps().ended(StepKind.REPAIR, key, StepResult.DONE);
        }
    }
}
