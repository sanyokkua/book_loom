package ua.bookloom.pipeline.glossary;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

/**
 * How a book writes each word: how often in lower case, how often with a capital, and how often with a capital away
 * from a sentence start. The counts are what tells a name from a common word in any language with capitals, since a
 * name is almost never written in lower case.
 */
@Slf4j
final class WordCounts {

    private final Map<String, Integer> lower = new HashMap<>();
    private final Map<String, Integer> capitalised = new HashMap<>();
    private final Map<String, Integer> midSentence = new HashMap<>();
    private final Map<String, Integer> standalone = new HashMap<>();

    private WordCounts() {}

    static WordCounts of(final List<Occurrences.Read> segments) {
        final WordCounts counts = new WordCounts();
        for (final Occurrences.Read segment : segments) {
            segment.words().forEach(counts::count);
            countStandalone(counts, segment);
        }
        log.debug(
                "Word counts over {} segments: {} lower-case forms, {} capitalised forms",
                segments.size(),
                counts.lower.size(),
                counts.capitalised.size());
        return counts;
    }

    // A capital that stands beside another capitalised word is a part of a name (Great Hall, Old Bailey), so it says
    // nothing about the common word it also is.
    private static void countStandalone(final WordCounts counts, final Occurrences.Read segment) {
        final List<Occurrences.Word> words = segment.words();
        for (int index = 0; index < words.size(); index++) {
            final Occurrences.Word word = words.get(index);
            if (word.capitalised() && !word.initial() && !hasCapitalNeighbour(segment, index)) {
                counts.standalone.merge(word.key(), 1, Integer::sum);
            }
        }
    }

    private static boolean hasCapitalNeighbour(final Occurrences.Read segment, final int index) {
        final List<Occurrences.Word> words = segment.words();
        final Occurrences.Word word = words.get(index);
        final boolean before = index > 0
                && words.get(index - 1).capitalised()
                && isSpaceGap(segment.text(), words.get(index - 1).end(), word.start());
        final boolean after = index + 1 < words.size()
                && words.get(index + 1).capitalised()
                && isSpaceGap(segment.text(), word.end(), words.get(index + 1).start());
        return before || after;
    }

    private static boolean isSpaceGap(final String text, final int from, final int to) {
        return text.substring(from, to).isBlank();
    }

    private void count(final Occurrences.Word word) {
        final String key = word.key();
        if (!word.capitalised()) {
            if (Character.isLowerCase(word.text().codePointAt(0))) {
                lower.merge(key, 1, Integer::sum);
            }
            return;
        }
        capitalised.merge(key, 1, Integer::sum);
        if (!word.initial()) {
            midSentence.merge(key, 1, Integer::sum);
        }
    }

    /**
     * The share of a word's occurrences written in lower case.
     *
     * @param key the word's {@link Occurrences#keyOf} key
     * @return from 0 (never in lower case, or never seen) to 1 (only in lower case)
     */
    double lowerShare(final String key) {
        final int lowerCount = lower.getOrDefault(key, 0);
        final int total = lowerCount + capitalised.getOrDefault(key, 0);
        return total == 0 ? 0 : (double) lowerCount / total;
    }

    /**
     * How often a word is written with a capital away from a sentence start.
     *
     * @param key the word's {@link Occurrences#keyOf} key
     * @return the count; 0 when never
     */
    int midSentence(final String key) {
        return midSentence.getOrDefault(key, 0);
    }

    /**
     * How often a word is written with a capital away from a sentence start and not beside another capitalised word,
     * which would make it a part of a name.
     *
     * @param key the word's {@link Occurrences#keyOf} key
     * @return the count; 0 when never
     */
    int standaloneMidSentence(final String key) {
        return standalone.getOrDefault(key, 0);
    }
}
