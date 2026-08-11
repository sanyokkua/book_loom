package ua.bookloom.document.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Objects;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.document.Segment;

/**
 * Run-scoped write-back — the half of ADR-0025 that the shipped implementation got wrong and that no test in the
 * repository exercised, because every existing fixture paragraph was plain text and the golden round trip performs
 * zero edits.
 */
class SkeletonAnchorsTest {

    private static final String UNIT_HREF = "unit.xhtml";

    /**
     * Parses with pretty-printing off, matching how {@code XhtmlParser} pins a real spine document: with it on,
     * jsoup re-indents on output and the assertion would be about jsoup's formatter rather than about write-back.
     */
    private static Element bodyOf(String html) {
        final org.jsoup.nodes.Document document = Jsoup.parse(html);
        document.outputSettings().prettyPrint(false);
        return document.body();
    }

    private static List<Segment> walk(Element body) {
        return BlockSegmentWalker.walk(JsoupTreeNode.of(body), UNIT_HREF);
    }

    private static Element requireElement(Element body, String selector) {
        return Objects.requireNonNull(body.selectFirst(selector), selector);
    }

    // Covers: FR-DOC-03 — WHEN a segment's target content contains inline markup, THEN it is written back as
    // markup, is not escaped, and does not sit beside the original inline children it replaced.
    @Test
    void writeBack_targetContainingInlineMarkup_replacesTheRunAsMarkup() {
        final Element body = bodyOf("<p>Hello <em>world</em>.</p>");
        final Segment segment = walk(body).get(0);

        SkeletonAnchors.writeBack(JsoupTreeNode.of(body), segment.anchor(), "Привіт <em>світ</em>.");

        final String output = requireElement(body, "p").outerHtml();
        assertThat(output).isEqualTo("<p>Привіт <em>світ</em>.</p>");
        assertThat(output).doesNotContain("&lt;em&gt;");
        assertThat(output).doesNotContain("world");
    }

    // Covers: FR-DOC-03 — WHEN only one run of a multi-run block is written back, THEN the other runs and every
    // line-break element between them are left exactly as they were.
    @Test
    void writeBack_secondRunOnly_leavesTheOtherRunsAndBothBreaksIntact() {
        final Element body = bodyOf("<div>Один<br/>Два<br/>Три</div>");
        final List<Segment> segments = walk(body);

        SkeletonAnchors.writeBack(JsoupTreeNode.of(body), segments.get(1).anchor(), "Two");

        final Element div = requireElement(body, "div");
        assertThat(div.outerHtml()).isEqualTo("<div>Один<br>Two<br>Три</div>");
        assertThat(div.select("br")).hasSize(2);
    }

    // Covers: FR-DOC-03 — WHEN a segment is reassembled, THEN the element it was parsed from keeps every
    // attribute it declared.
    @Test
    void writeBack_doesNotDisturbTheBlockElementsOwnAttributes() {
        final Element body = bodyOf("<p id=\"ch01-p07\" class=\"first\">Text.</p>");
        final Segment segment = walk(body).get(0);

        SkeletonAnchors.writeBack(JsoupTreeNode.of(body), segment.anchor(), "Текст.");

        final Element paragraph = requireElement(body, "p");
        assertThat(paragraph.attr("id")).isEqualTo("ch01-p07");
        assertThat(paragraph.attr("class")).isEqualTo("first");
        assertThat(paragraph.text()).isEqualTo("Текст.");
    }

    // Covers: DD-07 — WHEN an earlier segment receives target text longer than its source, THEN a later segment's
    // anchor still resolves to the element it was parsed from, with no anchor recomputed between the two writes.
    @Test
    void writeBack_longerEarlierTarget_doesNotInvalidateALaterAnchor() {
        final Element body = bodyOf("<p>Chapter One</p><p>Two.</p><p>Three.</p><p>Four.</p><p>Five.</p><p>Six.</p>");
        final List<Segment> segments = walk(body);

        SkeletonAnchors.writeBack(
                JsoupTreeNode.of(body), segments.get(0).anchor(), "Розділ перший, значно довший за оригінал");
        SkeletonAnchors.writeBack(JsoupTreeNode.of(body), segments.get(5).anchor(), "Шість.");

        assertThat(body.select("p").get(0).text()).isEqualTo("Розділ перший, значно довший за оригінал");
        assertThat(body.select("p").get(5).text()).isEqualTo("Шість.");
        assertThat(body.select("p").get(1).text()).isEqualTo("Two.");
    }

    // Covers: FR-DOC-03 — a comment between two block elements survives reassembly, in the same position.
    @Test
    void writeBack_leavesACommentBetweenBlocksInPlace() {
        final Element body = bodyOf("<p>Before.</p><!-- a comment --><p>After.</p>");
        final List<Segment> segments = walk(body);

        SkeletonAnchors.writeBack(JsoupTreeNode.of(body), segments.get(1).anchor(), "Після.");

        assertThat(body.html()).contains("<!-- a comment -->");
        assertThat(body.select("p").get(1).text()).isEqualTo("Після.");
    }
}
