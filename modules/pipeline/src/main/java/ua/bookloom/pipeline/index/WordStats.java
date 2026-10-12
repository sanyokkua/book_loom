package ua.bookloom.pipeline.index;

/**
 * How a book writes one word.
 *
 * @param total every occurrence, in any case
 * @param lowerCase occurrences written in lower case
 * @param capitalisedMidSentence occurrences with a capital away from a sentence start
 * @param sentenceInitial occurrences that start a sentence
 * @param standalone capitalised mid-sentence occurrences not beside another capitalised word
 * @param lowerShare lower case over lower case plus capitalised; 0 for a word never seen
 * @param segments how many segments hold the word
 * @param firstOrdinal the ordinal of the first segment holding it, or -1 for a word never seen
 */
public record WordStats(
        int total,
        int lowerCase,
        int capitalisedMidSentence,
        int sentenceInitial,
        int standalone,
        double lowerShare,
        int segments,
        int firstOrdinal) {

    /** The counts of a word the book never writes. */
    static final WordStats NONE = new WordStats(0, 0, 0, 0, 0, 0.0, 0, -1);
}
