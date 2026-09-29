package ua.bookloom.pipeline.glossary;

import java.util.Objects;

/**
 * A capitalised word or run that a scan found, with how often and where.
 *
 * @param term the word, or the two or three words joined by single spaces
 * @param count how many times it occurs outside a sentence start
 * @param firstSentence the first sentence that holds it, whitespace collapsed
 */
public record NameCandidate(String term, int count, String firstSentence) {

    /** Validates the components. */
    public NameCandidate {
        Objects.requireNonNull(term, "term");
        Objects.requireNonNull(firstSentence, "firstSentence");
    }
}
