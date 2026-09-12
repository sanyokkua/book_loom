package ua.bookloom.document.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.StringReader;
import java.util.List;
import java.util.Objects;
import org.jdom2.Element;
import org.jdom2.Namespace;
import org.jdom2.output.Format;
import org.jdom2.output.XMLOutputter;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.document.SegmentKind;

/**
 * The JDOM2 side of the shared walker. These assertions exist here rather than only in the FB2 group because the
 * adapter is what makes ADR-0027's rule single-sourced: if the abstraction did not fit an XML tree, the rule would
 * quietly be implemented twice and drift, which is the failure mode ADR-0027 was written to remove.
 */
class Jdom2TreeNodeTest {

    private static final Namespace FB2 = Namespace.getNamespace("http://www.gribuser.ru/xml/fictionbook/2.0");

    private static final String BOOK = """
            <?xml version="1.0" encoding="UTF-8"?>
            <FictionBook xmlns="http://www.gribuser.ru/xml/fictionbook/2.0"
                         xmlns:l="http://www.w3.org/1999/xlink">
              <body>
                <section>
                  <title><p>Розділ перший</p></title>
                  <subtitle>Підзаголовок</subtitle>
                  <p>Проза.</p>
                  <empty-line/>
                  <poem><stanza><v>Рядок один</v><v>Рядок два</v></stanza></poem>
                  <p>Пролог<br/>Хвіст комети</p>
                  <cite><p>Цитата.</p></cite>
                  <table><tr><td>A</td><td>B</td></tr></table>
                  <!-- scene break -->
                  <p>Останній <emphasis>абзац</emphasis>.</p>
                </section>
              </body>
            </FictionBook>
            """;

    private static Element body() {
        try {
            return Objects.requireNonNull(SecureXml.builder()
                    .build(new StringReader(BOOK))
                    .getRootElement()
                    .getChild("body", FB2));
        } catch (Exception e) {
            throw new IllegalStateException("fixture is not parseable", e);
        }
    }

    private static List<Segment> walk(Element body) {
        return BlockSegmentWalker.walk(Jdom2TreeNode.of(body), "book.fb2#0", TreeDialect.FICTION_BOOK);
    }

    // WHEN an FB2 body is parsed, THEN each block element yields a segment carrying the
    // kind matching that element, with a section title's paragraph and a subtitle both HEADING.
    @Test
    void walk_fb2Body_mapsEachBlockToItsKind() {
        final List<Segment> segments = walk(body());

        assertThat(kindOf(segments, "Розділ перший")).isEqualTo(SegmentKind.HEADING);
        assertThat(kindOf(segments, "Підзаголовок")).isEqualTo(SegmentKind.HEADING);
        assertThat(kindOf(segments, "Проза.")).isEqualTo(SegmentKind.PARAGRAPH);
        assertThat(kindOf(segments, "Рядок один")).isEqualTo(SegmentKind.VERSE_LINE);
        assertThat(kindOf(segments, "A")).isEqualTo(SegmentKind.TABLE_CELL);
    }

    // a poem's verse lines are each their own segment rather than one stanza-sized segment.
    @Test
    void walk_poemStanza_yieldsOneSegmentPerVerseLine() {
        final List<Segment> segments = walk(body());

        assertThat(segments)
                .filteredOn(s -> s.kind() == SegmentKind.VERSE_LINE)
                .extracting(Segment::sourceInner)
                .containsExactly("Рядок один", "Рядок два");
    }

    // a container element such as <cite> is descended into to reach the blocks inside it.
    @Test
    void walk_citeContainer_isDescendedIntoToReachItsParagraph() {
        assertThat(walk(body())).extracting(Segment::sourceInner).contains("Цитата.");
    }

    // a vertical-space element carries no words and yields no segment.
    @Test
    void walk_emptyLineElement_yieldsNoSegment() {
        assertThat(walk(body())).noneMatch(s -> s.sourceInner().contains("empty-line"));
    }

    // text carried after a line break inside an FB2 paragraph is not lost; each run becomes
    // its own segment.
    @Test
    void walk_lineBreakInsideParagraph_splitsIntoTwoSegments() {
        assertThat(walk(body())).extracting(Segment::sourceInner).contains("Пролог", "Хвіст комети");
    }

    // a translation containing inline markup is written back into the FB2 tree as markup, in
    // the book's own namespace, rather than escaped into text.
    @Test
    void writeBack_inlineMarkup_landsAsElementsInTheBooksNamespace() {
        final Element body = body();
        final Segment last = walk(body).stream()
                .filter(s -> s.sourceInner().contains("Останній"))
                .findFirst()
                .orElseThrow();

        SkeletonAnchors.writeBack(Jdom2TreeNode.of(body), last.anchor(), "Пере<emphasis>клад</emphasis>.");

        final String xml = new XMLOutputter(Format.getRawFormat()).outputString(body);
        assertThat(xml).contains("<emphasis>клад</emphasis>");
        assertThat(xml).doesNotContain("абзац");
        assertThat(xml).doesNotContain("&lt;emphasis&gt;");
    }

    // a comment between block elements survives a write-back into a neighbouring block.
    @Test
    void writeBack_leavesTheCommentBetweenBlocksInPlace() {
        final Element body = body();
        final Segment prose = walk(body).stream()
                .filter(s -> "Проза.".equals(s.sourceInner()))
                .findFirst()
                .orElseThrow();

        SkeletonAnchors.writeBack(Jdom2TreeNode.of(body), prose.anchor(), "Проза перекладена.");

        final String xml = new XMLOutputter(Format.getRawFormat()).outputString(body);
        assertThat(xml).contains("<!-- scene break -->");
        assertThat(xml).contains("Проза перекладена.");
    }

    @Test
    void writeBack_malformedTargetMarkup_isRejectedRatherThanWrittenAsText() {
        final Element body = body();
        final Segment prose = walk(body).stream()
                .filter(s -> "Проза.".equals(s.sourceInner()))
                .findFirst()
                .orElseThrow();

        assertThatThrownBy(
                        () -> SkeletonAnchors.writeBack(Jdom2TreeNode.of(body), prose.anchor(), "unclosed <emphasis>"))
                .isInstanceOf(MalformedFragmentException.class);
    }

    @Test
    void ownText_nonTextNode_reportsNoText() {
        assertThat(Jdom2TreeNode.of(new Element("p", FB2)).ownText()).isEmpty();
    }

    @Test
    void childNodes_nonElementNode_isEmpty() {
        assertThat(Jdom2TreeNode.of(new org.jdom2.Text("x")).childNodes()).isEmpty();
    }

    @Test
    void replaceChildren_onANonElementNode_isRejected() {
        final TreeNode text = Jdom2TreeNode.of(new org.jdom2.Text("x"));

        assertThatThrownBy(() -> text.replaceChildren(0, 0, "y")).isInstanceOf(IllegalStateException.class);
    }

    private static SegmentKind kindOf(List<Segment> segments, String sourceInner) {
        return segments.stream()
                .filter(s -> sourceInner.equals(s.sourceInner()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no segment with sourceInner " + sourceInner))
                .kind();
    }
}
