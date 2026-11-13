package ua.bookloom.pipeline.narrator;

import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import ua.bookloom.util.text.LineBreaks;

/**
 * Cuts a paragraph at a stop mark that is followed by blanks, and at every line break. Scanning stands in for the
 * regular expression {@code (?<![.!?…])[.!?…]++\s++|\R++}, which tries every start of a long run of marks.
 */
// Checkstyle's HideUtilityClassConstructor parses source text before Lombok's annotation processor runs (ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class SentenceBreaks {

    private static final String MARKS = ".!?…";
    private static final String ASCII_BLANKS = " \t\n\u000B\f\r";

    /**
     * The pieces between the breaks.
     *
     * @param text the paragraph
     * @return the non-empty pieces in order; the breaks themselves are dropped
     */
    static List<String> split(final String text) {
        final List<String> pieces = new ArrayList<>();
        int pieceStart = 0;
        int i = 0;
        while (i < text.length()) {
            final int breakEnd = breakEnd(text, i);
            if (breakEnd > i) {
                addPiece(pieces, text, pieceStart, i);
                pieceStart = breakEnd;
                i = breakEnd;
            } else {
                i++;
            }
        }
        addPiece(pieces, text, pieceStart, text.length());
        return pieces;
    }

    private static void addPiece(final List<String> pieces, final String text, final int from, final int to) {
        if (to > from) {
            pieces.add(text.substring(from, to));
        }
    }

    // The end of the break that starts at i, or i when none starts there.
    private static int breakEnd(final String text, final int i) {
        final char c = text.charAt(i);
        if (LineBreaks.isBreak(c)) {
            return skipBreaks(text, i);
        }
        if (MARKS.indexOf(c) < 0 || (i > 0 && MARKS.indexOf(text.charAt(i - 1)) >= 0)) {
            return i;
        }
        final int marksEnd = skip(text, i, MARKS);
        final int blanksEnd = skip(text, marksEnd, ASCII_BLANKS);
        return blanksEnd > marksEnd ? blanksEnd : i;
    }

    private static int skipBreaks(final String text, final int from) {
        int end = from;
        while (end < text.length() && LineBreaks.isBreak(text.charAt(end))) {
            end++;
        }
        return end;
    }

    private static int skip(final String text, final int from, final String members) {
        int end = from;
        while (end < text.length() && members.indexOf(text.charAt(end)) >= 0) {
            end++;
        }
        return end;
    }
}
