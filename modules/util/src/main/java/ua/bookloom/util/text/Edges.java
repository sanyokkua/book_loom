package ua.bookloom.util.text;

import java.util.Objects;
import java.util.function.IntPredicate;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Strips a set of characters from both ends of a text. Scanning stands in for the regular expression
 * {@code ^[set]+|[set]+$}, whose end-anchored half rescans a long run once per character and so runs in quadratic time.
 */
// Checkstyle's HideUtilityClassConstructor parses source text before Lombok's annotation processor runs (ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class Edges {

    /**
     * Tells whether a code point is neither a letter nor a number, the kind of character a term is trimmed of.
     *
     * @param codePoint any code point
     * @return {@code true} if it is not a letter and not a decimal, letter or other number, {@code false} otherwise
     */
    public static boolean isNeitherLetterNorNumber(final int codePoint) {
        final int type = Character.getType(codePoint);
        return !Character.isLetter(codePoint)
                && type != Character.DECIMAL_DIGIT_NUMBER
                && type != Character.LETTER_NUMBER
                && type != Character.OTHER_NUMBER;
    }

    /**
     * Removes every leading and trailing code point that {@code removable} accepts.
     *
     * @param text the text to strip
     * @param removable tells whether a code point is dropped at the ends
     * @return the text between its first and last code point that is kept; empty when none is
     */
    public static String strip(final String text, final IntPredicate removable) {
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(removable, "removable");
        int start = 0;
        int end = text.length();
        while (start < end && removable.test(text.codePointAt(start))) {
            start += Character.charCount(text.codePointAt(start));
        }
        while (end > start && removable.test(text.codePointBefore(end))) {
            end -= Character.charCount(text.codePointBefore(end));
        }
        return text.substring(start, end);
    }

    /**
     * Removes every leading and trailing character found in {@code characters}.
     *
     * @param text the text to strip
     * @param characters the characters to remove; a character in it is removed at both ends, whitespace included only
     *     when listed
     * @return the text between its first and last character that is not in {@code characters}; empty when none is
     */
    public static String strip(final String text, final String characters) {
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(characters, "characters");
        int start = 0;
        int end = text.length();
        while (start < end && characters.indexOf(text.charAt(start)) >= 0) {
            start++;
        }
        while (end > start && characters.indexOf(text.charAt(end - 1)) >= 0) {
            end--;
        }
        return text.substring(start, end);
    }
}
