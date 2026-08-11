package ua.bookloom.document.md;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.commonmark.ext.gfm.tables.TableCell;
import org.commonmark.node.FencedCodeBlock;
import org.commonmark.node.Heading;
import org.commonmark.node.HtmlBlock;
import org.commonmark.node.IndentedCodeBlock;
import org.commonmark.node.LinkReferenceDefinition;
import org.commonmark.node.ListItem;
import org.commonmark.node.Node;
import org.commonmark.node.Paragraph;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.document.ByteSpanAnchor;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.document.SegmentKind;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.util.hash.HashUtil;

/**
 * Walks a parsed CommonMark tree and emits one segment per translatable <strong>leaf</strong> block.
 *
 * <p><strong>Leaf blocks only.</strong> A container's source span can enclose things that must never be touched: a
 * real list item can hold a paragraph, then an indented fenced code block, then more prose, and replacing the
 * item's extent would destroy the code. So a {@code ListItem}, {@code BulletList}, {@code BlockQuote} or table
 * never becomes a segment — the walk descends through them to the paragraphs and cells inside.
 *
 * <p><strong>Code blocks, raw-HTML blocks and link-reference definitions yield nothing.</strong> Code is excluded
 * rather than marked untranslatable because a segment that exists still costs token budget and still has to be
 * explained to a model (DD-49). A raw-HTML block is one opaque literal node with nothing yet to address its inner
 * prose by; translating inside it needs a second parser nested in the first and is deferred. A link-reference
 * definition is a URL under a label, not prose.
 */
// Checkstyle's HideUtilityClassConstructor parses source text before Lombok's annotation processor runs,
// so it cannot see the private constructor @NoArgsConstructor generates below; suppressed per the escape
// hatch checkstyle.xml documents for exactly this case (java-coding-style.md, ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class MarkdownWalker {

    private static final double INITIAL_CONFIDENCE = 0.0;

    /**
     * Walks {@code root} and emits one segment per translatable leaf block.
     *
     * @param root the parsed body's root node
     * @param text the body text the spans index
     * @param bodyByteOffset the byte offset the body starts at within the whole file, added to every span so an
     *     anchor addresses the original buffer rather than the body
     * @param byteOffsets the character-to-byte offset map over the body text
     * @param unitId the owning unit's id, used as each segment's id prefix
     * @return the unit's segments in document order
     */
    static List<Segment> walk(Node root, String text, int bodyByteOffset, int[] byteOffsets, String unitId) {
        Objects.requireNonNull(root, "root");
        final List<Draft> drafts = new ArrayList<>();
        collect(root, text, drafts);
        return finalizeSegments(drafts, bodyByteOffset, byteOffsets, unitId);
    }

    private static void collect(Node parent, String text, List<Draft> drafts) {
        for (Node child = parent.getFirstChild(); child != null; child = child.getNext()) {
            if (isExcluded(child)) {
                continue;
            }
            final SegmentKind kind = kindOf(child);
            if (kind == null) {
                collect(child, text, drafts);
            } else {
                addDraft(child, text, kind, drafts);
            }
        }
    }

    private static void addDraft(Node block, String text, SegmentKind kind, List<Draft> drafts) {
        final int[] range = MarkdownSpans.contentCharRange(block);
        if (range == null) {
            return;
        }
        final int[] trimmed = MarkdownSpans.trimmed(text, range);
        if (trimmed == null) {
            return;
        }
        drafts.add(new Draft(kind, trimmed[0], trimmed[1], text.substring(trimmed[0], trimmed[1])));
    }

    private static boolean isExcluded(Node node) {
        return node instanceof FencedCodeBlock
                || node instanceof IndentedCodeBlock
                || node instanceof HtmlBlock
                || node instanceof LinkReferenceDefinition;
    }

    /**
     * The kinds a Markdown leaf block maps to, or {@code null} for a container the walk descends through.
     *
     * <p>A paragraph inside a list item is a {@code LIST_ITEM}; a paragraph inside a block quote stays a
     * {@code PARAGRAPH}, because a quotation is prose and a list entry is a distinct kind of block.
     */
    private static @Nullable SegmentKind kindOf(Node node) {
        if (node instanceof Heading) {
            return SegmentKind.HEADING;
        }
        if (node instanceof TableCell) {
            return SegmentKind.TABLE_CELL;
        }
        if (node instanceof Paragraph) {
            return node.getParent() instanceof ListItem ? SegmentKind.LIST_ITEM : SegmentKind.PARAGRAPH;
        }
        return null;
    }

    private static List<Segment> finalizeSegments(
            List<Draft> drafts, int bodyByteOffset, int[] byteOffsets, String unitId) {
        final List<Segment> segments = new ArrayList<>(drafts.size());
        for (int order = 0; order < drafts.size(); order++) {
            segments.add(toSegment(drafts, order, bodyByteOffset, byteOffsets, unitId));
        }
        return segments;
    }

    private static Segment toSegment(
            List<Draft> drafts, int order, int bodyByteOffset, int[] byteOffsets, String unitId) {
        final Draft draft = drafts.get(order);
        final String id = unitId + ":" + order;
        final String prevKey = order > 0 ? unitId + ":" + (order - 1) : null;
        final String nextKey = order < drafts.size() - 1 ? unitId + ":" + (order + 1) : null;
        final ByteSpanAnchor anchor = new ByteSpanAnchor(
                bodyByteOffset + byteOffsets[draft.charStart()], bodyByteOffset + byteOffsets[draft.charEnd()]);
        return new Segment(
                id,
                unitId,
                order,
                draft.kind(),
                draft.sourceInner(),
                draft.sourceInner(),
                Map.of(),
                HashUtil.sha256OfNfcText(draft.sourceInner()),
                prevKey,
                nextKey,
                anchor,
                null,
                SegmentStatus.PENDING,
                INITIAL_CONFIDENCE);
    }

    private record Draft(SegmentKind kind, int charStart, int charEnd, String sourceInner) {}
}
