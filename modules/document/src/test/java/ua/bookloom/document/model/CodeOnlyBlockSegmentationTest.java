package ua.bookloom.document.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.StringReader;
import java.util.List;
import java.util.Objects;
import org.jdom2.Element;
import org.jdom2.Namespace;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.document.Segment;

/**
 * The D11 boundary between "a block whose only translatable content is an inline code span" (XHTML-only, excluded
 * from segmentation) and every other shape a code span can sit in — a code span inside prose is still in the run,
 * and FictionBook's own {@code <code>} prose style is never excluded at all (see {@link BlockSegmentWalker}'s
 * {@code EXCLUDED_TAGS_XHTML}).
 */
class CodeOnlyBlockSegmentationTest {

    private static final String UNIT_HREF = "unit.xhtml";
    private static final Namespace FB2_NAMESPACE = Namespace.getNamespace("http://www.gribuser.ru/xml/fictionbook/2.0");

    private static List<Segment> walkEpub(String bodyHtml) {
        return BlockSegmentWalker.walk(JsoupTreeNode.of(XhtmlTrees.body(bodyHtml)), UNIT_HREF, TreeDialect.XHTML);
    }

    // IF an XHTML block's only translatable content is an inline code span,
    // THEN the system SHALL produce no segment for it and preserve it through the skeleton alone.
    @Test
    void walk_codeOnlyParagraphAmongProse_producesNoSegmentForIt() {
        final List<Segment> segments = walkEpub("<p>Before.</p>\n<p><code>List.of()</code></p>\n<p>After.</p>\n");

        assertThat(segments).hasSize(2);
        assertThat(segments.get(0).sourceInner()).isEqualTo("Before.");
        assertThat(segments.get(1).sourceInner()).isEqualTo("After.");
    }

    // IF an XHTML block's only translatable content is an inline code span,
    // THEN the system SHALL produce no segment for it and preserve it through the skeleton alone.
    @Test
    void walk_codeSpanInsideProse_stillProducesOneSegment() {
        final List<Segment> segments = walkEpub("<p>Call <code>List.of()</code> first.</p>");

        assertThat(segments).hasSize(1);
    }

    // IF an XHTML block's only translatable content is an inline code span,
    // THEN the system SHALL produce no segment for it and preserve it through the skeleton alone.
    @Test
    void walk_codeSpanInsideProse_maskedFormStillMasksTheSpanAsAToken() {
        final List<Segment> segments = walkEpub("<p>Call <code>List.of()</code> first.</p>");

        assertThat(segments.get(0).masked()).isEqualTo("Call ⟦g0⟧ first.");
    }

    // IF an XHTML block's only translatable content is an inline code span,
    // THEN the system SHALL produce no segment for it and preserve it through the skeleton alone.
    // FictionBook uses <code> as an ordinary prose style rather than as a code marker (D11), so this exclusion is
    // XHTML-only: a paragraph written entirely in it is real text a reader reads and must still be a segment.
    @Test
    void walk_fb2ParagraphWrittenEntirelyInCode_stillProducesASegment() {
        final String book = """
                <?xml version="1.0" encoding="UTF-8"?>
                <FictionBook xmlns="http://www.gribuser.ru/xml/fictionbook/2.0">
                  <body>
                    <section>
                      <p>Before.</p>
                      <p><code>List.of()</code></p>
                      <p>After.</p>
                    </section>
                  </body>
                </FictionBook>
                """;
        final Element body = fb2Body(book);

        final List<Segment> segments =
                BlockSegmentWalker.walk(Jdom2TreeNode.of(body), "book.fb2#0", TreeDialect.FICTION_BOOK);

        assertThat(segments).hasSize(3);
        assertThat(segments.get(1).sourceInner()).isEqualTo("List.of()");
    }

    private static Element fb2Body(String book) {
        try {
            return Objects.requireNonNull(SecureXml.builder()
                    .build(new StringReader(book))
                    .getRootElement()
                    .getChild("body", FB2_NAMESPACE));
        } catch (Exception e) {
            throw new IllegalStateException("fixture is not parseable", e);
        }
    }
}
