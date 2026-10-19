package ua.bookloom.pipeline.index;

import java.util.ArrayDeque;
import java.util.Deque;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Tells speech from narration at a position of a segment or a window: inside a quotation, or in a dash-led line before
 * the second dash (and between the third and fourth, since an interrupted line resumes), the text is spoken. A window
 * keeps its paragraphs on lines of their own, so a dash-led line of the next paragraph is read as speech there too.
 */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class Speech {

    private static final String DASHES = "—―–";
    private static final char STRAIGHT_QUOTE = '"';

    /**
     * Tells whether a position of a text lies in narration.
     *
     * @param text a segment's text or a window's; never null
     * @param position an offset from 0 to {@code text.length()}
     * @return {@code true} if the position is outside quotation marks and outside a speaker's dash-led line, {@code
     *     false} otherwise
     */
    public static boolean isNarration(final String text, final int position) {
        return !isQuoted(text, position) && !isDashSpeech(text, position);
    }

    private static boolean isQuoted(final String text, final int position) {
        final Deque<Character> expected = new ArrayDeque<>();
        boolean straight = false;
        for (int index = 0; index < position; index++) {
            final char letter = text.charAt(index);
            if (letter == STRAIGHT_QUOTE) {
                straight = !straight;
            } else if (!expected.isEmpty() && expected.peek() == letter) {
                expected.pop();
            } else if (closerOf(letter) != 0) {
                expected.push(closerOf(letter));
            }
        }
        return straight || !expected.isEmpty();
    }

    private static char closerOf(final char opener) {
        return switch (opener) {
            case '“' -> '”';
            case '«' -> '»';
            case '„' -> '“';
            case '‹' -> '›';
            default -> 0;
        };
    }

    // Only the line holding the position counts: a new line starts narration again, and a dash between two digits
    // (1990–1995) is a range, not a speaker's dash.
    private static boolean isDashSpeech(final String text, final int position) {
        final int lineStart = lineStartBefore(text, position);
        int first = lineStart;
        while (first < position && Character.isWhitespace(text.charAt(first))) {
            first++;
        }
        if (first >= position || DASHES.indexOf(text.charAt(first)) < 0) {
            return false;
        }
        int dashes = 0;
        for (int index = first; index < position; index++) {
            dashes += isSpeakerDash(text, index) ? 1 : 0;
        }
        return dashes % 2 == 1;
    }

    private static int lineStartBefore(final String text, final int position) {
        int start = position;
        while (start > 0 && text.charAt(start - 1) != '\n' && text.charAt(start - 1) != '\r') {
            start--;
        }
        return start;
    }

    private static boolean isSpeakerDash(final String text, final int index) {
        if (DASHES.indexOf(text.charAt(index)) < 0) {
            return false;
        }
        final boolean range = index > 0
                && index + 1 < text.length()
                && Character.isDigit(text.charAt(index - 1))
                && Character.isDigit(text.charAt(index + 1));
        return !range;
    }
}
