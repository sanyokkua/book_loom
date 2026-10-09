package ua.bookloom.api.pipeline;

import java.util.Objects;

/**
 * A character in the glossary whose gender is still unknown, with how often the book names it, so Start can ask about
 * the ones that matter.
 *
 * @param term the glossary term of the character
 * @param mentions how many times the book's body text names the character
 */
public record UnknownGender(String term, int mentions) {

    /** Rejects a missing term or a negative count. */
    public UnknownGender {
        Objects.requireNonNull(term, "term");
        if (mentions < 0) {
            throw new IllegalArgumentException("mentions must not be negative: " + mentions);
        }
    }
}
