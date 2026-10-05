package ua.bookloom.pipeline.run;

import java.util.Objects;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import ua.bookloom.pipeline.chunk.TokenEstimator;
import ua.bookloom.pipeline.context.ContextBudget;
import ua.bookloom.pipeline.prompt.CallFrame;

/**
 * Divides a unit's calls' window with {@link ContextBudget}: the static prefix (the system message and the style sheet)
 * and the dynamic context's whole allowance come off the window, and the rest is shared by a chunk's source and its
 * reply. The run packs a unit against the budget this gives, and a review retry cuts an oversized
 * segment against the same one, so a retry drafts in the pieces the run would.
 */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ChunkBudget {

    // The style sheet is English prose, as every prompt is.
    private static final String PROMPT_LANGUAGE = "en";

    /**
     * What the draft system message takes besides the style sheet: its rules, the foreign-passage rule and the
     * examples. A test holds it at or above the largest real system message.
     */
    static final int SYSTEM_PROMPT_RESERVE = 900;

    /**
     * The tokens of the static prefix every call of the run carries.
     *
     * @param frame the non-null call frame, whose style sheet is part of the prefix
     * @return the reserved prefix tokens
     */
    public static int staticPrefix(final CallFrame frame) {
        Objects.requireNonNull(frame, "frame");
        return TokenEstimator.estimate(frame.styleSheet().text(), PROMPT_LANGUAGE) + SYSTEM_PROMPT_RESERVE;
    }

    /**
     * What the dynamic context of one draft may take in {@code window}.
     *
     * @param frame the non-null call frame
     * @param window the usable window in tokens
     * @return the allowance for glossary, memory, preceding text and summary
     */
    public static int dynamicAllowance(final CallFrame frame, final int window) {
        return ContextBudget.dynamicAllowance(window, staticPrefix(frame));
    }

    /**
     * The budget a unit's calls are sized by. The whole dynamic allowance is reserved, not what the unit's glossary and
     * summary happen to take: the fit also fills it with memory, recurring terms and characters, so a smaller
     * reservation lets the prompt and its reply outgrow the window.
     *
     * @param frame the non-null call frame, whose style sheet and languages are reserved for
     * @param window the usable window in tokens
     * @return the budget; its {@link ContextBudget#chunkTokens()} is what one chunk may hold
     */
    public static ContextBudget budget(final CallFrame frame, final int window) {
        return new ContextBudget(
                window,
                staticPrefix(frame),
                dynamicAllowance(frame, window),
                TokenEstimator.outputRatio(frame.sourceLanguage(), frame.targetLanguage()));
    }
}
