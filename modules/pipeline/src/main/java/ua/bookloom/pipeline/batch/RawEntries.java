package ua.bookloom.pipeline.batch;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import ua.bookloom.util.text.LineBreaks;

/**
 * Finds the complete {@code {"id": …, ["notes": "…",] "target": "…"}} objects in a reply that is not valid JSON as a
 * whole. The string bodies are read by a loop, one pass with no recursion, so a target of any length is safe; each
 * fixed part is a small pattern.
 */
// Checkstyle's HideUtilityClassConstructor parses source text before Lombok's annotation processor runs (ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class RawEntries {

    private static final Pattern HEAD = Pattern.compile("\\{\\s*\"id\"\\s*:\\s*(\"?)([^\",}\\s]+)\\1\\s*,\\s*");
    private static final Pattern NOTES_KEY = Pattern.compile("\"notes\"\\s*:\\s*\"");
    private static final Pattern TARGET_KEY = Pattern.compile("\"target\"\\s*:\\s*\"");
    private static final Pattern COMMA = Pattern.compile("\\s*,\\s*");
    private static final Pattern CLOSE = Pattern.compile("\"\\s*[,}]");

    /**
     * One object found.
     *
     * @param id the id as written, without quotes
     * @param body the target's characters between its quotes, escapes not yet decoded
     */
    record Raw(String id, String body) {}

    /**
     * The objects of a text, in order.
     *
     * @param text the reply, never null
     * @return the objects found, never null; empty when none is complete
     */
    static List<Raw> find(final String text) {
        final List<Raw> found = new ArrayList<>();
        final Matcher head = HEAD.matcher(text);
        int from = 0;
        while (from <= text.length() && head.find(from)) {
            final int bodyStart = targetStart(text, head.end());
            final int close = bodyStart < 0 ? -1 : closingQuote(text, bodyStart);
            final Matcher end = CLOSE.matcher(text);
            if (close >= 0 && end.region(close, text.length()).lookingAt()) {
                found.add(new Raw(head.group(2), text.substring(bodyStart, close)));
                from = end.end();
            } else {
                from = head.start() + 1;
            }
        }
        return found;
    }

    // Where the target's string starts, after the notes when there are some; -1 when the shape breaks.
    private static int targetStart(final String text, final int from) {
        int at = from;
        final Matcher notes = NOTES_KEY.matcher(text).region(from, text.length());
        if (notes.lookingAt()) {
            final int notesClose = closingQuote(text, notes.end());
            final Matcher comma = COMMA.matcher(text);
            if (notesClose < 0 || !comma.region(notesClose + 1, text.length()).lookingAt()) {
                return -1;
            }
            at = comma.end();
        }
        final Matcher target = TARGET_KEY.matcher(text).region(at, text.length());
        return target.lookingAt() ? target.end() : -1;
    }

    // The index of the quote that ends a string whose body starts at from; -1 when it does not end.
    private static int closingQuote(final String text, final int from) {
        int i = from;
        while (i < text.length()) {
            final char c = text.charAt(i);
            if (c == '"') {
                return i;
            }
            if (c == '\\') {
                if (i + 1 >= text.length() || LineBreaks.isTerminator(text.charAt(i + 1))) {
                    return -1;
                }
                i++;
            }
            i++;
        }
        return -1;
    }
}
