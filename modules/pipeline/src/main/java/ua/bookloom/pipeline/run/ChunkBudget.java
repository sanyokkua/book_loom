package ua.bookloom.pipeline.run;

import java.util.List;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.pipeline.chunk.TokenEstimator;
import ua.bookloom.pipeline.context.ContextBudget;
import ua.bookloom.pipeline.prompt.CallFrame;

/**
 * Divides a unit's calls' window with {@link ContextBudget}: the static prefix (the system message and the style sheet)
 * and the unit's glossary lines, summary and preceding text come off the window, and the rest is shared by a chunk's
 * source and its reply. The run packs a unit against the budget this gives, and a review retry cuts an oversized
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
    static final int SYSTEM_PROMPT_RESERVE = 700;

    /** The last few translated segments the dial may show; reserved even before the unit has any. */
    static final int PRECEDING_RESERVE = 300;

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
     * The budget a unit's calls are sized by.
     *
     * @param frame the non-null call frame, whose style sheet and languages are reserved for
     * @param segments the non-null segments of the unit
     * @param glossary the non-null glossary entries the unit is drafted with; only those occurring in it count
     * @param summary the rolling summary the unit is drafted with, or null when there is none
     * @param window the usable window in tokens
     * @return the budget; its {@link ContextBudget#chunkTokens()} is what one chunk may hold
     */
    public static ContextBudget budget(
            final CallFrame frame,
            final List<Segment> segments,
            final List<GlossaryEntry> glossary,
            @Nullable final String summary,
            final int window) {
        Objects.requireNonNull(segments, "segments");
        Objects.requireNonNull(glossary, "glossary");
        final int wanted = ChunkContext.termsEstimate(segments, glossary)
                + (summary == null ? 0 : TokenEstimator.estimate(summary, null))
                + PRECEDING_RESERVE;
        return new ContextBudget(
                window,
                staticPrefix(frame),
                Math.min(dynamicAllowance(frame, window), wanted),
                TokenEstimator.outputRatio(frame.sourceLanguage(), frame.targetLanguage()));
    }
}
