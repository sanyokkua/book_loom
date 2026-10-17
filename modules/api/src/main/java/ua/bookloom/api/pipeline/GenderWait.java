package ua.bookloom.api.pipeline;

import java.util.Objects;

/**
 * A character whose gender is not known for certain and so holds segments back from a final rendering.
 *
 * @param character the glossary term of the character
 * @param segments how many segments wait on that character's gender; at least one
 * @param suggested whether the glossary holds a suggested gender nobody confirmed (the person only has to confirm it),
 *     as against no gender at all (the person has to set it)
 */
public record GenderWait(String character, int segments, boolean suggested) {

    /** Rejects a missing name or a count below one. */
    public GenderWait {
        Objects.requireNonNull(character, "character");
        if (segments < 1) {
            throw new IllegalArgumentException("a wait covers at least one segment: " + segments);
        }
    }

    /**
     * A wait on a character whose gender is unknown.
     *
     * @param character the glossary term of the character
     * @param segments how many segments wait; at least one
     */
    public GenderWait(final String character, final int segments) {
        this(character, segments, false);
    }
}
