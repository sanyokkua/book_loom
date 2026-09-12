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
 * A non-translatable block a book has nested inside a translatable one — the requirement that widens masking from
 * standalone {@code <pre>}/block {@code <math>} (excluded from segmentation entirely) to the shape a book only
 * reaches once its surrounding block owns text of its own. Every EPUB fixture here uses a {@code <div>}, not a
 * {@code <p>}: jsoup's HTML parser closes an open paragraph at a {@code <pre>} start tag, so a {@code <p>} shape
 * produces a sibling instead of a nested element and would assert nothing (see {@link XhtmlTrees}'s own warning).
 */
class NestedProtectedBlockMaskingTest {

    private static final String UNIT_HREF = "unit.xhtml";
    /**
     * An FB2 document whose text-owning block nests a listing beginning with two line feeds.
     *
     * <p>Deliberately not a text block: Java's incidental-whitespace stripping would collapse the two blank lines
     * this fixture's whole point is to preserve exactly (mirrors {@code EpubFixtures#preTwoLeadingLineFeeds}).
     */
    @SuppressWarnings("StringConcatToTextBlock")
    private static final String NESTED_PRE_BOOK = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
            + "<FictionBook xmlns=\"http://www.gribuser.ru/xml/fictionbook/2.0\">\n"
            + "  <body>\n"
            + "    <section>\n"
            + "      <p>Note: <pre>\n\ncode();</pre> ends it.</p>\n"
            + "    </section>\n"
            + "  </body>\n"
            + "</FictionBook>\n";

    private static final Namespace FB2_NAMESPACE = Namespace.getNamespace("http://www.gribuser.ru/xml/fictionbook/2.0");

    private static Segment onlyEpubSegment(String bodyHtml) {
        final List<Segment> segments =
                BlockSegmentWalker.walk(JsoupTreeNode.of(XhtmlTrees.body(bodyHtml)), UNIT_HREF, TreeDialect.XHTML);
        assertThat(segments).hasSize(1);
        return segments.get(0);
    }

    // WHEN a non-translatable block occurs inside a translatable run, the system
    // SHALL mask it as a protected span and restore it identically.
    @Test
    void mask_preNestedInTextOwningDiv_isOneToken() {
        final Segment segment = onlyEpubSegment("<div>Note: <pre>code();</pre> ends it.</div>");

        assertThat(segment.masked()).isEqualTo("Note: ⟦g0⟧ ends it.");
    }

    // WHEN a non-translatable block occurs inside a translatable run, the system
    // SHALL mask it as a protected span and restore it identically.
    @Test
    void mask_preNestedInTextOwningDiv_mapCarriesTheListingsText() {
        final Segment segment = onlyEpubSegment("<div>Note: <pre>code();</pre> ends it.</div>");

        assertThat(segment.placeholders()).containsEntry("g0", "<pre>code();</pre>");
    }

    // WHEN a non-translatable block occurs inside a translatable run, the system
    // SHALL mask it as a protected span and restore it identically.
    @Test
    void mask_blockLevelMathNestedInParagraph_isOneToken() {
        final Segment segment = onlyEpubSegment("<p>Given <math display=\"block\"><mi>x</mi></math> we conclude.</p>");

        assertThat(segment.masked()).isEqualTo("Given ⟦g0⟧ we conclude.");
    }

    // WHEN a non-translatable block occurs inside a translatable run, the system
    // SHALL mask it as a protected span and restore it identically.
    // The exact fragment, not a startsWith/endsWith shape: jsoup's HTML parser discards one line feed immediately
    // after a <pre> start tag on every parse, and the captured fragment reinserts one so the pair nets back to the
    // two the source wrote — a fragment sliced at the wrong offset would still start with `<pre` and end with
    // `</pre>`, so a loose assertion here proves nothing (this is how a real defect survived its first test).
    @Test
    void mask_preNestedInDivWithTwoLeadingLineFeeds_fragmentIsExactlyTheSourcesTwoLineFeeds() {
        final Segment segment = onlyEpubSegment("<div>Note: <pre>\n\ncode();</pre> ends it.</div>");

        assertThat(segment.masked()).isEqualTo("Note: ⟦g0⟧ ends it.");
        assertThat(segment.placeholders().get("g0")).isEqualTo("<pre>\n\ncode();</pre>");
    }

    // WHEN a non-translatable block occurs inside a translatable run, the system
    // SHALL mask it as a protected span and restore it identically.
    // FB2-only: JDOM2's XML parser discards nothing, so the EPUB reinsertion rule must not fire here — applying it
    // would add a third line feed on every cycle instead of preserving the two the source wrote.
    // Counted rather than asserted as one exact literal: measured, JDOM2's default raw Format re-serializes every
    // embedded '\n' in text content as the platform line separator (here "\r\n"), so the captured fragment's bytes
    // are not identical to the source even though its line-feed count is. A related, benign gap: JDOM2 stamps the
    // in-scope default namespace onto a serialized subtree, so this fragment reads `<pre xmlns="…">` where the
    // source wrote `<pre>` (see the assertion below). The restore is still canonical-equal — the namespace is
    // absorbed rather than surfaced as a defect — proven by
    // GoldenComparisonMetaTest#maskRestoreFb2Comparison_inlineNamespaceStampingOnly_isAbsorbed.
    @Test
    void mask_fb2NestedPreWithTwoLeadingLineFeeds_doesNotGainALineFeed() {
        final List<Segment> segments = BlockSegmentWalker.walk(
                Jdom2TreeNode.of(fb2Body(NESTED_PRE_BOOK)), "book.fb2#0", TreeDialect.FICTION_BOOK);

        assertThat(segments).hasSize(1);
        assertThat(segments.get(0).masked()).isEqualTo("Note: ⟦g0⟧ ends it.");
        // The exact fragment, not a shape plus a line-feed count: before the adapter pinned LineSeparator.NL the
        // count was already right — JDOM2 emitted \r\n for each of the two — so only comparing the fragment itself
        // distinguishes "kept the source's line feeds" from "kept the source's number of line breaks".
        assertThat(segments.get(0).placeholders().get("g0"))
                .isEqualTo("<pre xmlns=\"http://www.gribuser.ru/xml/fictionbook/2.0\">\n\ncode();</pre>");
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

    // WHEN a book is parsed, the system SHALL produce for every segment a masked form and an
    // ordered map from each emitted token to the exact source fragment it replaced.
    // "Exact" is what this pins. JDOM2's Format.getRawFormat() does not mean "the bytes as they were read": its
    // line separator defaults to \r\n, so before the adapter pinned LineSeparator.NL an FB2 fragment holding one
    // line feed came back carrying two characters where the source had one — and the count-of-line-feeds assertion
    // above would still have passed, which is why this one compares the fragment itself.
    @Test
    void mask_fb2FragmentSpanningALineBreak_carriesTheSourcesOwnLineFeedsNotCarriageReturns() {
        final List<Segment> segments = BlockSegmentWalker.walk(
                Jdom2TreeNode.of(fb2Body(NESTED_PRE_BOOK)), "book.fb2#0", TreeDialect.FICTION_BOOK);

        assertThat(segments.get(0).placeholders().get("g0")).doesNotContain("\r");
        assertThat(segments.get(0).sourceInner()).doesNotContain("\r");
    }
}
