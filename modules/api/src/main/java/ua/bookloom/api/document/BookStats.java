package ua.bookloom.api.document;

import java.util.Objects;
import java.util.Set;

/**
 * A book's computed statistics ({@code specs/document-round-trip/spec.md} "Compute the book's statistics"), shown
 * on the structure/profile screen.
 *
 * @param segments the total number of translatable segments
 * @param words the total word count across all segments
 * @param images the number of images found
 * @param codeBlocks the number of code blocks found
 * @param fonts the number of embedded fonts found
 * @param verseLines the number of verse/poetry lines found
 * @param footnotes the number of footnotes found
 * @param tables the number of tables found
 * @param formatting the kinds of inline formatting present anywhere in the book, defensively copied and
 *     unmodifiable
 */
public record BookStats(
        int segments,
        int words,
        int images,
        int codeBlocks,
        int fonts,
        int verseLines,
        int footnotes,
        int tables,
        Set<Formatting> formatting) {

    /**
     * Validates that every count is non-negative and defensively copies {@code formatting} so a caller-held
     * mutable set cannot corrupt this record after construction.
     */
    public BookStats {
        Objects.requireNonNull(formatting, "formatting");
        requireNonNegative("segments", segments);
        requireNonNegative("words", words);
        requireNonNegative("images", images);
        requireNonNegative("codeBlocks", codeBlocks);
        requireNonNegative("fonts", fonts);
        requireNonNegative("verseLines", verseLines);
        requireNonNegative("footnotes", footnotes);
        requireNonNegative("tables", tables);
        formatting = Set.copyOf(formatting);
    }

    private static void requireNonNegative(final String name, final int value) {
        if (value < 0) {
            throw new IllegalArgumentException(name + " must be >= 0, but was " + value);
        }
    }

    /**
     * A kind of inline formatting a book's content may carry.
     */
    public enum Formatting {

        /** Italic/emphasis spans. */
        ITALICS,

        /** Bold/strong spans. */
        BOLD,

        /** Hyperlinks. */
        LINKS,

        /** Block or inline quotations. */
        QUOTES,

        /** Inline or block code. */
        CODE,

        /** Explicit line breaks within a block. */
        LINE_BREAKS,

        /** A kind of formatting not covered by the other constants. */
        OTHER
    }
}
