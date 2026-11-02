package ua.bookloom.util.text;

import java.util.Objects;
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
