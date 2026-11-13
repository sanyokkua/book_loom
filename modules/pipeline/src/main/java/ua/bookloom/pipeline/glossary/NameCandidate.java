package ua.bookloom.pipeline.glossary;

import java.util.ArrayList;
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
 * @param lemmaInferred whether the term is a base form the scan inferred from two inflected forms that agree on it,
 *     though the book never writes it ({@link NameGroups})
 */
public record NameCandidate(
        String term,
        int count,
        String firstSentence,
        List<String> aliases,
        boolean readingOnly,
        boolean lemmaInferred) {

    /** Validates the components. */
    public NameCandidate {
        Objects.requireNonNull(term, "term");
        Objects.requireNonNull(firstSentence, "firstSentence");
        aliases = List.copyOf(aliases);
    }

    /** A candidate that may be kept only for the names reading, its term as written. */
    public NameCandidate(
            final String term,
            final int count,
            final String firstSentence,
            final List<String> aliases,
            final boolean readingOnly) {
        this(term, count, firstSentence, aliases, readingOnly, false);
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
        return new NameCandidate(term, count, firstSentence, aliases, true, lemmaInferred);
    }

    /**
     * This candidate with more spellings folded into it.
     *
     * @param more the non-null spellings to add after its own aliases
     * @param uses how many uses they add to its count
     * @return a copy with the aliases and the count added; its term, first sentence and marks kept
     */
    public NameCandidate withAliases(final List<String> more, final int uses) {
        final List<String> all = new ArrayList<>(aliases);
        all.addAll(more);
        return new NameCandidate(term, count + uses, firstSentence, all, readingOnly, lemmaInferred);
    }
}
