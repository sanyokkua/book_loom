package ua.bookloom.pipeline.memory;

import java.util.List;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.pipeline.chunk.TokenBudget;
import ua.bookloom.pipeline.chunk.TokenEstimator;

/**
 * One side of a chapter as the summary call is shown it. A long chapter would not fit the model's context, so its
 * middle is dropped: the opening sets the chapter up and the ending is where it leaves the story, which are the parts a
 * running summary needs most.
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class ChapterText {

    /**
     * The most estimated tokens either side of a chapter may take: the source and the target together use half the
     * model's context, which leaves the other half for the instructions, the previous summary and the reply.
     */
    static final int MAX_TOKENS = TokenBudget.EFFECTIVE_CONTEXT / 4;

    /** Stands where the middle of a chapter was dropped. */
    static final String GAP = "\n[…]\n";

    /**
     * Joins a chapter's lines and drops the middle when they do not fit.
     *
     * @param lines the chapter's texts, one per line, in document order; never null
     * @param languageTag the texts' language, or null when unknown, for the estimate
     * @param maxTokens the most estimated tokens the result may take; at least the gap's own estimate
     * @return the lines joined by newlines when they fit, otherwise as much of the beginning and the end as fits around
     *     {@link #GAP}
     */
    static String capped(final List<String> lines, @Nullable final String languageTag, final int maxTokens) {
        Objects.requireNonNull(lines, "lines");
        final String text = String.join("\n", lines);
        final int estimated = TokenEstimator.estimate(text, languageTag);
        if (estimated <= maxTokens) {
            log.debug("Chapter text fits lines={} tokens={} cap={}", lines.size(), estimated, maxTokens);
            return text;
        }
        final int[] codePoints = text.codePoints().toArray();
        int fits = 0;
        int tooMany = codePoints.length;
        while (tooMany - fits > 1) {
            final int kept = (fits + tooMany) >>> 1;
            if (TokenEstimator.estimate(around(codePoints, kept), languageTag) <= maxTokens) {
                fits = kept;
            } else {
                tooMany = kept;
            }
        }
        final String capped = around(codePoints, fits);
        log.debug(
                "Chapter text capped lines={} tokens={} cap={} keptCodePoints={} of {}",
                lines.size(),
                estimated,
                maxTokens,
                fits,
                codePoints.length);
        return capped;
    }

    private static String around(final int[] codePoints, final int kept) {
        final int head = (kept + 1) / 2;
        final int tail = kept - head;
        return new String(codePoints, 0, head) + GAP + new String(codePoints, codePoints.length - tail, tail);
    }
}
