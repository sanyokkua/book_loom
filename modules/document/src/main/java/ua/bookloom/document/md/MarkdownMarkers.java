package ua.bookloom.document.md;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.commonmark.node.ListItem;
import org.commonmark.node.Node;

/**
 * Derives the character position of a marker-led block construct's marker for {@link MarkdownEscaper} — split out
 * of that class to keep it within its size budget, the same way {@link HardLineBreakDeletion} and
 * {@link AsciiPunctuation} are; this logic has no other caller.
 */
// Checkstyle's HideUtilityClassConstructor parses source text before Lombok's annotation processor runs,
// so it cannot see the private constructor @NoArgsConstructor generates below; suppressed per the escape
// hatch checkstyle.xml documents for exactly this case (java-coding-style.md, ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class MarkdownMarkers {

    /**
     * The first non-whitespace character within {@code node}'s own range — for an ATX heading, a block quote or a
     * thematic break, the marker character itself.
     *
     * <p><strong>It is not always a marker, and the earlier claim here that a construct's own delimiter "always
     * sits at the range's start" is false.</strong> A <em>setext</em> heading's marker is the {@code ---}/{@code
     * ===} underline on the <em>next</em> line, and its span includes that underline ({@link MarkdownSpans}
     * records the same fact), so the range starts at the heading's content and this returns the first letter of
     * that content. A backslash before a letter is not a CommonMark escape — it renders as a literal, visible
     * backslash — and the text still parses as a setext heading afterwards, so the escape loop used to insert one
     * more at the same position every round: measured, twenty backslashes ahead of a translated heading, in 2,330
     * of 35,290 accepted restores over the real corpus. That is why every position this returns is checked for
     * escapability before it becomes an insertion, rather than trusted to be a marker.
     *
     * @param node the marker-led block construct
     * @param text the text the node's spans index
     * @return the position of the first non-whitespace character in the node's range, or the range's end when it
     *     holds nothing but whitespace
     */
    static int firstNonWhitespace(Node node, String text) {
        final int start = MarkdownSpans.firstSpanStart(node);
        final int end = MarkdownSpans.lastSpanEnd(node);
        int index = start;
        while (index < end && Character.isWhitespace(text.charAt(index))) {
            index++;
        }
        return index;
    }

    /**
     * The last non-whitespace character of a list item's marker run — the {@code -} of a bullet or the {@code .}
     * / {@code )} of an ordered item, taken as the run between the item's own start and its first child's start.
     * A childless item has no marker-relative content to bound the run by, so its own range end stands in.
     *
     * @param item the list item whose marker is located
     * @param text the text the item's spans index
     * @return the position of the marker's last non-whitespace character
     */
    static int listItemMarkerPosition(ListItem item, String text) {
        final int start = MarkdownSpans.firstSpanStart(item);
        final int markerEnd = item.getFirstChild() != null
                ? MarkdownSpans.firstSpanStart(item.getFirstChild())
                : MarkdownSpans.lastSpanEnd(item);
        int index = markerEnd - 1;
        while (index > start && Character.isWhitespace(text.charAt(index))) {
            index--;
        }
        return index;
    }
}
