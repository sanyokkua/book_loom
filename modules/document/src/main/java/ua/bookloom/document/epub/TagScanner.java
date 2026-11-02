package ua.bookloom.document.epub;

import org.jspecify.annotations.Nullable;

/**
 * Finds start tags in markup text, reading a quoted attribute value as one unit so a {@code >} or {@code /} inside it
 * cannot end a tag. Scanning stands in for the regular expressions {@code <name (?:"[^"]*"|'[^']*'|[^"'>])*>} and its
 * self-closing twin, which Java matches by recursion, one frame per character of the tag.
 *
 * <p>A tag that fails to close is remembered by the positions it crossed: a later start that reaches one of them would
 * fail the same way, so no stretch of text is read twice and a text of any length costs one pass.
 */
final class TagScanner {

    /**
     * An element written in XML self-closing form.
     *
     * @param name the tag name, as written
     * @param attributes everything between the name and the closing {@code />}
     * @param end where the element ends in the text, after the {@code />}
     */
    record SelfClosed(String name, String attributes, int end) {}

    private final String text;
    private final boolean[] crossed;

    TagScanner(final String text) {
        this.text = text;
        this.crossed = new boolean[text.length()];
    }

    /**
     * The next place a tag can start: a {@code <} and a letter.
     *
     * @param from where to look
     * @return the index of the {@code <}, or {@code -1} when there is none
     */
    int nextTag(final int from) {
        for (int at = text.indexOf('<', from); at >= 0; at = text.indexOf('<', at + 1)) {
            if (at + 1 < text.length() && isAsciiLetter(text.charAt(at + 1))) {
                return at;
            }
        }
        return -1;
    }

    /**
     * Where a start tag ends.
     *
     * @param open the index of its {@code <}, from {@link #nextTag(int)}
     * @return the index after its {@code >}, or {@code -1} when it never closes
     */
    int startTagEnd(final int open) {
        int at = open + 1;
        while (at < text.length() && isNameChar(text.charAt(at), true)) {
            at++;
        }
        final int close = bodyEnd(at, false);
        return close < 0 ? -1 : close + 1;
    }

    /**
     * Reads an element written as {@code <name attributes/>}.
     *
     * @param open the index of its {@code <}, from {@link #nextTag(int)}
     * @return the element, or {@code null} when the tag is not written self-closed
     */
    @Nullable
    SelfClosed selfClosedAt(final int open) {
        int nameEnd = open + 1;
        while (nameEnd < text.length() && isNameChar(text.charAt(nameEnd), false)) {
            nameEnd++;
        }
        int boundary = nameEnd;
        while (boundary > open + 1 && !isWordBoundary(boundary)) {
            boundary--;
        }
        if (boundary == open + 1) {
            return null;
        }
        final int slash = bodyEnd(nameEnd, true);
        if (slash < 0) {
            return null;
        }
        return new SelfClosed(text.substring(open + 1, boundary), text.substring(boundary, slash), slash + 2);
    }

    // The index of the ">" that closes a tag, or of the "/" of a "/>" when slashCloses; -1 when the tag never closes.
    private int bodyEnd(final int from, final boolean slashCloses) {
        final int end = walk(from, slashCloses, false);
        if (end < 0) {
            walk(from, slashCloses, true);
        }
        return end;
    }

    // One pass over a tag's body; with mark set, the positions crossed are remembered as ones that lead nowhere.
    private int walk(final int from, final boolean slashCloses, final boolean mark) {
        int at = from;
        while (at >= 0 && at < text.length() && !crossed[at]) {
            crossed[at] |= mark;
            final char c = text.charAt(at);
            if (c == '>') {
                return slashCloses ? -1 : at;
            }
            if (slashCloses && c == '/' && at + 1 < text.length() && text.charAt(at + 1) == '>') {
                return at;
            }
            at = c == '"' || c == '\'' ? afterQuote(at) : at + 1;
        }
        return -1;
    }

    private int afterQuote(final int quote) {
        final int close = text.indexOf(text.charAt(quote), quote + 1);
        return close < 0 ? -1 : close + 1;
    }

    // \b between the name's last character and the next one, where only ASCII letters, digits and "_" are word
    // characters.
    private boolean isWordBoundary(final int at) {
        final boolean before = isWord(text.charAt(at - 1));
        final boolean after = at < text.length() && isWord(text.charAt(at));
        return before != after;
    }

    private static boolean isWord(final char c) {
        return isAsciiLetter(c) || (c >= '0' && c <= '9') || c == '_';
    }

    private static boolean isAsciiLetter(final char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z');
    }

    private static boolean isNameChar(final char c, final boolean allowColon) {
        return isAsciiLetter(c) || (c >= '0' && c <= '9') || c == '-' || (allowColon && c == ':');
    }
}
