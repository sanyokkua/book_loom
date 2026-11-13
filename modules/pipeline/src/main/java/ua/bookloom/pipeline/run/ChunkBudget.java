package ua.bookloom.pipeline.run;

import java.util.Objects;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import ua.bookloom.pipeline.chunk.TokenEstimator;
import ua.bookloom.pipeline.context.ContextBudget;
import ua.bookloom.pipeline.prompt.CallFrame;

/**
 * The static prefix of a run's calls (the system message and the style sheet) and the allowance {@link ContextBudget}
 * leaves beside it for the memory lines and the character sheet.
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
     * @return the allowance the chunk's character sheet takes its share of
     */
    public static int dynamicAllowance(final CallFrame frame, final int window) {
        return ContextBudget.dynamicAllowance(window, staticPrefix(frame));
    }
}
