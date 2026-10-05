package ua.bookloom.pipeline.heal;

import java.util.Objects;

/**
 * Where a segment's self-heal rounds stopped when a model call failed, kept so the next decision continues at the
 * failed call instead of starting the segment over ({@code specs/quality-gates/spec.md} "Continue a segment's repair at
 * the call that failed").
 *
 * @param round the round to continue, counted from one
 * @param state the state that round started from
 */
record Resumption(int round, BestCandidate state) {

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
     * @return round one
     */
    static Resumption first(final BestCandidate state) {
        return new Resumption(1, state);
    }
}
