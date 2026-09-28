package ua.bookloom.document.md;

import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * What a YAML reader makes of an unquoted scalar, kept to the few types a translation could break: a null, a
 * boolean, a number or a date is data a site generator parses, and an address is not prose. It is a flat check
 * and not a YAML parser for the reason {@link Frontmatter} gives.
 */
// Checkstyle's HideUtilityClassConstructor parses source text before Lombok's annotation processor runs (ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class YamlScalars {

    private static final Pattern NULL_OR_BOOLEAN =
            Pattern.compile("~|null|true|false|yes|no|on|off|y|n", Pattern.CASE_INSENSITIVE);

    private static final Pattern NUMBER = Pattern.compile(
            "[-+]?\\d[\\d_]*(?:\\.[\\d_]*)?(?:[eE][-+]?\\d+)?"
                    + "|[-+]?\\.\\d[\\d_]*(?:[eE][-+]?\\d+)?"
                    + "|[-+]?0[xX][0-9a-fA-F_]+|[-+]?0[oO][0-7_]+|[-+]?0[bB][01_]+"
                    + "|[-+]?\\.inf|\\.nan",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern DATE = Pattern.compile(
            "\\d{4}-\\d{1,2}-\\d{1,2}"
                    + "(?:(?:T|[ \\t]+)\\d{1,2}:\\d{2}(?::\\d{2}(?:\\.\\d*)?)?(?:[ \\t]*(?:Z|[-+]\\d{1,2}(?::?\\d{2})?))?)?",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern ADDRESS = Pattern.compile(
            "[A-Za-z][A-Za-z0-9+.-]*://.*|www\\..*|mailto:.*|[^\\s@]+@[^\\s@]+\\.[^\\s@]+", Pattern.CASE_INSENSITIVE);

    /** A plain scalar starting with any of these is not a plain scalar: it is structure, or must be quoted. */
    private static final String INDICATORS = "-?:,[]{}#&*!|>'\"%@`";

    /**
     * Whether a YAML reader reads {@code plain}, written without quotes, as something other than a string.
     *
     * @param plain the value as it would sit after the colon
     * @return {@code true} for a null (including nothing at all), a boolean, a number or a date
     */
    static boolean isTyped(String plain) {
        return plain.isEmpty()
                || NULL_OR_BOOLEAN.matcher(plain).matches()
                || NUMBER.matcher(plain).matches()
                || DATE.matcher(plain).matches();
    }

    static boolean isAddress(String text) {
        return ADDRESS.matcher(text).matches();
    }

    static boolean hasLetter(String text) {
        return text.chars().anyMatch(Character::isLetter);
    }

    /**
     * Whether a translation written as a plain scalar would stop reading as the same text.
     *
     * @param text the translated value
     * @return {@code true} when it must be wrapped in quotes
     */
    static boolean needsQuotes(String text) {
        return isTyped(text)
                || text.contains(": ")
                || text.contains(" #")
                || text.endsWith(":")
                || !text.equals(text.strip())
                || INDICATORS.indexOf(text.charAt(0)) >= 0;
    }
}
