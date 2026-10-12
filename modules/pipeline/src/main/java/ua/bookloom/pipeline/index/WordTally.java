package ua.bookloom.pipeline.index;

/**
 * The one rule by which a word occurrence is counted, shared by {@link WordCounts} (the scans' running-text view) and
 * the {@link BookIndex} build (every segment), so the two can differ only in which text they feed it.
 */
final class WordTally {

    private int lowerCase;
    private int capitalised;
    private int capitalisedMidSentence;
    private int sentenceInitial;
    private int standalone;

    /** Counts one occurrence; {@code isStandalone} is {@link Occurrences#isStandalone} for its place in its read. */
    void add(final Occurrences.Word word, final boolean isStandalone) {
        if (word.capitalised()) {
            capitalised++;
            capitalisedMidSentence += word.initial() ? 0 : 1;
        } else if (Character.isLowerCase(word.text().codePointAt(0))) {
            lowerCase++;
        }
        sentenceInitial += word.initial() ? 1 : 0;
        standalone += isStandalone ? 1 : 0;
    }

    int lowerCase() {
        return lowerCase;
    }

    int capitalisedMidSentence() {
        return capitalisedMidSentence;
    }

    int sentenceInitial() {
        return sentenceInitial;
    }

    int standalone() {
        return standalone;
    }

    double lowerShare() {
        final int seen = lowerCase + capitalised;
        return seen == 0 ? 0 : (double) lowerCase / seen;
    }
}
