package ua.bookloom.pipeline.heal;

import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Where a segment's self-heal rounds stopped when a model call failed, kept so the next decision continues at the
 * failed call instead of starting the segment over ({@code specs/quality-gates/spec.md} "Continue a segment's repair at
 * the call that failed").
 *
 * @param round the round to continue, counted from one
 * @param state the state that round started from
 * @param evaluated the round's rewrite when only its re-judge failed, so only the re-judge is sent again; or null when
 *     the round's own repair call failed and the round is sent again from its start
 */
record Resumption(int round, RoundState state, RoundOutcome.@Nullable Evaluated evaluated) {

    /** Rejects a missing state or a round before the first. */
    Resumption {
        Objects.requireNonNull(state, "state");
        if (round < 1) {
            throw new IllegalArgumentException("round must be positive: " + round);
        }
    }

    /**
     * The first round of a segment, started from its draft.
     *
     * @param state the draft's state
     * @return round one with no evaluated rewrite
     */
    static Resumption first(final RoundState state) {
        return new Resumption(1, state, null);
    }
}
