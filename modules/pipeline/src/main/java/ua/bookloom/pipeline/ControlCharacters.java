package ua.bookloom.pipeline;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * The C0 and DEL control characters a model reply must never carry unless its source holds them. A model that writes its quote marks and dashes as
 * U+001C-U+001F leaves text that {@link Character#isWhitespace(int)} calls spacing, so trimming would silently drop an
 * opening mark and a refusal would hide the cause; such a reply is rejected whole instead, unless
 * {@link ControlCharacterMapper} can put each code back where a quote or a dash belongs. Tab, line feed and carriage
 * return are ordinary text.
 */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ControlCharacters {

    private static final char DEL = '\u007f';

    /**
     * Whether a text holds a control character more often than its source does. An old TXT or FB2 may hold a form
     * feed, a vertical tab or U+001C-U+001F as its own text; a reply may carry each of those back, as often as the
     * source holds it, and no other.
     *
     * @param source the non-null text the reply translates or edits
     * @param text the non-null reply text
     * @return {@code true} if the text holds a control character the source does not, or holds it more often
     */
    public static boolean addsControl(final String source, final String text) {
        return text.chars()
                .filter(ControlCharacters::isControl)
                .distinct()
                .anyMatch(c -> occurrences(text, c) > occurrences(source, c));
    }

    /**
     * Strips the whitespace round a text but keeps a control code at its edge: {@link String#strip()} calls U+001C-U+001F
     * whitespace, and a quote mark a model wrote as one would be lost before the mapper could read it.
     *
     * @param text the non-null text
     * @return the text without its leading and trailing spacing; control codes stay
     */
    public static String stripSpacing(final String text) {
        int start = 0;
        int end = text.length();
        while (start < end && isSpacing(text.charAt(start))) {
            start++;
        }
        while (end > start && isSpacing(text.charAt(end - 1))) {
            end--;
        }
        return text.substring(start, end);
    }

    private static boolean isSpacing(final char c) {
        return Character.isWhitespace(c) && !isControl(c);
    }

    private static long occurrences(final String text, final int c) {
        return text.chars().filter(each -> each == c).count();
    }

    /**
     * Whether a character is one of the control characters that is not ordinary text.
     *
     * @param c the character
     * @return {@code true} for C0 controls except tab, line feed and carriage return, and for DEL
     */
    public static boolean isControl(final int c) {
        return (c < ' ' && c != '\t' && c != '\n' && c != '\r') || c == DEL;
    }
}
