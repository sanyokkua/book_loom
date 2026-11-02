package ua.bookloom.document.md;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
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

    // Each form of a YAML number is its own pattern, so no single expression grows past a reviewable size.
    private static final List<Pattern> NUMBERS = Stream.of(
                    "[-+]?\\d[\\d_]*(?:\\.[\\d_]*)?(?:[eE][-+]?\\d+)?",
                    "[-+]?\\.\\d[\\d_]*(?:[eE][-+]?\\d+)?",
                    "[-+]?0(?:[xX][0-9a-fA-F_]+|[oO][0-7_]+|[bB][01_]+)",
                    "[-+]?\\.inf|\\.nan")
            .map(regex -> Pattern.compile(regex, Pattern.CASE_INSENSITIVE))
            .toList();

    // A date, then optionally a time, then optionally a zone; each part is read where the one before it ended.
    private static final Pattern DAY = Pattern.compile("\\d{4}-\\d{1,2}-\\d{1,2}");
    private static final Pattern TIME =
            Pattern.compile("(?:T|[ \\t]+)\\d{1,2}:\\d{2}(?::\\d{2}(?:\\.\\d*)?)?", Pattern.CASE_INSENSITIVE);
    private static final Pattern ZONE =
            Pattern.compile("[ \\t]*(?:Z|[-+]\\d{1,2}(?::?\\d{2})?)", Pattern.CASE_INSENSITIVE);

    private static final List<Pattern> ADDRESSES = Stream.of(
                    "[A-Za-z][A-Za-z0-9+.-]*://.*", "www\\..*", "mailto:.*", "[^\\s@]+@[^\\s@]+\\.[^\\s@]+")
            .map(regex -> Pattern.compile(regex, Pattern.CASE_INSENSITIVE))
            .toList();

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
                || NUMBERS.stream().anyMatch(number -> number.matcher(plain).matches())
                || isDate(plain);
    }

    private static boolean isDate(final String plain) {
        final Matcher day = DAY.matcher(plain);
        if (!day.lookingAt()) {
            return false;
        }
        final String afterDay = plain.substring(day.end());
        if (afterDay.isEmpty()) {
            return true;
        }
        final Matcher time = TIME.matcher(afterDay);
        if (!time.lookingAt()) {
            return false;
        }
        final String afterTime = afterDay.substring(time.end());
        return afterTime.isEmpty() || ZONE.matcher(afterTime).matches();
    }

    static boolean isAddress(String text) {
        return ADDRESSES.stream().anyMatch(address -> address.matcher(text).matches());
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
