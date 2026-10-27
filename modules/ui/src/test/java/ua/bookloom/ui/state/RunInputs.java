package ua.bookloom.ui.state;

import java.time.Duration;
import java.time.LocalTime;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.pipeline.CallKind;
import ua.bookloom.api.pipeline.RecoveryWaiting;

/**
 * Builds the inputs of the run-state table for a test: 74 of 142 segments decided (51 %), 6 to review, 4 in flight,
 * Ollama with {@code gemma4:e4b} chosen, and the controls the view model offers for the state, unless a test says
 * otherwise.
 */
final class RunInputs {

    static final ModelChoice OLLAMA = new ModelChoice("Ollama", "gemma4:e4b", true);
    static final ModelChoice NO_MODEL = new ModelChoice("Ollama", "", false);

    private RunState state = RunState.IDLE;
    private @Nullable RunNotice notice;
    private @Nullable RecoveryState recovery;
    private @Nullable ErrorCode pausedOn;
    private @Nullable WaitingCall waiting;
    private int decided = 74;
    private int toReview = 6;
    private int inFlight = 4;
    private ModelChoice model = OLLAMA;
    private @Nullable Controls controls;
    private @Nullable ActivityKind otherWork;

    static RunInputs in(final RunState state) {
        final RunInputs inputs = new RunInputs();
        inputs.state = state;
        return inputs;
    }

    RunInputs decided(final int count) {
        decided = count;
        return this;
    }

    RunInputs toReview(final int count) {
        toReview = count;
        return this;
    }

    RunInputs inFlight(final int count) {
        inFlight = count;
        return this;
    }

    RunInputs model(final ModelChoice choice) {
        model = choice;
        return this;
    }

    RunInputs notice(final RunNotice value) {
        notice = value;
        return this;
    }

    RunInputs controls(final Controls value) {
        controls = value;
        return this;
    }

    RunInputs heldBy(final ActivityKind kind) {
        otherWork = kind;
        controls = Controls.of(state, false, false).heldByOtherWork();
        return this;
    }

    /** Paused on {@code code}, the wait for the AI program in {@code status} with 12 seconds to the next try. */
    RunInputs recovery(final RecoveryWaiting.Status status, final ErrorCode code) {
        recovery = new RecoveryState(status, 3, LocalTime.of(9, 30), null, null, 12);
        pausedOn = code;
        controls = status == RecoveryWaiting.Status.WAITING
                ? Controls.waitingForProvider()
                : Controls.of(RunState.PAUSED, false, false);
        return this;
    }

    RunInputs pausedOn(final ErrorCode code) {
        pausedOn = code;
        return this;
    }

    /** An attempt of a draft call that has waited {@code seconds} against a limit of {@code limitSeconds}. */
    RunInputs waiting(final long seconds, final long limitSeconds) {
        waiting = new WaitingCall(
                CallKind.DRAFT,
                "ch2 · p05",
                3,
                1,
                2,
                Duration.ofSeconds(seconds),
                Duration.ofSeconds(limitSeconds),
                Duration.ofSeconds(seconds),
                seconds >= WaitNotice.STUCK_SECONDS);
        return this;
    }

    RunStateInputs build() {
        final Controls offered = controls == null ? Controls.of(state, false, false) : controls;
        return new RunStateInputs(
                state,
                notice,
                recovery,
                pausedOn,
                waiting,
                51,
                "about 1 h 10 min",
                decided,
                142,
                toReview,
                inFlight,
                model,
                offered,
                otherWork,
                null,
                null);
    }

    RunStateView view() {
        return RunStateView.of(build());
    }
}
