package ua.bookloom.pipeline.context;

import java.util.Objects;

/**
 * One map line a call could carry in block 9, with what the window budgets need to choose which line goes first
 * when the prompt is over its ceiling (design D8).
 *
 * @param section the section of block 9 the line belongs to
 * @param line the line as sent
 * @param occurrences how many times the window names the entry; zero or more
 * @param kept whether the line is never dropped: a pinned or locked name, or a character's stated gender
 */
public record MapEntry(Section section, String line, int occurrences, boolean kept) {

    /** The sections of block 9, in the order {@link MapLines} holds them. */
    public enum Section {
        /** The person's and the code's glossary lines. */
        GLOSSARY,
        /** The locked names behind their tokens. */
        LOCKED,
        /** The entries whose target the model suggested. */
        SUGGESTED,
        /** The recurring terms' established renderings. */
        LEXICON
    }

    /** Rejects a missing part or a negative count. */
    public MapEntry {
        Objects.requireNonNull(section, "section");
        Objects.requireNonNull(line, "line");
        if (occurrences < 0) {
            throw new IllegalArgumentException("occurrences must not be negative: " + occurrences);
        }
    }
}
