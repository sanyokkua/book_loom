package ua.bookloom.pipeline.context;

import java.util.Locale;
import org.jspecify.annotations.Nullable;
import ua.bookloom.pipeline.chunk.TokenBudget;

/**
 * How one model window is divided: the static prefix, the dynamic context and a safety margin come off the window,
 * and what remains is split between a batch's source tokens {@code T} and its output {@code r·T}. One place decides
 * every size a call is given, so no limit is a free-standing guess.
 *
 * @param window the usable context window in tokens; positive
 * @param staticPrefixTokens the tokens the byte-identical system message and style sheet take; not negative
 * @param dynamicTokens the tokens the glossary, memory, preceding text and summary take; not negative
 * @param outputRatio output tokens per source token for the language pair; positive
 */
public record ContextBudget(int window, int staticPrefixTokens, int dynamicTokens, double outputRatio) {

    /**
     * The window used when none is detected, and the cap on a detected one: a server may train a model for 128k but
     * running it that wide costs memory the person did not ask to spend, so only an explicit override goes above it.
     */
    public static final int DEFAULT_WINDOW = TokenBudget.EFFECTIVE_CONTEXT;

    /** Held back from every window for the tokenizer's disagreement with the estimate and for role markers. */
    public static final int SAFETY_MARGIN = 500;

    /** The most the dynamic context may take, however large the window. */
    public static final int DYNAMIC_CEILING = 2000;

    private static final double DYNAMIC_SHARE = 0.4;
    private static final double LEXICON_SHARE = 0.2;
    private static final double CHARACTER_SHARE = 0.1;

    /** Rejects a window that is not positive, a negative reservation or a ratio that is not positive. */
    public ContextBudget {
        if (window <= 0 || staticPrefixTokens < 0 || dynamicTokens < 0 || outputRatio <= 0) {
            throw new IllegalArgumentException("invalid context budget: window=" + window + " prefix="
                    + staticPrefixTokens + " dynamic=" + dynamicTokens + " ratio=" + outputRatio);
        }
    }

    /**
     * The window a run sizes against.
     *
     * @param detected the context length the provider reported, or null when it said nothing
     * @param override the window the person set, or null; wins over detection and over the cap
     * @return the override, else the detected length limited to {@link #DEFAULT_WINDOW}, else {@link #DEFAULT_WINDOW}
     */
    public static int windowFor(@Nullable final Integer detected, @Nullable final Integer override) {
        if (override != null) {
            return override;
        }
        return detected == null ? DEFAULT_WINDOW : Math.min(detected, DEFAULT_WINDOW);
    }

    /**
     * What the dynamic context may take in a window: a share of what the static prefix and margin leave, so a short
     * window keeps most of itself for the text being translated.
     *
     * @param window the usable window in tokens
     * @param staticPrefixTokens the static prefix's tokens
     * @return the allowance, between 0 and {@link #DYNAMIC_CEILING}
     */
    public static int dynamicAllowance(final int window, final int staticPrefixTokens) {
        final int free = Math.max(0, window - staticPrefixTokens - SAFETY_MARGIN);
        return Math.min(DYNAMIC_CEILING, (int) (free * DYNAMIC_SHARE));
    }

    /**
     * What the recurring-term renderings may take of the dynamic allowance: a fifth, so a long lexicon never starves the
     * glossary, the memory or the preceding text, and a short one leaves its room to them.
     *
     * @param allowance the dynamic context's allowance in tokens
     * @return the lexicon's share, never above the allowance
     */
    public static int lexiconAllowance(final int allowance) {
        return (int) Math.min(allowance, Math.ceil(allowance * LEXICON_SHARE));
    }

    /**
     * What the character gender sheet may take of the dynamic allowance: a tenth, since a line is a name and a word, so
     * the sheet can never crowd out the glossary, the memory or the preceding text.
     *
     * @param allowance the dynamic context's allowance in tokens
     * @return the sheet's share, never above the allowance
     */
    public static int characterAllowance(final int allowance) {
        return (int) Math.min(allowance, Math.ceil(allowance * CHARACTER_SHARE));
    }

    /** The source tokens a batch may hold so that it and its reply fit what the window leaves. */
    public int sourceTokens() {
        final int free = window - staticPrefixTokens - dynamicTokens - SAFETY_MARGIN;
        return Math.max(0, (int) Math.floor(free / (1 + outputRatio)));
    }

    /** The source tokens one chunk holds: the batch size, never above the ceiling quality allows. */
    public int chunkTokens() {
        return Math.min(TokenBudget.MAX_CHUNK_TOKENS, sourceTokens());
    }

    /**
     * The reply a batch of {@code sourceTokens} may take.
     *
     * @param sourceTokens the batch's source tokens; not negative
     * @return the output tokens it is expected to need
     */
    public int outputTokens(final int sourceTokens) {
        return (int) Math.ceil(sourceTokens * outputRatio);
    }

    /** The one DEBUG line that explains how the window was split. */
    public String describe() {
        return String.format(
                Locale.ROOT,
                "window=%d prefix=%d dynamic=%d margin=%d ratio=%.2f sourceTokens=%d chunkTokens=%d",
                window,
                staticPrefixTokens,
                dynamicTokens,
                SAFETY_MARGIN,
                outputRatio,
                sourceTokens(),
                chunkTokens());
    }
}
