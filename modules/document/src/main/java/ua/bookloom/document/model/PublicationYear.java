package ua.bookloom.document.model;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;

/**
 * Reduces whatever a book writes in its date field to a bare year. Dates in the wild are timestamps, prose
 * ("March 12, 1895"), ranges and junk; the first run of exactly four digits is the one reading all of them agree on,
 * and a longer digit run (a compact date, a five-digit number) is refused rather than guessed at.
 */
// Checkstyle's HideUtilityClassConstructor parses source text before Lombok's annotation processor runs,
// so it cannot see the private constructor @NoArgsConstructor generates below; suppressed per the escape
// hatch checkstyle.xml documents for exactly this case (java-coding-style.md, ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class PublicationYear {

    private static final Pattern FOUR_DIGITS = Pattern.compile("(?<!\\d)\\d{4}(?!\\d)");

    /**
     * Finds the year in a written date.
     *
     * @param written the date as the book wrote it; may be null or blank
     * @return the first run of exactly four digits, or empty when the text has none
     */
    public static Optional<String> of(final @Nullable String written) {
        if (written == null) {
            return Optional.empty();
        }
        final Matcher matcher = FOUR_DIGITS.matcher(written);
        return matcher.find() ? Optional.of(matcher.group()) : Optional.empty();
    }
}
