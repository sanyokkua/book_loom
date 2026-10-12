package ua.bookloom.pipeline.index;

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
public final class WordCounts {

    private final Map<String, WordTally> tallies = new HashMap<>();

    private WordCounts() {}

    /**
     * Counts the words of some segments in one pass.
     *
     * @param segments the segments' reads; never null
     * @return the counts; never null
     */
    public static WordCounts of(final List<Occurrences.Read> segments) {
        final WordCounts counts = new WordCounts();
        for (final Occurrences.Read segment : segments) {
            final List<Occurrences.Word> words = segment.words();
            for (int index = 0; index < words.size(); index++) {
                final Occurrences.Word word = words.get(index);
                counts.tallies
                        .computeIfAbsent(word.key(), _ -> new WordTally())
                        .add(word, Occurrences.isStandalone(segment, index));
            }
        }
        log.debug("Word counts over {} segments: {} word forms", segments.size(), counts.tallies.size());
        return counts;
    }

    /**
     * The share of a word's occurrences written in lower case.
     *
     * @param key the word's {@link Occurrences#keyOf} key
     * @return from 0 (never in lower case, or never seen) to 1 (only in lower case)
     */
    public double lowerShare(final String key) {
        final WordTally tally = tallies.get(key);
        return tally == null ? 0 : tally.lowerShare();
    }

    /**
     * How often a word is written with a capital away from a sentence start.
     *
     * @param key the word's {@link Occurrences#keyOf} key
     * @return the count; 0 when never
     */
    public int midSentence(final String key) {
        final WordTally tally = tallies.get(key);
        return tally == null ? 0 : tally.capitalisedMidSentence();
    }

    /**
     * How often a word is written with a capital away from a sentence start and not beside another capitalised word,
     * which would make it a part of a name.
     *
     * @param key the word's {@link Occurrences#keyOf} key
     * @return the count; 0 when never
     */
    public int standaloneMidSentence(final String key) {
        final WordTally tally = tallies.get(key);
        return tally == null ? 0 : tally.standalone();
    }
}
