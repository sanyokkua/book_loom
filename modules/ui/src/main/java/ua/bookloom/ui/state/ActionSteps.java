package ua.bookloom.ui.state;

import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.pipeline.NamesPassStage;
import ua.bookloom.api.pipeline.StepChanged;
import ua.bookloom.api.pipeline.StepKind;
import ua.bookloom.api.pipeline.StepResult;
import ua.bookloom.api.pipeline.StepState;
import ua.bookloom.api.pipeline.StepsPlanned;

/**
 * The two steps of a card action of Names &amp; style (Scan, Review or Translate on either card): the action, worded as
 * its activity, then "Write the results". The model's requests are announced by the call feed and counted under the
 * action by their place in the log. FX thread only.
 */
@Slf4j
final class ActionSteps {

    private static final int STAGES = 2;
    private static final int ACTION = 0;
    private static final int WRITE = 1;

    private final ActivityTracker.Handle handle;
    private final String action;

    private ActionSteps(final ActivityTracker.Handle handle, final String action) {
        this.handle = handle;
        this.action = action;
    }

    /**
     * Plans the two steps and starts the first.
     *
     * @param handle the registration whose dialog lists them
     * @param action the first step's name, already worded
     * @return the steps, to finish when the action ends
     */
    static ActionSteps begin(final ActivityTracker.Handle handle, final String action) {
        Objects.requireNonNull(handle, "handle");
        Objects.requireNonNull(action, "action");
        log.debug("action steps begin: {}", action);
        handle.steps(new StepsPlanned(StepKind.STAGE, STAGES, false));
        handle.steps(new StepChanged(StepKind.STAGE, action, ACTION, StepState.STARTED, null, null));
        return new ActionSteps(handle, action);
    }

    /**
     * Ends the action and runs the write step, which the service did as part of its answer.
     *
     * @param sentRequests whether the action asked the model anything
     */
    void written(final boolean sentRequests) {
        log.debug("action steps written: {}, requests sent {}", action, sentRequests);
        ended(ACTION, action, sentRequests ? StepResult.DONE : StepResult.NO_CALL);
        final String write = NamesPassStage.WRITE.code();
        handle.steps(new StepChanged(StepKind.STAGE, write, WRITE, StepState.STARTED, null, null));
        ended(WRITE, write, StepResult.NO_CALL);
    }

    /** Ends the action as failed; the write step never starts. */
    void failed() {
        log.debug("action steps failed: {}", action);
        ended(ACTION, action, StepResult.FAILED);
    }

    private void ended(final int index, final String label, final StepResult result) {
        handle.steps(new StepChanged(StepKind.STAGE, label, index, StepState.ENDED, null, result));
    }
}
