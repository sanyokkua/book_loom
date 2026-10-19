package ua.bookloom.pipeline.memory;

import java.util.ArrayList;
import java.util.List;
import ua.bookloom.pipeline.chunk.TokenEstimator;

/**
 * The deterministic summary text, condensed to fit the size later prompts can spare: the oldest headings are the
 * first thing to go, because a name still in play matters more than where the story was three chapters ago, and only
 * then the entries the decided text mentions least.
 *
 * @param text the entries then the headings, one per line
 * @param estimatedTokens the estimated size of {@code text}
 * @param headingsDropped how many of the oldest headings were left out
 * @param entriesDropped how many of the rarest entries were left out
 */
record SummaryText(String text, int estimatedTokens, int headingsDropped, int entriesDropped) {

    /** The most estimated tokens the summary may take. */
    static final int MAX_TOKENS = 300;

    /**
     * Joins and condenses the lines.
     *
     * @param entryLines the entry lines, most frequent first; never null
     * @param headings the heading texts, oldest first; never null
     * @return the text that fits {@link #MAX_TOKENS}, or the empty text when even one line does not fit
     */
    static SummaryText build(final List<String> entryLines, final List<String> headings) {
        int firstHeading = 0;
        int entryCount = entryLines.size();
        String text = join(entryLines, entryCount, headings, firstHeading);
        int tokens = TokenEstimator.estimate(text, null);
        while (tokens > MAX_TOKENS) {
            if (firstHeading < headings.size()) {
                firstHeading++;
            } else {
                entryCount--;
            }
            text = join(entryLines, entryCount, headings, firstHeading);
            tokens = TokenEstimator.estimate(text, null);
        }
        return new SummaryText(text, tokens, firstHeading, entryLines.size() - entryCount);
    }

    private static String join(
            final List<String> entryLines, final int entryCount, final List<String> headings, final int firstHeading) {
        final List<String> lines = new ArrayList<>(entryLines.subList(0, entryCount));
        lines.addAll(headings.subList(firstHeading, headings.size()));
        return String.join("\n", lines);
    }
}
