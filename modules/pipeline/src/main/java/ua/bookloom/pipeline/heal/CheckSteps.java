package ua.bookloom.pipeline.heal;

import java.util.List;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.pipeline.StepKind;
import ua.bookloom.api.pipeline.StepResult;
import ua.bookloom.pipeline.prompt.StepAnnouncer;

/**
 * The check steps of one chunk: one per reviewer pass the dial enables, keyed by the chunk's first segment. A pass
 * the chunk does not make (no pair to review, an unreadable first verdict) still ends, with no call, so the plan the
 * run announced at chunk entry always finishes. A pass whose call failed stays open for the retry.
 */
@Slf4j
final class CheckSteps {

    private static final String PREFIX = "check:";

    private final StepAnnouncer steps;
    private final String firstId;
    private final int passes;

    CheckSteps(final StepAnnouncer steps, final List<DraftOutcome> outcomes, final int passes) {
        this.steps = steps;
        this.firstId = outcomes.isEmpty() ? "" : outcomes.getFirst().segment().id();
        this.passes = outcomes.isEmpty() ? 0 : passes;
    }

    private String key(final int pass) {
        return PREFIX + firstId + ":" + pass;
    }

    void started(final int pass) {
        steps.started(StepKind.CHECK, "check-" + pass, key(pass), firstId);
    }

    void done(final int pass) {
        steps.ended(StepKind.CHECK, key(pass), StepResult.DONE);
    }

    /** Ends every pass from {@code fromPass} on with no call. */
    void skipFrom(final int fromPass) {
        for (int pass = fromPass; pass <= passes; pass++) {
            log.debug("Check pass skipped chunkFirst={} pass={}", firstId, pass);
            steps.instant(StepKind.CHECK, "check-" + pass, key(pass), firstId, StepResult.NO_CALL);
        }
    }

    /** The reviewer is given up on: the open pass fails and the passes never made end with no call. */
    void abandoned() {
        steps.endOpen(StepKind.CHECK, PREFIX + firstId + ":", StepResult.FAILED);
        for (int pass = 1; pass <= passes; pass++) {
            if (!steps.hasStarted(StepKind.CHECK, key(pass))) {
                steps.instant(StepKind.CHECK, "check-" + pass, key(pass), firstId, StepResult.NO_CALL);
            }
        }
    }
}
