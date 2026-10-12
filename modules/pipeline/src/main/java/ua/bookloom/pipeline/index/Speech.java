package ua.bookloom.pipeline.index;

import java.util.ArrayDeque;
import java.util.Deque;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Tells speech from narration at a position of a segment: inside a quotation, or in a dash-led line before the second
 * dash (and between the third and fourth, since an interrupted line resumes), the text is spoken.
 */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class Speech {

    private static final String DASHES = "—―–";
    private static final char STRAIGHT_QUOTE = '"';

    static boolean isNarration(final String text, final int position) {
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

    private static boolean isDashSpeech(final String text, final int position) {
        final String line = text.stripLeading();
        if (line.isEmpty() || DASHES.indexOf(line.charAt(0)) < 0) {
            return false;
        }
        final long dashes = text.substring(0, position)
                .chars()
                .filter(dash -> DASHES.indexOf(dash) >= 0)
                .count();
        return dashes % 2 == 1;
    }
}
