package ua.bookloom.pipeline.heal;

import java.util.Objects;
import ua.bookloom.api.Result;

/**
 * One self-heal round attempt's control-flow outcome: either the segment is decided (accepted, flagged, or the
 * whole step ends with an error) or the loop carries the updated {@link RoundState} into the next round.
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

    /** A step that continues the round loop. */
    record Continue(RoundState state) implements RoundStep {

        /** Rejects a missing state. */
        public Continue {
            Objects.requireNonNull(state, "state");
        }
    }
}
