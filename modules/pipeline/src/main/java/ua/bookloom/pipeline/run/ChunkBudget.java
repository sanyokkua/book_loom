package ua.bookloom.pipeline.run;

import java.util.List;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.pipeline.chunk.TokenBudget;
import ua.bookloom.pipeline.chunk.TokenEstimator;
import ua.bookloom.pipeline.prompt.CallFrame;

/**
 * What every call of a unit reserves out of the effective context before any source text: the style sheet, the output
 * a full chunk may take, the glossary lines of the unit and the rolling summary. The run packs a unit against the
 * budget this leaves, and a review retry cuts an oversized segment against the same budget, so a retry drafts in the
 * pieces the run would.
 */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ChunkBudget {

    // The style sheet is English prose, as every prompt is.
    private static final String PROMPT_LANGUAGE = "en";

    /**
     * The tokens a unit's calls reserve.
     *
     * @param frame the non-null call frame, whose style sheet and languages are reserved for
     * @param segments the non-null segments of the unit
     * @param glossary the non-null glossary entries the unit is drafted with; only those occurring in it count
     * @param summary the rolling summary the unit is drafted with, or null when there is none
     * @return the reserved tokens, never negative
     */
    public static int headroom(
            final CallFrame frame,
            final List<Segment> segments,
            final List<GlossaryEntry> glossary,
            @Nullable final String summary) {
        Objects.requireNonNull(frame, "frame");
        Objects.requireNonNull(segments, "segments");
        Objects.requireNonNull(glossary, "glossary");
        return TokenEstimator.estimate(frame.styleSheet().text(), PROMPT_LANGUAGE)
                + TokenBudget.fullChunkAllowance(frame.sourceLanguage(), frame.targetLanguage())
                + ChunkContext.termsEstimate(segments, glossary)
                + (summary == null ? 0 : TokenEstimator.estimate(summary, null));
    }
}
