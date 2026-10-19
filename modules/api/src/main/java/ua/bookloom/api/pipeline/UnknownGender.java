package ua.bookloom.api.pipeline;

import java.util.Objects;

/**
 * A character in the glossary whose gender is not settled, with how often the book names it, so Start can ask about
 * the ones that matter.
 *
 * @param term the glossary term of the character
 * @param mentions how many times the book's body text names the character
 * @param suggested whether the glossary holds a suggested gender nobody confirmed, as against no gender at all
 */
public record UnknownGender(String term, int mentions, boolean suggested) {

    /** Rejects a missing term or a negative count. */
    public UnknownGender {
        Objects.requireNonNull(term, "term");
        if (mentions < 0) {
            throw new IllegalArgumentException("mentions must not be negative: " + mentions);
        }
    }

    /**
     * A character with no gender at all.
     *
     * @param term the glossary term of the character
     * @param mentions how many times the book's body text names the character
     */
    public UnknownGender(final String term, final int mentions) {
        this(term, mentions, false);
    }
}
