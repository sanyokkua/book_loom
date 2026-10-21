package ua.bookloom.pipeline.heal;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.pipeline.StepKind;
import ua.bookloom.api.pipeline.StepResult;
import ua.bookloom.pipeline.prompt.ModelCalls;

/**
 * The repair steps of the heal paths: each round, directed fix or soft fix is one step added when it starts, so a
 * segment that needs no repair adds none. A round that paused the run on a model error stays open for its retry.
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class RepairSteps {

    static final String ROUND_SUFFIX = ":r";
    static final String FIX_SUFFIX = ":fix";
    static final String SOFT_SUFFIX = ":soft";

    /** Adds and starts the step; returns its key for {@link #end}. */
    static String begin(final ModelCalls calls, final String segmentId, final String suffix, final String label) {
        final String key = segmentId + suffix;
        log.debug("Repair step segment={} label={}", segmentId, label);
        calls.steps().added(StepKind.REPAIR, label, key, segmentId);
        return key;
    }

    /** Ends the step as the round went: left open on a step error, FAILED when the segment is flagged now. */
    static void end(final ModelCalls calls, final String key, final RoundOutcome outcome) {
        switch (outcome) {
            case RoundOutcome.StepError ignored -> log.debug("Repair step {} stays open for the retry", key);
            case RoundOutcome.FlagNow ignored -> calls.steps().ended(StepKind.REPAIR, key, StepResult.FAILED);
            case RoundOutcome.Failed ignored -> calls.steps().ended(StepKind.REPAIR, key, StepResult.DONE);
            case RoundOutcome.Evaluated ignored -> calls.steps().ended(StepKind.REPAIR, key, StepResult.DONE);
        }
    }
}
