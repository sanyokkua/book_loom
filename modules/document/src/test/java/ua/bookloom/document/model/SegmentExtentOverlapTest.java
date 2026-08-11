package ua.bookloom.document.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import ua.bookloom.api.document.NodeAnchor;
import ua.bookloom.api.document.Segment;

/**
 * The invariant that keeps the structural rule from double-counting: no two segments' extents overlap.
 *
 * <p>The innermost-element rule is the only thing preventing a wrapper and its child both being segmented, and a
 * regression there would translate the same text twice and corrupt write-back — while every fidelity assertion in
 * the suite stayed green, because nothing would be damaged, only duplicated. The coverage assertion does not catch
 * it either: over-segmentation raises coverage rather than lowering it.
 *
 * <p>Two tree segments overlap exactly when one's node path is a prefix of the other's — a block contained inside
 * another block — or when they share a path <em>and</em> a run index.
 */
class SegmentExtentOverlapTest {

    private static List<NodeAnchor> anchorsOf(String bodyHtml) {
        final List<Segment> segments =
                BlockSegmentWalker.walk(JsoupTreeNode.of(Jsoup.parse(bodyHtml).body()), "unit.xhtml");
        final List<NodeAnchor> anchors = new ArrayList<>(segments.size());
        for (final Segment segment : segments) {
            anchors.add((NodeAnchor) segment.anchor());
        }
        return anchors;
    }

    // Covers: FR-DOC-01 — WHEN a document is parsed, THEN no segment's extent contains another segment's, so the
    // same text is never emitted twice.
    @ParameterizedTest
    @ValueSource(
            strings = {
                "<div class=\"wrap\"><div class=\"paragraph\">Prose.</div></div>",
                "<div>Owner text<p>Nested paragraph.</p></div>",
                "<div><div><div>Deeply wrapped.</div></div></div>",
                "<p>Hello <em>world</em>.</p>",
                "<p><span><i>Wrapped.</i></span></p>",
                "<div>One<br/>Two<br/>Three</div>",
                "<blockquote><p>Quoted.</p><p>More.</p></blockquote>",
                "<table><tr><td>A</td><td>B</td></tr></table>"
            })
    void walk_noTwoSegmentExtentsOverlap(String bodyHtml) {
        assertThat(overlappingPairs(anchorsOf(bodyHtml))).isEmpty();
    }

    /**
     * Every pair whose extents overlap. One anchor's extent contains another's exactly when its node path is a
     * proper prefix of the other's — the wrapper-and-child case — and two anchors on the same block collide only
     * if they name the same run.
     */
    private static List<String> overlappingPairs(List<NodeAnchor> anchors) {
        final List<String> overlapping = new ArrayList<>();
        for (int i = 0; i < anchors.size(); i++) {
            collectOverlapsFrom(anchors, i, overlapping);
        }
        return overlapping;
    }

    private static void collectOverlapsFrom(List<NodeAnchor> anchors, int index, List<String> overlapping) {
        for (int j = index + 1; j < anchors.size(); j++) {
            final NodeAnchor left = anchors.get(index);
            final NodeAnchor right = anchors.get(j);
            if (contains(left, right) || contains(right, left) || sameExtent(left, right)) {
                overlapping.add(left + " overlaps " + right);
            }
        }
    }

    private static boolean sameExtent(NodeAnchor left, NodeAnchor right) {
        return left.nodePath().equals(right.nodePath()) && left.runIndex() == right.runIndex();
    }

    private static boolean contains(NodeAnchor outer, NodeAnchor inner) {
        final List<Integer> outerPath = outer.nodePath();
        final List<Integer> innerPath = inner.nodePath();
        return outerPath.size() < innerPath.size()
                && innerPath.subList(0, outerPath.size()).equals(outerPath);
    }

    // Covers: FR-DOC-01 — a wrapper and the block it wraps never both produce a segment.
    @Test
    void walk_wrapperAndChild_doNotBothProduceASegment() {
        assertThat(anchorsOf("<div class=\"wrap\"><div class=\"paragraph\">Prose.</div></div>"))
                .hasSize(1);
    }
}
