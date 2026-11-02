package ua.bookloom.llm.response;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Unwraps a JSON string whose text is {@code \boxed{…}}, written in the string as {@code "\\boxed{…}"}. Scanning
 * stands in for a regular expression that repeats an alternation of a plain character and an escape, which Java
 * matches by recursion, one frame per character.
 */
// Checkstyle's HideUtilityClassConstructor parses source text before Lombok's annotation processor runs (ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class BoxedStrings {

    private static final String OPENING = "\"\\\\boxed{";
    private static final String CLOSING = "}\"";
    private static final String LINE_BREAKS = "\n\r\u0085  ";

    /**
     * The text after unwrapping.
     *
     * @param text the text with each boxed string replaced by a plain string of its content
     * @param count how many strings were unwrapped
     */
    record Unboxed(String text, int count) {}

    /**
     * Unwraps each boxed string of a reply.
     *
     * @param text the reply; never null
     * @return the unwrapped text, equal to the input when nothing was unwrapped, and the count
     */
    static Unboxed unwrap(final String text) {
        final boolean[] dead = new boolean[text.length()];
        final StringBuilder out = new StringBuilder();
        int copied = 0;
        int count = 0;
        int open = text.indexOf(OPENING);
        while (open >= 0) {
            final int contentStart = open + OPENING.length();
            final int close = closing(text, contentStart, dead);
            if (close >= 0) {
                out.append(text, copied, open)
                        .append('"')
                        .append(text, contentStart, close)
                        .append('"');
                copied = close + CLOSING.length();
                count++;
            }
            open = text.indexOf(OPENING, close >= 0 ? copied : open + 1);
        }
        return count == 0
                ? new Unboxed(text, 0)
                : new Unboxed(out.append(text, copied, text.length()).toString(), count);
    }

    // The index of the "}" that ends the content; -1 when there is none, and then every position the walk crossed is
    // remembered, since a later start that reaches one would end the same way.
    private static int closing(final String text, final int from, final boolean[] dead) {
        final int close = walk(text, from, dead, false);
        if (close < 0) {
            walk(text, from, dead, true);
        }
        return close;
    }

    private static int walk(final String text, final int from, final boolean[] dead, final boolean mark) {
        int at = from;
        while (at < text.length() && !dead[at]) {
            dead[at] |= mark;
            final char c = text.charAt(at);
            if (c == '}' && at + 1 < text.length() && text.charAt(at + 1) == '"') {
                return at;
            }
            if (c == '"' || (c == '\\' && (at + 1 >= text.length() || LINE_BREAKS.indexOf(text.charAt(at + 1)) >= 0))) {
                return -1;
            }
            at += c == '\\' ? 2 : 1;
        }
        return -1;
    }
}
