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
 */
public record NameCandidate(String term, int count, String firstSentence, List<String> aliases) {

    /** Validates the components. */
    public NameCandidate {
        Objects.requireNonNull(term, "term");
        Objects.requireNonNull(firstSentence, "firstSentence");
        aliases = List.copyOf(aliases);
    }

    /** A candidate with no alias. */
    public NameCandidate(final String term, final int count, final String firstSentence) {
        this(term, count, firstSentence, List.of());
    }
}
