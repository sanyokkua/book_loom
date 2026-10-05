package ua.bookloom.pipeline.heal;

import java.util.Objects;
import ua.bookloom.api.AppError;
import ua.bookloom.api.Result;

/**
 * One self-heal round attempt's control-flow outcome: the segment is decided (accepted or flagged), the step ends
 * with an error the round is kept for, or the loop carries the updated {@link RoundState} into the next round.
 */
sealed interface RoundStep {

    /**
     * Builds a terminal step that ends {@code nextDecision()} with {@code result}.
     *
     * @param result the segment's decision, or the error ending the step
     * @return a terminal step
     */
    static RoundStep terminal(final Result<SegmentOutcome> result) {
        return new Terminal(result);
    }

    /**
     * Builds a step that continues the round loop with {@code state}.
     *
     * @param state the state the next round attempt starts from
     * @return a continuing step
     */
    static RoundStep continueWith(final RoundState state) {
        return new Continue(state);
    }

    /** A step that ends {@code nextDecision()}. */
    record Terminal(Result<SegmentOutcome> result) implements RoundStep {

        /** Rejects a missing result. */
        public Terminal {
            Objects.requireNonNull(result, "result");
        }
    }

    /**
     * A step a model or gate call ended with an error; the round is kept, so the next decision continues at that call.
     *
     * @param error the error that ends this {@code nextDecision()} step
     */
    record Interrupted(AppError error) implements RoundStep {

        /** Rejects a missing error. */
        public Interrupted {
            Objects.requireNonNull(error, "error");
        }
    }

    /** A step that continues the round loop. */
    record Continue(RoundState state) implements RoundStep {

        /** Rejects a missing state. */
        public Continue {
            Objects.requireNonNull(state, "state");
        }
    }
}
