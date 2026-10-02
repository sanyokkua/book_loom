package ua.bookloom.document.mask;

import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Whether a plain-text paragraph is laid out line by line — a list, a stanza, a table of contents — so each line end
 * is part of what a translation must keep, rather than prose hard-wrapped at a column, which a translation reflows.
 */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class LineStructure {

    /**
     * The longest line, in characters, that still reads as a verse or list line. Hard-wrapped prose wraps at 65–80
     * columns; a paragraph wrapped narrower is taken for verse, which only pins its translation to the same lines.
     */
    static final int MAX_SHORT_LINE = 60;

    /** A bullet, dash or number that opens a list item. */
    private static final Pattern LIST_MARKER = Pattern.compile("^\\s*(?:[•●▪◦‣*+\\-–—]|\\d{1,3}[.)])\\s");

    /** The fewest list-item lines that make a paragraph a list. */
    private static final int MIN_LIST_ITEMS = 2;

    /**
     * Decides whether a paragraph's line ends belong to its structure.
     *
     * @param text the paragraph as written; never null
     * @return {@code true} if it has at least two lines and either two of them open with a list marker or none is
     *     longer than {@value #MAX_SHORT_LINE} characters, {@code false} otherwise
     */
    public static boolean isLineBased(String text) {
        Objects.requireNonNull(text, "text");
        final List<String> lines = text.lines().toList();
        if (lines.size() < 2) {
            return false;
        }
        final long listItems =
                lines.stream().filter(line -> LIST_MARKER.matcher(line).find()).count();
        return listItems >= MIN_LIST_ITEMS
                || lines.stream()
                        .allMatch(line ->
                                line.strip().codePointCount(0, line.strip().length()) <= MAX_SHORT_LINE);
    }
}
