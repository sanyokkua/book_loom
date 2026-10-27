package ua.bookloom.pipeline.glossary;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.pipeline.JobEvent;
import ua.bookloom.api.pipeline.ModelCallFinished;
import ua.bookloom.api.pipeline.ModelCallStarted;
import ua.bookloom.api.pipeline.NamesPassStage;
import ua.bookloom.api.pipeline.StepKind;
import ua.bookloom.api.pipeline.StepResult;
import ua.bookloom.pipeline.prompt.StepAnnouncer;

/**
 * Forwards every event of a names pass, counts its calls (a call sent again after a timeout is still one call) and
 * announces the pass's steps: one stage row per step of the pass and one request step per model call, labelled with
 * the stage that sent it. A stopped pass leaves its running steps open rather than showing them failed.
 */
@Slf4j
final class NamesPassSteps implements Consumer<JobEvent> {

    /** The stages of a pass, as the planned count of the stage rows. */
    static final int STAGE_COUNT = NamesPassStage.values().length;

    private final Consumer<JobEvent> receiver;
    private final StepAnnouncer announcer;
    private int calls;
    private int plannedRequests;
    private String stage = "";
    private boolean requestOpen;

    NamesPassSteps(final Consumer<JobEvent> receiver) {
        this.receiver = Objects.requireNonNull(receiver, "receiver");
        this.announcer = StepAnnouncer.over(receiver);
    }

    @Override
    public void accept(final JobEvent event) {
        if (event instanceof ModelCallStarted started && started.attempt() == 1) {
            requestStarted();
        } else if (event instanceof ModelCallFinished finished && finished.isAnswered()) {
            announcer.ended(StepKind.REQUEST, requestKey(calls), StepResult.DONE);
            requestOpen = false;
        }
        receiver.accept(event);
    }

    int calls() {
        return calls;
    }

    void planStages() {
        announcer.planned(StepKind.STAGE, STAGE_COUNT, false);
    }

    void planRequests(final int estimate) {
        plannedRequests = estimate;
        announcer.planned(StepKind.REQUEST, estimate, true);
    }

    /** Runs one stage as a step: done, or with no call when it sent none, or failed; a stop leaves it open. */
    <T> Result<T> stage(final String name, final Supplier<Result<T>> body) {
        stage = name;
        final int before = calls;
        announcer.started(StepKind.STAGE, name, name, null);
        final Result<T> result = body.get();
        final boolean stopped =
                result.isErr() && Objects.requireNonNull(result.error()).code() == ErrorCode.cancelled;
        if (!stopped) {
            closeRequest(StepResult.FAILED);
            announcer.ended(StepKind.STAGE, name, outcome(result, calls > before));
        }
        return result;
    }

    private static StepResult outcome(final Result<?> result, final boolean sentCalls) {
        if (result.isErr()) {
            return StepResult.FAILED;
        }
        return sentCalls ? StepResult.DONE : StepResult.NO_CALL;
    }

    private void requestStarted() {
        closeRequest(StepResult.FAILED);
        calls++;
        if (calls > plannedRequests) {
            plannedRequests = calls;
            announcer.planned(StepKind.REQUEST, 1, false);
        }
        announcer.started(StepKind.REQUEST, stage, requestKey(calls), null);
        requestOpen = true;
    }

    private void closeRequest(final StepResult result) {
        if (requestOpen) {
            announcer.ended(StepKind.REQUEST, requestKey(calls), result);
            requestOpen = false;
        }
    }

    private static String requestKey(final int number) {
        return "request:" + number;
    }
}
