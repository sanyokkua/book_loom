package ua.bookloom.document.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.document.SegmentKind;

/**
 * Inline masking's EPUB half — one segment is walked and masked at a time, driving the shared masker through
 * {@link BlockSegmentWalker} exactly as production does, over trees built by {@link XhtmlTrees#body(String)} so a
 * serialized-markup assertion measures the adapter, not jsoup's pretty-printer (see that helper's Javadoc).
 */
class TreeMaskingEpubTest {

    private static final String UNIT_HREF = "unit.xhtml";

    private static List<Segment> walk(String bodyHtml) {
        return BlockSegmentWalker.walk(JsoupTreeNode.of(XhtmlTrees.body(bodyHtml)), UNIT_HREF, TreeDialect.XHTML);
    }

    private static Segment onlySegment(String bodyHtml) {
        final List<Segment> segments = walk(bodyHtml);
        assertThat(segments).hasSize(1);
        return segments.get(0);
    }

    // --- 3.5a: masked form + map, dense first-appearance numbering ---------------------------------------------

    // WHEN a book is parsed, the system SHALL produce for every segment a masked form and an
    // ordered map from each emitted token to the fragment it replaced.
    @Test
    void mask_emphasisPair_producesTwoTokensInMaskedForm() {
        final Segment segment = onlySegment("<p>He opened the <em>old</em> door.</p>");

        assertThat(segment.masked()).isEqualTo("He opened the ⟦g0⟧old⟦g1⟧ door.");
    }

    // WHEN a book is parsed, the system SHALL produce for every segment a masked form and an
    // ordered map from each emitted token to the fragment it replaced.
    @Test
    void mask_emphasisPair_mapHoldsExactFragments() {
        final Segment segment = onlySegment("<p>He opened the <em>old</em> door.</p>");

        assertThat(segment.placeholders()).isEqualTo(Map.of("g0", "<em>", "g1", "</em>"));
    }

    // WHEN a book is parsed, the system SHALL produce for every segment a masked form and an
    // ordered map from each emitted token to the fragment it replaced.
    @Test
    void mask_noProtectedSpan_masksToItself() {
        final Segment segment = onlySegment("<p>Plain prose with no markup.</p>");

        assertThat(segment.masked()).isEqualTo("Plain prose with no markup.");
    }

    // WHEN a book is parsed, the system SHALL produce for every segment a masked form and an
    // ordered map from each emitted token to the fragment it replaced.
    @Test
    void mask_noProtectedSpan_hasEmptyMap() {
        final Segment segment = onlySegment("<p>Plain prose with no markup.</p>");

        assertThat(segment.placeholders()).isEmpty();
    }

    // WHEN a segment is masked, the system SHALL assign placeholder indices densely from zero
    // in first-appearance order.
    @Test
    void mask_twoEmphasisedWords_yieldsIndicesZeroThroughThree() {
        final Segment segment = onlySegment("<p><b>A</b> and <i>B</i></p>");

        assertThat(segment.masked()).isEqualTo("⟦g0⟧A⟦g1⟧ and ⟦g2⟧B⟦g3⟧");
    }

    // WHEN a segment is masked, the system SHALL assign placeholder indices densely from zero
    // in first-appearance order.
    @Test
    void mask_orderFollowsFirstAppearance_notElementType() {
        final Segment segment = onlySegment("<p><i>x</i><b>y</b>.</p>");

        assertThat(segment.masked()).isEqualTo("⟦g0⟧x⟦g1⟧⟦g2⟧y⟦g3⟧.");
    }

    // --- 3.5b: the token grammar within a walked segment ----------------------------------------------------------

    // WHILE tokens are matched, the system SHALL treat only text of the form ⟦gN⟧ as a token
    // and SHALL key the placeholder map by the bare index form.
    @Test
    void mask_nonDigitIndexText_isNotReadAsAToken() {
        final Segment segment = onlySegment("<p>See note ⟦gX⟧ below.</p>");

        assertThat(segment.masked()).isEqualTo("See note ⟦g0⟧gX⟦g1⟧ below.");
    }

    // WHILE tokens are matched, the system SHALL treat only text of the form ⟦gN⟧ as a token
    // and SHALL key the placeholder map by the bare index form.
    @Test
    void mask_pairedEmphasis_placeholderMapIsKeyedByBareIndexForm() {
        final Segment segment = onlySegment("<p>He opened the <em>old</em> door.</p>");

        assertThat(segment.placeholders()).containsOnlyKeys("g0", "g1");
    }

    // --- 3.6: protected spans are one atomic token, interior never exposed ---------------------------------------

    // WHEN a node inside a segment's content is a protected
    // span, the system SHALL emit exactly one token whose fragment is its complete serialized form and SHALL
    // expose no part of its interior.
    @Test
    void mask_inlineCodeSpan_isOneToken() {
        final Segment segment = onlySegment("<p>Call <code>List.of()</code> first.</p>");

        assertThat(segment.masked()).isEqualTo("Call ⟦g0⟧ first.");
    }

    // WHEN a node inside a segment's content is a protected
    // span, the system SHALL emit exactly one token whose fragment is its complete serialized form and SHALL
    // expose no part of its interior.
    @Test
    void mask_inlineCodeSpan_mapCarriesItsCompleteFragment() {
        final Segment segment = onlySegment("<p>Call <code>List.of()</code> first.</p>");

        assertThat(segment.placeholders()).containsEntry("g0", "<code>List.of()</code>");
    }

    // WHEN a node inside a segment's content is a protected
    // span, the system SHALL emit exactly one token whose fragment is its complete serialized form and SHALL
    // expose no part of its interior.
    @Test
    void mask_inlineMathml_isOneToken() {
        final Segment segment = onlySegment("<p>Let <math><mi>x</mi></math> be positive.</p>");

        assertThat(segment.masked()).isEqualTo("Let ⟦g0⟧ be positive.");
    }

    // WHEN a node inside a segment's content is a protected
    // span, the system SHALL emit exactly one token whose fragment is its complete serialized form and SHALL
    // expose no part of its interior.
    @Test
    void mask_inlineVectorGraphic_ownTextNeverExposed() {
        final Segment segment = onlySegment("<p>See <svg><text>Fig 1</text></svg> above.</p>");

        assertThat(segment.masked()).isEqualTo("See ⟦g0⟧ above.");
    }

    // WHEN a node inside a segment's content is a protected
    // span, the system SHALL emit exactly one token whose fragment is its complete serialized form and SHALL
    // expose no part of its interior.
    @Test
    void mask_codeSpanInsideHeading_isMasked() {
        final Segment segment = onlySegment("<h1>Using <code>Optional</code> well</h1>");

        assertThat(segment.masked()).isEqualTo("Using ⟦g0⟧ well");
    }

    // WHEN a node inside a segment's content is a protected
    // span, the system SHALL emit exactly one token whose fragment is its complete serialized form and SHALL
    // expose no part of its interior.
    @Test
    void mask_codeSpanInsideHeading_segmentKindIsStillHeading() {
        final Segment segment = onlySegment("<h1>Using <code>Optional</code> well</h1>");

        assertThat(segment.kind()).isEqualTo(SegmentKind.HEADING);
    }

    // WHEN a node inside a segment's content is a protected
    // span, the system SHALL emit exactly one token whose fragment is its complete serialized form and SHALL
    // expose no part of its interior.
    @Test
    void mask_image_isOneToken() {
        final Segment segment = onlySegment("<p>Before <img src=\"fig1.png\" alt=\"Figure 1\"/> after</p>");

        assertThat(segment.masked()).isEqualTo("Before ⟦g0⟧ after");
    }

    // WHEN a node inside a segment's content is a protected
    // span, the system SHALL emit exactly one token whose fragment is its complete serialized form and SHALL
    // expose no part of its interior.
    @Test
    void mask_lineBreakNestedInsideInlineMarkup_isOneToken() {
        final Segment segment = onlySegment("<p>x<em>one<br/>two</em>y</p>");

        assertThat(segment.masked()).isEqualTo("x⟦g0⟧one⟦g1⟧two⟦g2⟧y");
    }

    // WHEN a node inside a segment's content is a protected
    // span, the system SHALL emit exactly one token whose fragment is its complete serialized form and SHALL
    // expose no part of its interior.
    @Test
    void mask_xmlComment_mapCarriesItsCompleteFragment() {
        final Segment segment = onlySegment("<p>Text <!-- editor note --> more text</p>");

        assertThat(segment.placeholders()).containsEntry("g0", "<!-- editor note -->");
    }

    // --- 3.7: a non-protected element with children is a paired opening/closing token ----------------------------

    // WHEN an element inside a segment's content has children and is not a
    // protected span, the system SHALL emit a paired opening and closing token with its children masked between
    // them.
    @Test
    void mask_linkTarget_isProtectedWhileVisibleTextStaysTranslatable() {
        final Segment segment = onlySegment("<p>See <a href=\"ch2.xhtml#top\" id=\"x1\">chapter two</a>.</p>");

        assertThat(segment.masked()).isEqualTo("See ⟦g0⟧chapter two⟦g1⟧.");
    }

    // WHEN an element inside a segment's content has children and is not a
    // protected span, the system SHALL emit a paired opening and closing token with its children masked between
    // them.
    @Test
    void mask_linkOpeningToken_carriesTheAnchorsAttributes() {
        final Segment segment = onlySegment("<p>See <a href=\"ch2.xhtml#top\" id=\"x1\">chapter two</a>.</p>");

        assertThat(segment.placeholders()).containsEntry("g0", "<a href=\"ch2.xhtml#top\" id=\"x1\">");
    }

    // WHEN an element inside a segment's content has children and is not a
    // protected span, the system SHALL emit a paired opening and closing token with its children masked between
    // them.
    @Test
    void mask_nestedEmphasis_producesTwoWellFormedPairs() {
        final Segment segment = onlySegment("<p>x <b>bold <i>and italic</i></b></p>");

        assertThat(segment.masked()).isEqualTo("x ⟦g0⟧bold ⟦g1⟧and italic⟦g2⟧⟦g3⟧");
    }

    // WHEN an element inside a segment's content has children and is not a
    // protected span, the system SHALL emit a paired opening and closing token with its children masked between
    // them.
    @Test
    void mask_elementEnclosingOnlyWhitespace_isStillAPair() {
        final Segment segment = onlySegment("<p>a<span> </span>b</p>");

        assertThat(segment.masked()).isEqualTo("a⟦g0⟧ ⟦g1⟧b");
    }

    // --- 3.8: an unrecognized element name is masked identically to a known one ----------------------------------

    // WHEN a node inside a segment's content is not character data, the system SHALL mask it
    // whatever element name it carries.
    @Test
    void mask_unknownEpubElementName_isStillMasked() {
        final Segment segment = onlySegment("<p>a <calibre-inline class=\"x\">b</calibre-inline> c</p>");

        assertThat(segment.masked()).isEqualTo("a ⟦g0⟧b⟦g1⟧ c");
    }

    // --- 3.9: character data that is not itself a protected span stays present and translatable ------------------

    // WHILE a segment is masked, the system SHALL leave every piece of character data that is
    // not itself a protected span present and translatable.
    @Test
    void mask_textAfterClosingTag_staysTranslatable() {
        final Segment segment = onlySegment("<p>He opened the <em>old</em> door at seven.</p>");

        assertThat(segment.masked()).contains(" door at seven.");
    }

    // WHILE a segment is masked, the system SHALL leave every piece of character data that is
    // not itself a protected span present and translatable.
    @Test
    void mask_numeralInRunningProse_isNotMasked() {
        final Segment segment = onlySegment("<p>It was built in 1893.</p>");

        assertThat(segment.masked()).isEqualTo("It was built in 1893.");
    }

    // --- 3.10: a markup-shaped segment's character data is presented decoded -------------------------------------

    // IF a segment's format expresses inline structure as markup, THEN the system SHALL
    // present its character data decoded, with no entity or character-reference syntax.
    @Test
    void mask_escapedAmpersand_reachesTheModelAsAPlainAmpersand() {
        final Segment segment = onlySegment("<p>Smith &amp; Sons</p>");

        assertThat(segment.masked()).isEqualTo("Smith & Sons");
    }

    // IF a segment's format expresses inline structure as markup, THEN the system SHALL
    // present its character data decoded, with no entity or character-reference syntax.
    @Test
    void mask_numericCharacterReference_reachesTheModelAsItsCharacter() {
        final Segment segment = onlySegment("<p>1880&#8212;1893</p>");

        assertThat(segment.masked()).isEqualTo("1880—1893");
    }

    // --- 5.3: a literal placeholder bracket in character data is protected on its own ------------------------------

    // WHEN a node inside a segment's content is a protected
    // span, the system SHALL emit exactly one token whose fragment is its complete serialized form and SHALL
    // expose no part of its interior.
    // Prefixed forms, because jsoup reports tagName() as `m:math` and `svg:svg` for the spelling real EPUB2 and
    // DAISY-derived books use: matching the qualified name lets the walk descend and makes a mathematical
    // identifier — or a figure's drawn caption — translatable prose.
    @Test
    void mask_prefixedInlineMathml_isStillOneToken() {
        final Segment segment =
                onlySegment("<p>Let <m:math xmlns:m=\"http://www.w3.org/1998/Math/MathML\"><m:mi>x</m:mi></m:math>"
                        + " be positive.</p>");

        assertThat(segment.masked()).isEqualTo("Let \u27E6g0\u27E7 be positive.");
    }

    // WHEN a node inside a segment's content is a protected
    // span, the system SHALL emit exactly one token whose fragment is its complete serialized form and SHALL
    // expose no part of its interior.
    @Test
    void mask_prefixedInlineVectorGraphic_ownTextNeverExposed() {
        final Segment segment = onlySegment("<p>See <svg:svg><text>Fig 1</text></svg:svg> above.</p>");

        assertThat(segment.masked()).isEqualTo("See \u27E6g0\u27E7 above.");
        assertThat(segment.masked()).doesNotContain("Fig 1");
    }

    // WHEN a non-translatable block occurs inside a translatable run, the system
    // SHALL mask it as a protected span and restore it identically.
    // A valueless attribute is the case where jsoup's two serializers disagree: attributes().html() renders it
    // collapsed under HTML syntax while outerHtml() renders it expanded under the document's pinned XML syntax,
    // so a fragment composed by slicing one at the other's length cuts in the wrong place.
    @Test
    void mask_nestedListingWithValuelessAttribute_capturesTheWholeElement() {
        final Segment segment = onlySegment("<div>Note: <pre hidden>\n\ncode();</pre> ends it.</div>");

        assertThat(segment.masked()).isEqualTo("Note: \u27E6g0\u27E7 ends it.");
        // The exact fragment, not a startsWith/endsWith shape: a fragment sliced at the wrong offset still starts
        // with `<pre hidden` and still ends with `</pre>`, so a loose assertion here proves nothing.
        assertThat(segment.placeholders().get("g0")).isEqualTo("<pre hidden>\n\ncode();</pre>");
    }

    // WHEN a non-translatable block occurs inside a translatable run, the system
    // SHALL mask it as a protected span and restore it identically.
    // An attribute key that is valid in HTML but not coercible to a valid XML name is dropped by XML-syntax
    // serialization, which made openMarkup() longer than markup() and the old slice throw outright.
    @Test
    void mask_nestedListingWithNonXmlAttributeKey_doesNotThrow() {
        final Segment segment = onlySegment("<div>Note: <pre 2col=\"x\">\n\na</pre> ends it.</div>");

        assertThat(segment.masked()).isEqualTo("Note: \u27E6g0\u27E7 ends it.");
        assertThat(segment.placeholders()).containsOnlyKeys("g0");
    }
}
