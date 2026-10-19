package ua.bookloom.pipeline.reviewer;

/**
 * What the reviewer's gender and agreement edits are held to when a gender in the book is only a guess: the checks no
 * longer enforce it, so an edit that exists to make the text agree with it must not enforce it either (15i.T1).
 */
@FunctionalInterface
public interface GenderGuess {

    /** A chunk with no guessed gender: no edit favours one. */
    GenderGuess NONE = (before, after) -> false;

    /**
     * Whether an edit moves a text toward a gender that was only suggested.
     *
     * @param before the display text before the edit; never null
     * @param after the display text after the edit; never null
     * @return {@code true} when {@code after} agrees with a suggested gender in more places than {@code before} does,
     *     {@code false} otherwise
     */
    boolean isFavoured(String before, String after);
}
