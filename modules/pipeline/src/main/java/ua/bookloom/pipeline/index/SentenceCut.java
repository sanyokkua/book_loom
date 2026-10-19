package ua.bookloom.pipeline.index;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;
import ua.bookloom.pipeline.chunk.TokenEstimator;

/**
 * A sentence too long for its window, cut at word boundaries around the mention: words are taken outwards one at a
 * time, from the side the plan grows to first, while the cut stays inside the budget, and each cut end is shown by an ellipsis.
 *
 * @param text the cut sentence, with an ellipsis at each end that was cut
 * @param markOffset where the mention starts in {@code text}
 */
record SentenceCut(String text, int markOffset) {

    private static final String ELLIPSIS = "…";
    private static final Pattern WORD = Pattern.compile("\\S+");

    /** One blank-separated word of the sentence and where it lies. */
    private record Span(int start, int end) {}

    /**
     * Cuts a sentence around its mention.
     *
     * @param sentence the whole sentence, whitespace collapsed; never null
     * @param markOffset where the mention starts in it
     * @param markLength the mention's length
     * @param budget the tokens the cut may hold; the mention's own words are kept whatever it is
     * @param language the book's language tag for the estimate, or null when unknown
     * @param plan the window's plan, whose growth says which side the first word is taken from
     * @return the cut; never null
     */
    static SentenceCut around(
            final String sentence,
            final int markOffset,
            final int markLength,
            final int budget,
            @Nullable final String language,
            final WindowPlan plan) {
        final List<Span> words = words(sentence);
        int low = indexAt(words, markOffset);
        int high = Math.max(low, indexAt(words, markOffset + Math.max(0, markLength - 1)));
        boolean beforeNext = plan.growth() == WindowPlan.Growth.BACKWARD_FIRST;
        while (low > 0 || high < words.size() - 1) {
            final boolean takeBefore = low > 0 && (beforeNext || high == words.size() - 1);
            final int newLow = takeBefore ? low - 1 : low;
            final int newHigh = takeBefore ? high : high + 1;
            if (TokenEstimator.estimate(text(sentence, words, newLow, newHigh), language) > budget) {
                break;
            }
            low = newLow;
            high = newHigh;
            beforeNext = !takeBefore;
        }
        final int prefix = low > 0 ? ELLIPSIS.length() : 0;
        return new SentenceCut(
                text(sentence, words, low, high), markOffset - words.get(low).start() + prefix);
    }

    private static List<Span> words(final String sentence) {
        final List<Span> words = new ArrayList<>();
        final Matcher matcher = WORD.matcher(sentence);
        while (matcher.find()) {
            words.add(new Span(matcher.start(), matcher.end()));
        }
        return words;
    }

    // The word that holds the offset, else the last word that starts before it.
    private static int indexAt(final List<Span> words, final int offset) {
        int found = 0;
        for (int index = 0; index < words.size() && words.get(index).start() <= offset; index++) {
            found = index;
        }
        return found;
    }

    private static String text(final String sentence, final List<Span> words, final int low, final int high) {
        return (low > 0 ? ELLIPSIS : "")
                + sentence.substring(words.get(low).start(), words.get(high).end())
                + (high < words.size() - 1 ? ELLIPSIS : "");
    }
}
