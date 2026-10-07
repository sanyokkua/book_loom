package ua.bookloom.pipeline;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * The C0 and DEL control characters a model reply must never carry. A model that writes its quote marks and dashes as
 * U+001C-U+001F leaves text that {@link Character#isWhitespace(int)} calls spacing, so trimming would silently drop an
 * opening mark and a refusal would hide the cause; such a reply is rejected whole instead. Tab, line feed and carriage
 * return are ordinary text.
 */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ControlCharacters {

    private static final char DEL = '\u007f';

    /**
     * Whether a text holds a control character other than tab, line feed and carriage return.
     *
     * @param text the non-null text
     * @return {@code true} if it holds one, {@code false} otherwise
     */
    public static boolean containsControl(final String text) {
        return text.chars().anyMatch(ControlCharacters::isControl);
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
