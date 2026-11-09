package ua.bookloom.pipeline.context;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * What is left of the window sizes before the context builder: the safety margin every fit holds back, and the
 * allowance the translation memory lines and the character sheet are cut to, since the builder never drops
 * those lines itself. A chunk's, a batch's and a piece's size is the tier's current part ({@link WindowBudgets}).
 */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ContextBudget {

    /** Held back from every window for the tokenizer's disagreement with the estimate and for role markers. */
    public static final int SAFETY_MARGIN = 500;

    // The most the memory lines and the character sheet may take, however large the window.
    private static final int DYNAMIC_CEILING = 2000;
    private static final double DYNAMIC_SHARE = 0.4;
    private static final double CHARACTER_SHARE = 0.1;

    /**
     * What the memory lines and the character sheet may take in a window: a share of what the static prefix and margin
     * leave, so a short window keeps most of itself for the text being translated.
     *
     * @param window the usable window in tokens
     * @param staticPrefixTokens the static prefix's tokens
     * @return the allowance, between 0 and 2,000 tokens
     */
    public static int dynamicAllowance(final int window, final int staticPrefixTokens) {
        final int free = Math.max(0, window - staticPrefixTokens - SAFETY_MARGIN);
        return Math.min(DYNAMIC_CEILING, (int) (free * DYNAMIC_SHARE));
    }

    /**
     * What the character gender sheet may take of the dynamic allowance: a tenth, since a line is a name and a word, so
     * the sheet stays short however large the cast.
     *
     * @param allowance the dynamic context's allowance in tokens
     * @return the sheet's share, never above the allowance
     */
    public static int characterAllowance(final int allowance) {
        return (int) Math.min(allowance, Math.ceil(allowance * CHARACTER_SHARE));
    }
}
