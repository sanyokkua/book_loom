package ua.bookloom.document.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Objects;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ua.bookloom.api.document.NodeAnchor;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.document.SegmentKind;

/**
 * {@code BlockSegmentWalker}'s structural recognition, kind mapping and run splitting, against small ad-hoc jsoup
 * fragments.
 */
class BlockSegmentWalkerTest {

    private static final String UNIT_HREF = "unit.xhtml";

    private static List<Segment> walk(String bodyHtml) {
        return BlockSegmentWalker.walk(JsoupTreeNode.of(Jsoup.parse(bodyHtml).body()), UNIT_HREF);
    }

    // Covers: FR-DOC-01 — a segment is emitted per translatable block element, carrying its kind.
    @Test
    void walk_headingParagraphAndListItem_areDistinguishedByKind() {
        final List<Segment> segments = walk("<h1>Chapter One</h1><p>Prose.</p><ul><li>An item</li></ul>");

        assertThat(segments)
                .extracting(Segment::kind)
                .containsExactly(SegmentKind.HEADING, SegmentKind.PARAGRAPH, SegmentKind.LIST_ITEM);
    }

    // Covers: FR-DOC-01 — a chapter with three paragraphs yields three ordered segments with matching ids.
    @Test
    void walk_threeParagraphs_yieldsOrderedSegmentsWithIdShape() {
        final List<Segment> segments = walk("<p>One.</p><p>Two.</p><p>Three.</p>");

        assertThat(segments).extracting(Segment::id).containsExactly("unit.xhtml:0", "unit.xhtml:1", "unit.xhtml:2");
        assertThat(segments).extracting(Segment::order).containsExactly(0, 1, 2);
    }

    // Covers: FR-DOC-01 — segments know their document-order neighbours, null at the ends.
    @Test
    void walk_threeParagraphs_wireDocumentOrderNeighboursWithNullAtEnds() {
        final List<Segment> segments = walk("<p>One.</p><p>Two.</p><p>Three.</p>");

        assertThat(segments.get(0).prevKey()).isNull();
        assertThat(segments.get(1).prevKey()).isEqualTo(segments.get(0).id());
        assertThat(segments.get(1).nextKey()).isEqualTo(segments.get(2).id());
        assertThat(segments.get(2).nextKey()).isNull();
    }

    // Covers: FR-DOC-01 — WHEN an element owns direct non-whitespace text, THEN it is segmented whatever its tag,
    // so a div carrying prose yields a segment even in a document with no <p> at all.
    @Test
    void walk_divCarryingProse_isSegmented() {
        final List<Segment> segments = walk("<div class=\"paragraph\">Еней був парубок моторний</div>");

        assertThat(segments).hasSize(1);
        assertThat(segments.get(0).sourceInner()).isEqualTo("Еней був парубок моторний");
        assertThat(segments.get(0).kind()).isEqualTo(SegmentKind.PARAGRAPH);
    }

    // Covers: FR-DOC-01 — IF an element owns no direct text, THEN it is descended into rather than segmented, so a
    // wrapper contributes no segment of its own.
    @Test
    void walk_wrapperDiv_isDescendedIntoNotSegmented() {
        final List<Segment> segments = walk("<div class=\"wrap\"><div class=\"paragraph\">Prose.</div></div>");

        assertThat(segments).hasSize(1);
        assertThat(segments.get(0).sourceInner()).isEqualTo("Prose.");
    }

    // Covers: FR-DOC-01 — the tag name decides only the segment's kind, never whether it is a segment.
    @ParameterizedTest
    @CsvSource({
        "'<h2>Chapter One</h2>', HEADING",
        "'<div>Prose.</div>', PARAGRAPH",
        "'<li>An item</li>', LIST_ITEM",
        "'<table><tr><td>A cell</td></tr></table>', TABLE_CELL",
        "'<section>Unrecognised tag prose.</section>', PARAGRAPH"
    })
    void walk_tagName_decidesOnlyTheKind(String html, SegmentKind expected) {
        final List<Segment> segments = walk(html);

        assertThat(segments).singleElement().extracting(Segment::kind).isEqualTo(expected);
    }

    // Covers: DD-49 — IF a block is excluded, THEN it yields no segment even though it owns text.
    @Test
    void walk_codeListing_yieldsNoSegment_andNeighboursSkipOverIt() {
        final List<Segment> segments = walk("<p>Before.</p><pre><code>int x = 1;</code></pre><p>After.</p>");

        assertThat(segments).hasSize(2);
        assertThat(segments).noneMatch(s -> s.sourceInner().contains("int x = 1;"));
        assertThat(segments.get(0).nextKey()).isEqualTo(segments.get(1).id());
        assertThat(segments.get(1).prevKey()).isEqualTo(segments.get(0).id());
    }

    // Covers: DD-49 — a block-level MathML <math> element produces no segment.
    @Test
    void walk_blockLevelMath_yieldsNoSegment() {
        final org.jsoup.nodes.Document doc =
                Jsoup.parse("<body><p>Before.</p><math><mi>x</mi></math><p>After.</p></body>", "", Parser.xmlParser());
        final Element body = Objects.requireNonNull(doc.selectFirst("body"));

        final List<Segment> segments = BlockSegmentWalker.walk(JsoupTreeNode.of(body), UNIT_HREF);

        assertThat(segments).hasSize(2);
        assertThat(segments).noneMatch(s -> s.sourceInner().contains("<mi>"));
    }

    // Covers: EC-IMG-1 — a block whose content is empty once markup is disregarded yields no segment, so an
    // image-only paragraph costs no model call.
    @Test
    void walk_imageOnlyParagraph_yieldsNoSegment() {
        final List<Segment> segments = walk("<p>One.</p><p><img src=\"fig1.png\"/></p><p>Two.</p>");

        assertThat(segments).hasSize(2);
        assertThat(segments).extracting(Segment::sourceInner).containsExactly("One.", "Two.");
    }

    // Covers: EC-IMG-1 — a spacer paragraph holding only a line break yields no segment.
    @Test
    void walk_spacerParagraph_yieldsNoSegment() {
        assertThat(walk("<p><br/></p>")).isEmpty();
    }

    // Covers: FR-DOC-01 — a block mixing text and an image is still one segment, because it owns text.
    @Test
    void walk_paragraphMixingTextAndImage_isStillOneSegment() {
        final List<Segment> segments = walk("<p>See <img src=\"fig1.png\"/> here.</p>");

        assertThat(segments).hasSize(1);
        assertThat(segments.get(0).sourceInner()).contains("See ", "fig1.png", " here.");
    }

    // Covers: FR-DOC-08 — WHERE a segment-bearing block contains line breaks, one segment per maximal run is
    // emitted in document order, and the break elements stay in the skeleton.
    @Test
    void walk_threeRuns_yieldThreeSegmentsWithAscendingRunIndices() {
        final List<Segment> segments = walk("<div>Розділ перший<br/>Еней був парубок<br/>І хлопець хоть куди</div>");

        assertThat(segments)
                .extracting(Segment::sourceInner)
                .containsExactly("Розділ перший", "Еней був парубок", "І хлопець хоть куди");
        assertThat(segments)
                .allSatisfy(s -> assertThat(((NodeAnchor) s.anchor()).nodePath())
                        .isEqualTo(((NodeAnchor) segments.get(0).anchor()).nodePath()));
        assertThat(segments)
                .extracting(s -> ((NodeAnchor) s.anchor()).runIndex())
                .containsExactly(0, 1, 2);
    }

    // Covers: FR-DOC-08 — an empty run between two adjacent line breaks yields no segment, while the runs that do
    // carry text keep the positional index reassembly will resolve.
    @Test
    void walk_emptyRunBetweenAdjacentBreaks_yieldsNoSegment() {
        final List<Segment> segments = walk("<div>Один<br/><br/>Два</div>");

        assertThat(segments).extracting(Segment::sourceInner).containsExactly("Один", "Два");
        assertThat(segments)
                .extracting(s -> ((NodeAnchor) s.anchor()).runIndex())
                .containsExactly(0, 2);
    }

    // Covers: FR-DOC-08 — a block containing no line break has exactly one run, carrying run index zero.
    @Test
    void walk_ordinaryParagraph_carriesRunIndexZero() {
        final List<Segment> segments = walk("<p>Prose with no line break.</p>");

        assertThat(segments).hasSize(1);
        assertThat(((NodeAnchor) segments.get(0).anchor()).runIndex()).isZero();
    }

    // Covers: FR-DOC-01 — no segment carries a kind this change cannot produce, in particular no metadata-unit
    // kind and none of FOOTNOTE, CAPTION or TITLE.
    @Test
    void walk_mixedDocument_emitsOnlyBodyKindsThisChangeProduces() {
        final List<Segment> segments = walk(
                "<h1>H</h1><p>P</p><ul><li>L</li></ul><table><tr><td>C</td></tr></table><figcaption>Cap</figcaption>");

        assertThat(segments)
                .extracting(Segment::kind)
                .doesNotContain(
                        SegmentKind.FOOTNOTE,
                        SegmentKind.CAPTION,
                        SegmentKind.TITLE,
                        SegmentKind.METADATA_TITLE,
                        SegmentKind.METADATA_AUTHOR,
                        SegmentKind.FRONTMATTER_VALUE,
                        SegmentKind.ALT,
                        SegmentKind.NAV_LABEL);
        assertThat(segments)
                .extracting(Segment::kind)
                .containsExactly(
                        SegmentKind.HEADING,
                        SegmentKind.PARAGRAPH,
                        SegmentKind.LIST_ITEM,
                        SegmentKind.TABLE_CELL,
                        SegmentKind.PARAGRAPH);
    }

    // Covers: DD-07 — parsing adds no element to the skeleton and no element gains an attribute absent from the
    // source.
    @Test
    void walk_doesNotMutateTheParsedTree() {
        final Element body =
                Jsoup.parse("<p id=\"p1\" class=\"first\">One.</p><p>Two.</p>").body();
        final int elementsBefore = body.getAllElements().size();

        BlockSegmentWalker.walk(JsoupTreeNode.of(body), UNIT_HREF);

        assertThat(body.getAllElements()).hasSize(elementsBefore);
        final Element pOne = Objects.requireNonNull(body.selectFirst("#p1"));
        assertThat(pOne.attr("class")).isEqualTo("first");
    }

    @Test
    void walk_emptyBody_yieldsNoSegments() {
        assertThat(walk("")).isEmpty();
    }

    @Test
    void walk_containerElementWrappingParagraph_stillEmitsTheNestedParagraph() {
        final List<Segment> segments = walk("<div><section><p>Nested.</p></section></div>");

        assertThat(segments).hasSize(1);
        assertThat(segments.get(0).sourceInner()).isEqualTo("Nested.");
    }

    // Covers: FR-DOC-01 — a paragraph whose prose sits under inline wrappers is still reached, because the walk
    // descends past every element that owns no direct text of its own.
    @Test
    void walk_proseWrappedInSpanAndItalic_isStillSegmented() {
        final List<Segment> segments = walk("<p><span><i>Wrapped prose.</i></span></p>");

        assertThat(segments).hasSize(1);
        assertThat(segments.get(0).sourceInner()).isEqualTo("Wrapped prose.");
    }
}
