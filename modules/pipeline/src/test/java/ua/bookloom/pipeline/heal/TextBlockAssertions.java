package ua.bookloom.pipeline.heal;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * What {@code DirectedFixTest}, {@code ReflectImproveTest} and {@code PolishTest} otherwise each repeated: reading a
 * rendered user message's {@code <Translation>} block(s) — the text a rewrite returns — back out for assertions.
 */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class TextBlockAssertions {

    private static final String OPENING = "<Translation>\n";
    private static final String CLOSING = "\n</Translation>";

    /** The sole {@code <Translation>} block's content of a rendered user message. */
    static String textBlockOf(final String userMessage) {
        final int start = userMessage.indexOf(OPENING);
        final int end = userMessage.indexOf(CLOSING, start + OPENING.length());
        return userMessage.substring(start + OPENING.length(), end);
    }

    /** How many {@code <Translation>} blocks a rendered user message holds. */
    static int textBlockCount(final String userMessage) {
        int count = 0;
        int index = 0;
        while ((index = userMessage.indexOf(OPENING, index)) != -1) {
            count++;
            index += OPENING.length();
        }
        return count;
    }
}
