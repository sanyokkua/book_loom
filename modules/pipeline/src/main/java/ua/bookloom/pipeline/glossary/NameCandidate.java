package ua.bookloom.pipeline.glossary;

import java.util.List;
import java.util.Objects;

/**
 * A capitalised word or run that a scan found, with how often and where.
 *
 * @param term the word, or the two or three words joined by single spaces
 * @param count how many times it occurs outside a sentence start, its aliases included
 * @param firstSentence the first sentence that holds it, whitespace collapsed
 * @param aliases the spellings folded into it (a plural, a one-letter misspelling), which share its target; never null
 * @param readingOnly whether the scan kept it only for the names reading to decide (15i.N5): a word the book also
 *     writes in lower case often enough to be a common word, but often enough with a capital mid-sentence to be a
 *     name too; a scan that no reading follows drops it
 */
public record NameCandidate(String term, int count, String firstSentence, List<String> aliases, boolean readingOnly) {

    /** Validates the components. */
    public NameCandidate {
        Objects.requireNonNull(term, "term");
        Objects.requireNonNull(firstSentence, "firstSentence");
        aliases = List.copyOf(aliases);
    }

    /** A candidate any scan proposes. */
    public NameCandidate(final String term, final int count, final String firstSentence, final List<String> aliases) {
        this(term, count, firstSentence, aliases, false);
    }

    /** A candidate any scan proposes, with no alias. */
    public NameCandidate(final String term, final int count, final String firstSentence) {
        this(term, count, firstSentence, List.of());
    }

    /**
     * This candidate, kept only for the names reading to decide.
     *
     * @return a copy marked {@link #readingOnly()}
     */
    public NameCandidate forReadingOnly() {
        return new NameCandidate(term, count, firstSentence, aliases, true);
    }
}
