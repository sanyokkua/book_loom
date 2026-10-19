package ua.bookloom.pipeline.typography;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** What the speech rules read off a source paragraph and a target's edges. */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class QuoteMarks {

    private static final String OPENERS = "\"“‘'«„";
    private static final String RUN_OPENERS = "“«„";
    private static final char STRAIGHT = '"';

    /** Whether the source's first visible character is a quote mark of any style. */
    static boolean opensWithQuote(final String source) {
        final String stripped = source.strip();
        return !stripped.isEmpty() && OPENERS.indexOf(stripped.charAt(0)) >= 0;
    }

    /** How many quoted runs a source holds: its curly or angle openers plus half its straight marks. */
    static int runsIn(final String source) {
        final String clean = source;
        final long openers =
                clean.chars().filter(c -> RUN_OPENERS.indexOf(c) >= 0).count();
        final long straight = clean.chars().filter(c -> c == STRAIGHT).count();
        return (int) (openers + straight / 2);
    }

    /** Index of the first character that is neither white space nor a quote mark, or -1 when there is none. */
    static int firstContent(final String text) {
        for (int i = 0; i < text.length(); i++) {
            final char c = text.charAt(i);
            if (!Character.isWhitespace(c) && OPENERS.indexOf(c) < 0 && c != '»' && c != '”' && c != '“') {
                return i;
            }
        }
        return -1;
    }
}
