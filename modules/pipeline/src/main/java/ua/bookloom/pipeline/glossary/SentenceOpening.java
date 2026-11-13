package ua.bookloom.pipeline.glossary;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Tells whether a text ends where a sentence opens: at its start, or after a closing stop mark with any closing
 * quotation marks or brackets and then any opening marks and blanks. Scanning back from the end stands in for the
 * regular expression {@code (?:^|[.!?…]["'”’»)\]]*)[\s"'“‘«(]*$}, which tries every start of a long run of blanks.
 */
// Checkstyle's HideUtilityClassConstructor parses source text before Lombok's annotation processor runs (ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class SentenceOpening {

    private static final String STOPS = ".!?…";
    private static final String CLOSERS = "\"'”’»)]";
    private static final String OPENERS = "\"'“‘«(";
    private static final String BLANKS = " \t\n\u000B\f\r";

    /**
     * Whether a sentence opens at the end of a text.
     *
     * @param text the text before a word; never null
     * @return {@code true} if the text is empty, only opening marks and blanks, or ends in a stop mark, closers, then
     *     opening marks and blanks; {@code false} otherwise
     */
    static boolean endsAtOpening(final String text) {
        int opened = text.length();
        while (opened > 0 && isOpener(text.charAt(opened - 1))) {
            opened--;
        }
        if (opened == 0) {
            return true;
        }
        int stop = opened - 1;
        while (stop >= 0 && CLOSERS.indexOf(text.charAt(stop)) >= 0) {
            stop--;
        }
        for (int end = opened; end <= text.length(); end++) {
            if (stop >= 0 && STOPS.indexOf(text.charAt(stop)) >= 0) {
                return true;
            }
            if (end < text.length() && CLOSERS.indexOf(text.charAt(end)) < 0) {
                stop = end;
            }
        }
        return false;
    }

    private static boolean isOpener(final char c) {
        return OPENERS.indexOf(c) >= 0 || BLANKS.indexOf(c) >= 0;
    }
}
