package ua.bookloom.pipeline.typography;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Finds a spaced dash that opens a clause: an optional comma, blanks, a dash and one more blank, the blanks not
 * following another blank. Scanning stands in for a regular expression with a look-behind in front of a blank run,
 * which a long run of blanks makes quadratic.
 */
// Checkstyle's HideUtilityClassConstructor parses source text before Lombok's annotation processor runs (ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class DashClause {

    private static final String DASHES = "—–";

    /**
     * Where the first dash clause at or after a position starts.
     *
     * @param text the text to search
     * @param from where the search starts
     * @return the index of the clause's comma, or of its first blank when it has no comma; {@code -1} when none
     */
    static int find(final String text, final int from) {
        for (int i = Math.max(from, 0); i < text.length(); i++) {
            final int blanks = text.charAt(i) == ',' ? i + 1 : i;
            if (startsBlankRun(text, blanks) && isDashAfterBlanks(text, blanks)) {
                return i;
            }
        }
        return -1;
    }

    private static boolean startsBlankRun(final String text, final int at) {
        return at < text.length() && isBlank(text.charAt(at)) && (at == 0 || !isBlank(text.charAt(at - 1)));
    }

    private static boolean isDashAfterBlanks(final String text, final int blanksStart) {
        int end = blanksStart;
        while (end < text.length() && isBlank(text.charAt(end))) {
            end++;
        }
        return end + 1 < text.length() && DASHES.indexOf(text.charAt(end)) >= 0 && isBlank(text.charAt(end + 1));
    }

    private static boolean isBlank(final char c) {
        return c == ' ' || c == '\t' || c == '\n' || c == '\u000B' || c == '\f' || c == '\r' || c == ' ';
    }
}
