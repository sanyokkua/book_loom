package ua.bookloom.util.text;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Which characters end a line, named by number so no source file holds a raw separator character. Hand-written
 * scanners that stand in for a regular expression use it to read {@code .} (anything but a line terminator) and
 * {@code \R} (any line break) the way the expression did.
 */
// Checkstyle's HideUtilityClassConstructor parses source text before Lombok's annotation processor runs (ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class LineBreaks {

    private static final char NEXT_LINE = 0x85;
    private static final char LINE_SEPARATOR = 0x2028;
    private static final char PARAGRAPH_SEPARATOR = 0x2029;

    /**
     * Tells whether a character is one a {@code .} does not match: a line feed, a carriage return, a next line, a line
     * separator or a paragraph separator.
     *
     * @param c any character
     * @return {@code true} for a line terminator, {@code false} otherwise
     */
    public static boolean isTerminator(final char c) {
        return c == '\n' || c == '\r' || c == NEXT_LINE || c == LINE_SEPARATOR || c == PARAGRAPH_SEPARATOR;
    }

    /**
     * Tells whether a character is one {@code \R} matches: a line terminator, a vertical tab or a form feed.
     *
     * @param c any character
     * @return {@code true} for a line break, {@code false} otherwise
     */
    public static boolean isBreak(final char c) {
        return isTerminator(c) || c == '\u000B' || c == '\f';
    }
}
