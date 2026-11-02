package ua.bookloom.util.text;

import java.util.Objects;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Trims the slashes around a URL path. A loop stands in for the regular expressions {@code /+$} and {@code ^/+}
 * because those backtrack quadratically on a long run of slashes followed by another character.
 */
// Checkstyle's HideUtilityClassConstructor parses source text before Lombok's annotation processor runs (ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class Slashes {

    private static final char SLASH = '/';

    /**
     * Removes every slash at the end of the text.
     *
     * @param text the text to trim
     * @return the text without its trailing slashes; empty if it held only slashes
     */
    public static String trimTrailing(final String text) {
        Objects.requireNonNull(text, "text");
        int end = text.length();
        while (end > 0 && text.charAt(end - 1) == SLASH) {
            end--;
        }
        return text.substring(0, end);
    }

    /**
     * Removes every slash at the start of the text.
     *
     * @param text the text to trim
     * @return the text without its leading slashes; empty if it held only slashes
     */
    public static String trimLeading(final String text) {
        Objects.requireNonNull(text, "text");
        int start = 0;
        while (start < text.length() && text.charAt(start) == SLASH) {
            start++;
        }
        return text.substring(start);
    }
}
