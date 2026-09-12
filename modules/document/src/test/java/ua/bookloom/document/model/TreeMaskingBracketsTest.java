package ua.bookloom.document.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.document.BookFormat;
import ua.bookloom.api.document.Segment;
import ua.bookloom.document.mask.RestoredContent;
import ua.bookloom.document.mask.Unmasker;

/**
 * The literal-bracket protected span, and the mask-time invariant that depends on it.
 *
 * <p>Split out of {@code TreeMaskingEpubTest} because these two obligations are one mechanism seen from both
 * ends: a book that prints U+27E6 or U+27E7 must never have it read as part of a placeholder, and the invariant
 * that proves so must scan the masked form alone — a check that also scanned the mapped fragments would reject a
 * legitimate book whose code span prints the literal text of a token.
 */
class TreeMaskingBracketsTest {

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

    // IF a segment's character data contains ⟦ or ⟧, THEN the system
    // SHALL replace each occurrence with its own placeholder token and restore the original character exactly.
    @Test
    void mask_bracketPairInProse_becomesTwoTokens() {
        final Segment segment = onlySegment("<p>He wrote ⟦x⟧ on the board.</p>");

        assertThat(segment.masked()).isEqualTo("He wrote ⟦g0⟧x⟦g1⟧ on the board.");
    }

    // IF a segment's character data contains ⟦ or ⟧, THEN the system
    // SHALL replace each occurrence with its own placeholder token and restore the original character exactly.
    @Test
    void mask_bracketPairInProse_eachBracketMappedToItself() {
        final Segment segment = onlySegment("<p>He wrote ⟦x⟧ on the board.</p>");

        assertThat(segment.placeholders()).isEqualTo(Map.of("g0", "⟦", "g1", "⟧"));
    }

    // IF a segment's character data contains ⟦ or ⟧, THEN the system
    // SHALL replace each occurrence with its own placeholder token and restore the original character exactly.
    @Test
    void mask_loneOpeningBracket_isProtectedOnItsOwn() {
        final Segment segment = onlySegment("<p>The symbol ⟦ is rare.</p>");

        assertThat(segment.masked()).isEqualTo("The symbol ⟦g0⟧ is rare.");
    }

    // IF a segment's character data contains ⟦ or ⟧, THEN the system
    // SHALL replace each occurrence with its own placeholder token and restore the original character exactly.
    @Test
    void mask_loneClosingBracket_isProtectedOnItsOwn() {
        final Segment segment = onlySegment("<p>The symbol ⟧ is rare.</p>");

        assertThat(segment.masked()).isEqualTo("The symbol ⟦g0⟧ is rare.");
    }

    // IF a segment's character data contains ⟦ or ⟧, THEN the system
    // SHALL replace each occurrence with its own placeholder token and restore the original character exactly.
    @Test
    void mask_bracketsInsideCodeSpan_needNoSeparateProtection() {
        final Segment segment = onlySegment("<p>Type <code>⟦g0⟧</code> exactly.</p>");

        assertThat(segment.masked()).isEqualTo("Type ⟦g0⟧ exactly.");
    }

    // IF a segment's character data contains ⟦ or ⟧, THEN the system
    // SHALL replace each occurrence with its own placeholder token and restore the original character exactly.
    @Test
    void mask_codeSpanBrackets_stayInsideItsFragment() {
        final Segment segment = onlySegment("<p>Type <code>⟦g0⟧</code> exactly.</p>");

        assertThat(segment.placeholders()).containsEntry("g0", "<code>⟦g0⟧</code>");
    }

    // IF a segment's character data contains ⟦ or ⟧, THEN the system
    // SHALL replace each occurrence with its own placeholder token and restore the original character exactly.
    @Test
    void mask_bracketInsideAttributeValue_needsNoProtection() {
        final Segment segment = onlySegment("<p>a <span title=\"⟦x⟧\">b</span> c</p>");

        assertThat(segment.masked()).isEqualTo("a ⟦g0⟧b⟦g1⟧ c");
    }

    // IF a segment's character data contains ⟦ or ⟧, THEN the system
    // SHALL replace each occurrence with its own placeholder token and restore the original character exactly.
    @Test
    void mask_bracketedNonDigitIndexText_isNotReadAsAnExistingToken() {
        final Segment segment = onlySegment("<p>See note ⟦gX⟧ below.</p>");

        assertThat(segment.masked()).isEqualTo("See note ⟦g0⟧gX⟦g1⟧ below.");
    }

    // IF a segment's character data contains ⟦ or ⟧, THEN the system
    // SHALL replace each occurrence with its own placeholder token and restore the original character exactly.
    @Test
    void unmask_bracketPairInProse_survivesMaskThenRestoreCycleExactly() {
        final Segment segment = onlySegment("<p>He wrote ⟦x⟧ on the board.</p>");

        final RestoredContent restored = Unmasker.restore(BookFormat.EPUB, segment, segment.masked());

        assertThat(restored.text()).isEqualTo("He wrote ⟦x⟧ on the board.");
    }

    // --- 5.4: the mask-time invariant is a bijection over exactly the masked form's tokens -------------------------

    // WHEN a segment is masked, the system SHALL assert every emitted token is unique and the
    // placeholder map is a bijection over the tokens in the masked form.
    @Test
    void mask_boldAndCodeSpan_mapCoversExactlyItsTokens() {
        final Segment segment = onlySegment("<p><b>A</b> and <code>x</code></p>");

        assertThat(segment.masked()).isEqualTo("⟦g0⟧A⟦g1⟧ and ⟦g2⟧");
        assertThat(segment.placeholders()).containsOnlyKeys("g0", "g1", "g2");
    }

    // WHEN a segment is masked, the system SHALL assert every emitted token is unique and the
    // placeholder map is a bijection over the tokens in the masked form.
    @Test
    void mask_fragmentContainingATokenSpelling_doesNotBreakTheInvariant() {
        final Segment segment = onlySegment("<p>Type <code>⟦g0⟧</code> exactly.</p>");

        assertThat(segment.masked()).isEqualTo("Type ⟦g0⟧ exactly.");
    }
}
