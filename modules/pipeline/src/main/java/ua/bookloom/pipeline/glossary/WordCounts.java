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

    private WordCounts() {}

    static WordCounts of(final List<Occurrences.Read> segments) {
        final WordCounts counts = new WordCounts();
        for (final Occurrences.Read segment : segments) {
            segment.words().forEach(counts::count);
        }
        log.debug(
                "Word counts over {} segments: {} lower-case forms, {} capitalised forms",
                segments.size(),
                counts.lower.size(),
                counts.capitalised.size());
        return counts;
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
}
