package ua.bookloom.api.pipeline;

import java.util.Objects;

/**
 * A character whose unknown gender holds segments back from a final rendering.
 *
 * @param character the glossary term of the character
 * @param segments how many segments wait on that character's gender; at least one
 */
public record GenderWait(String character, int segments) {

    /** Rejects a missing name or a count below one. */
    public GenderWait {
        Objects.requireNonNull(character, "character");
        if (segments < 1) {
            throw new IllegalArgumentException("a wait covers at least one segment: " + segments);
        }
    }
}
