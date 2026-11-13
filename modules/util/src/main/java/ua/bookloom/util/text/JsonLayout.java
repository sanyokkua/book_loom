package ua.bookloom.util.text;

import java.util.Objects;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Lays JSON out for reading without parsing it, so a body that is cut short or holds several documents (a streamed
 * reply is one per line) still shows. Text that is not JSON comes back unchanged.
 */
// Checkstyle's HideUtilityClassConstructor parses source text before Lombok's annotation processor runs (ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class JsonLayout {

    private static final String INDENT = "  ";

    /**
     * Indents each line of a body that holds JSON.
     *
     * @param body the non-null text; blank lines are dropped
     * @return the body with one member or element per line, or the body itself when it does not start with a brace or
     *     bracket; never null
     */
    public static String pretty(final String body) {
        Objects.requireNonNull(body, "body");
        final String trimmed = body.strip();
        if (trimmed.isEmpty() || (trimmed.charAt(0) != '{' && trimmed.charAt(0) != '[')) {
            return body;
        }
        final StringBuilder out = new StringBuilder(trimmed.length() * 2);
        trimmed.lines()
                .filter(line -> !line.isBlank())
                .forEach(line -> out.append(layOut(line.strip())).append('\n'));
        return out.toString().stripTrailing();
    }

    private static String layOut(final String json) {
        final StringBuilder out = new StringBuilder(json.length() * 2);
        int depth = 0;
        boolean inString = false;
        boolean escaped = false;
        for (int at = 0; at < json.length(); at++) {
            final char c = json.charAt(at);
            if (inString) {
                out.append(c);
                inString = !(c == '"' && !escaped);
                escaped = !escaped && c == '\\';
            } else {
                inString = c == '"';
                depth = outside(out, json, at, depth);
            }
        }
        return out.toString();
    }

    // Writes the character at `at`, which is outside any string, and returns the depth after it.
    private static int outside(final StringBuilder out, final String json, final int at, final int depth) {
        final char c = json.charAt(at);
        if (isOpen(c)) {
            out.append(c);
            final boolean empty = at + 1 < json.length() && isClose(json.charAt(at + 1));
            return empty ? depth : breakAfter(out, depth + 1);
        }
        if (isClose(c)) {
            final boolean empty = at > 0 && isOpen(json.charAt(at - 1));
            final int after = empty ? depth : breakAfter(out, depth - 1);
            out.append(c);
            return after;
        }
        if (c == ',') {
            out.append(c);
            return breakAfter(out, depth);
        }
        if (c == ':') {
            out.append(": ");
        } else if (!Character.isWhitespace(c)) {
            out.append(c);
        }
        return depth;
    }

    private static int breakAfter(final StringBuilder out, final int depth) {
        out.append('\n').append(INDENT.repeat(Math.max(0, depth)));
        return depth;
    }

    private static boolean isOpen(final char c) {
        return c == '{' || c == '[';
    }

    private static boolean isClose(final char c) {
        return c == '}' || c == ']';
    }
}
