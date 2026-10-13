package ua.bookloom.pipeline.index;

import java.util.Objects;

/**
 * How many windows to cut and how wide.
 *
 * @param count the most windows to return
 * @param neighbours how many segments before and after the mention's own may be read, inside its unit
 * @param tokensPerWindow the budget a window's text is trimmed to, sentence by sentence, by {@code TokenEstimator}
 * @param growth which side of the mention a window takes its first sentence from while it grows to the budget
 */
public record WindowPlan(int count, int neighbours, int tokensPerWindow, Growth growth) {

    /** Which side a window grows to first; it then alternates while the budget allows. */
    public enum Growth {
        /** The sentence before the mention first: the scene it stands in. */
        BACKWARD_FIRST,
        /**
         * The sentence after the mention first: where a pronoun that refers to the name usually stands, so the name
         * review, which checks such a pronoun in the window, keeps it (15i.N4).
         */
        FORWARD_FIRST
    }

    /** Rejects a missing growth. */
    public WindowPlan {
        Objects.requireNonNull(growth, "growth");
    }

    /**
     * A plan that grows backward first.
     *
     * @param count the most windows to return
     * @param neighbours how many segments before and after the mention's own may be read, inside its unit
     * @param tokensPerWindow the budget a window's text is trimmed to
     */
    public WindowPlan(final int count, final int neighbours, final int tokensPerWindow) {
        this(count, neighbours, tokensPerWindow, Growth.BACKWARD_FIRST);
    }
}
