package ua.bookloom.document.md;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.commonmark.node.Emphasis;
import org.commonmark.node.HardLineBreak;
import org.commonmark.node.Link;
import org.commonmark.node.Node;
import org.commonmark.node.SoftLineBreak;
import org.commonmark.node.StrongEmphasis;
import org.commonmark.node.Text;
import org.jspecify.annotations.Nullable;
import ua.bookloom.document.mask.MaskWriter;
import ua.bookloom.document.mask.MaskedContent;

/**
 * Masks a Markdown leaf block by the source ranges its inline children occupy — the Markdown counterpart of
 * {@code ua.bookloom.document.model.TreeMasker}, kept here rather than in {@code .mask} because it walks
 * commonmark's {@link Node} tree and {@code .mask} must stay format-agnostic (design.md D7, D10).
 *
 * <p><strong>Never read {@link Text#getLiteral()} to build the masked form.</strong> Measured on commonmark
 * 0.24.0, a {@code Text} node's literal is already un-escaped (a backslash-escaped {@code *} decodes to a bare
 * {@code *}) and already un-referenced (a character reference such as an ampersand entity decodes to the literal
 * character it names) — the parser has done this decoding before this class ever sees the node. Markdown
 * reassembles by splicing bytes into the original buffer, so writing a re-spelled literal back would change what
 * the next parse sees. Every range this class emits is therefore read out of the raw source text via
 * {@code text.substring(start, end)}, and every gap between ranges is carried through unchanged by
 * {@link MaskWriter#appendCharacterData(String)}. {@code Text#getLiteral()} is used nowhere in this class,
 * including in the one place it would seem to help — the atomic-link destination-equality test below reads the
 * literal only to <em>compare</em> it to the link's destination, never to emit it into the masked form.
 */
// Checkstyle's HideUtilityClassConstructor parses source text before Lombok's annotation processor runs,
// so it cannot see the private constructor @NoArgsConstructor generates below; suppressed per the escape
// hatch checkstyle.xml documents for exactly this case (java-coding-style.md, ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class MarkdownMasker {

    /**
     * Masks {@code block}'s content, {@code text[charStart, charEnd)}, by the source ranges its inline descendants
     * occupy.
     *
     * @param block the leaf block whose inline children are walked (a {@code Paragraph}, {@code Heading} or
     *     {@code TableCell}); never null
     * @param text the body text the block's source spans index; never null
     * @param charStart the block's trimmed content start, inclusive
     * @param charEnd the block's trimmed content end, exclusive
     * @return the masked content; never null
     * @throws ua.bookloom.document.mask.MaskInvariantException if the mask-time invariant fails
     */
    static MaskedContent mask(Node block, String text, int charStart, int charEnd) {
        Objects.requireNonNull(block, "block");
        Objects.requireNonNull(text, "text");
        final List<int[]> ranges = new ArrayList<>();
        collectChildren(block, text, ranges);
        final MaskWriter writer = new MaskWriter();
        int cursor = charStart;
        for (final int[] range : ranges) {
            writer.appendCharacterData(text.substring(cursor, range[0]));
            writer.appendAtomic(text.substring(range[0], range[1]));
            cursor = range[1];
        }
        writer.appendCharacterData(text.substring(cursor, charEnd));
        return writer.build();
    }

    private static void collectChildren(Node parent, String text, List<int[]> ranges) {
        for (Node child = parent.getFirstChild(); child != null; child = child.getNext()) {
            collectNode(child, text, ranges);
        }
    }

    /**
     * Classifies one inline node and appends the range(s) it contributes, in document order — {@code Text} and
     * {@code SoftLineBreak} contribute nothing and fall through as residual source text; a hard line break, a
     * paired construct's delimiters, and every other construct's full span are each appended here.
     */
    private static void collectNode(Node node, String text, List<int[]> ranges) {
        if (node instanceof Text || node instanceof SoftLineBreak) {
            return;
        }
        if (node instanceof HardLineBreak hardLineBreak) {
            final int @Nullable [] range = hardLineBreakRange(hardLineBreak, text);
            if (range != null) {
                ranges.add(range);
            }
            return;
        }
        if (isPaired(node, text)) {
            collectPaired(node, text, ranges);
        } else {
            collectAtomic(node, ranges);
        }
    }

    /**
     * A paired construct — {@code Emphasis}, {@code StrongEmphasis}, or a non-atomic {@code Link} — emits its
     * opening delimiter, then its children's ranges, then its closing delimiter, per the requirement <em>Wrap
     * translatable inline content in a paired placeholder group</em> applied to Markdown's punctuation delimiters
     * rather than XML tags.
     */
    private static void collectPaired(Node node, String text, List<int[]> ranges) {
        final Node firstSpanned = MarkdownSpans.firstSpannedChild(node);
        final Node lastSpanned = MarkdownSpans.lastSpannedChild(node);
        if (firstSpanned == null || lastSpanned == null) {
            collectAtomic(node, ranges);
            return;
        }
        ranges.add(new int[] {MarkdownSpans.firstSpanStart(node), MarkdownSpans.firstSpanStart(firstSpanned)});
        collectChildren(node, text, ranges);
        ranges.add(new int[] {MarkdownSpans.lastSpanEnd(lastSpanned), MarkdownSpans.lastSpanEnd(node)});
    }

    /** The catch-all: any construct that is not paired is masked as one atomic token over its full span. */
    private static void collectAtomic(Node node, List<int[]> ranges) {
        if (node.getSourceSpans().isEmpty()) {
            return;
        }
        ranges.add(new int[] {MarkdownSpans.firstSpanStart(node), MarkdownSpans.lastSpanEnd(node)});
    }

    /**
     * A node is paired when it has at least one child and is either an {@code Emphasis}/{@code StrongEmphasis}, or
     * a {@code Link} that is not itself atomic (task 6.2). A childless node always falls through to the atomic
     * catch-all, since there is nothing to wrap a pair around.
     */
    private static boolean isPaired(Node node, String text) {
        if (node.getFirstChild() == null) {
            return false;
        }
        if (node instanceof Emphasis || node instanceof StrongEmphasis) {
            return true;
        }
        return node instanceof Link link && !isAtomicLink(link, text);
    }

    /**
     * A link is atomic when its source substring is delimited by {@code <} and {@code >} (every autolink form), or
     * when its sole child is a {@code Text} whose literal equals the destination. Neither test alone suffices: an
     * email autolink {@code <me@example.com>} has destination {@code mailto:me@example.com}, which the equality
     * test misses, while {@code [https://x.org](https://x.org)} carries no angle brackets at all (task 6.2).
     */
    private static boolean isAtomicLink(Link link, String text) {
        final String source = text.substring(MarkdownSpans.firstSpanStart(link), MarkdownSpans.lastSpanEnd(link));
        if (source.startsWith("<") && source.endsWith(">")) {
            return true;
        }
        // link.getFirstChild() is non-null here (isPaired already checked); "sole child" is tested as
        // "no next sibling" rather than by comparing to getLastChild(), which would be a reference-equality
        // comparison between two Node instances that error-prone flags regardless of intent.
        final Node onlyChild = link.getFirstChild();
        return onlyChild.getNext() == null
                && onlyChild instanceof Text soleText
                && soleText.getLiteral().equals(link.getDestination());
    }

    /**
     * A hard line break's range, or {@code null} when none can be derived. The backslash spelling carries its own
     * source span, used directly. The two-trailing-spaces spelling carries none — the parser folds the spaces into
     * the preceding {@code Text} node's span instead — so its range comes from
     * {@link MarkdownSpans#trailingSpacesRange(Node, String)}, which records why that derivation is what it is and
     * returns {@code null} for a preceding node that carries no span of its own.
     */
    private static int @Nullable [] hardLineBreakRange(HardLineBreak hardLineBreak, String text) {
        if (!hardLineBreak.getSourceSpans().isEmpty()) {
            return new int[] {MarkdownSpans.firstSpanStart(hardLineBreak), MarkdownSpans.lastSpanEnd(hardLineBreak)};
        }
        // Grammatically guaranteed: the two-trailing-spaces spelling can only follow a text run on the same line.
        final Node precedingText = Objects.requireNonNull(hardLineBreak.getPrevious(), "precedingText");
        return MarkdownSpans.trailingSpacesRange(precedingText, text);
    }
}
