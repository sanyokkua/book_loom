package ua.bookloom.pipeline.reviewer;

import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Finds the first letter of each sentence: after the text's start, or after a stop mark, its closing marks and blanks,
 * then any opening marks, dashes and blanks. Scanning stands in for a regular expression whose blank runs overlap and
 * so are tried again from every start.
 */
// Checkstyle's HideUtilityClassConstructor parses source text before Lombok's annotation processor runs (ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class SentenceStarts {

    private static final String STOPS = ".!?…";
    private static final String CLOSERS = "»”\"’)";
    private static final String OPENERS = "«„“\"'([—–-";
    private static final String BLANKS = " \t\n\u000B\f\r";

    /**
     * The positions of the sentence-initial letters.
     *
     * @param text the text to read
     * @return the index of each first letter, in order and never null; empty when none
     */
    static List<Integer> of(final String text) {
        final List<Integer> starts = new ArrayList<>();
        int i = 0;
        while (i <= text.length()) {
            final int letter = letterAt(text, i);
            if (letter >= 0) {
                starts.add(letter);
                i = letter + Character.charCount(text.codePointAt(letter));
            } else {
                i++;
            }
        }
        return starts;
    }

    // The index of the sentence-initial letter of a match that begins at i, or -1.
    private static int letterAt(final String text, final int i) {
        if (i == 0) {
            final int fromStart = afterOpeners(text, 0);
            if (fromStart >= 0) {
                return fromStart;
            }
        }
        if (i >= text.length() || STOPS.indexOf(text.charAt(i)) < 0) {
            return -1;
        }
        int at = i + 1;
        while (at < text.length() && CLOSERS.indexOf(text.charAt(at)) >= 0) {
            at++;
        }
        if (at >= text.length() || BLANKS.indexOf(text.charAt(at)) < 0) {
            return -1;
        }
        return afterOpeners(text, at);
    }

    private static int afterOpeners(final String text, final int from) {
        int at = from;
        while (at < text.length() && (OPENERS.indexOf(text.charAt(at)) >= 0 || BLANKS.indexOf(text.charAt(at)) >= 0)) {
            at++;
        }
        return at < text.length() && Character.isLetter(text.codePointAt(at)) ? at : -1;
    }
}
