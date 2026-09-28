package ua.bookloom.document.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.document.PlaceholderPair;
import ua.bookloom.api.document.Segment;

/** The pairs and line-break tokens a masked EPUB segment records (task 5.8). */
class TreeMaskingPairsTest {

    private static Segment onlySegment(String bodyHtml) {
        final List<Segment> segments =
                BlockSegmentWalker.walk(JsoupTreeNode.of(XhtmlTrees.body(bodyHtml)), "unit.xhtml", TreeDialect.XHTML);
        assertThat(segments).hasSize(1);
        return segments.get(0);
    }

    // WHEN an inline element wraps text, THEN its two tokens are recorded as one pair with no language.
    @Test
    void mask_emphasis_recordsOnePairWithNoLanguage() {
        final Segment segment = onlySegment("<p>Hello <em>world</em>.</p>");

        assertThat(segment.pairs()).containsExactly(new PlaceholderPair("⟦g0⟧", "⟦g1⟧", null));
        assertThat(segment.lineBreakTokens()).isEmpty();
    }

    // WHEN an inline element declares xml:lang, THEN the pair carries that language as written.
    @Test
    void mask_spanDeclaringXmlLang_recordsThePairsLanguage() {
        final Segment segment = onlySegment("<p>He said <span xml:lang=\"fr\">bonjour</span> and left.</p>");

        assertThat(segment.masked()).isEqualTo("He said ⟦g0⟧bonjour⟦g1⟧ and left.");
        assertThat(segment.pairs()).containsExactly(new PlaceholderPair("⟦g0⟧", "⟦g1⟧", "fr"));
    }

    // WHEN a line break sits directly in the block, THEN it splits the block into runs and no pair is recorded.
    @Test
    void mask_directLineBreak_recordsNoPair() {
        final List<Segment> segments = BlockSegmentWalker.walk(
                JsoupTreeNode.of(XhtmlTrees.body("<p>a<br/>b</p>")), "unit.xhtml", TreeDialect.XHTML);

        assertThat(segments)
                .extracting(Segment::pairs)
                .allSatisfy(pairs -> assertThat(pairs).isEmpty());
    }

    // WHEN a line break sits inside an inline element, THEN it is a line-break token and the element's pair spans it.
    @Test
    void mask_lineBreakInsideEmphasis_recordsThePairAndTheLineBreakToken() {
        final Segment segment = onlySegment("<p>a<em>x<br/>y</em>b</p>");

        assertThat(segment.masked()).isEqualTo("a⟦g0⟧x⟦g1⟧y⟦g2⟧b");
        assertThat(segment.pairs()).containsExactly(new PlaceholderPair("⟦g0⟧", "⟦g2⟧", null));
        assertThat(segment.lineBreakTokens()).containsExactly("⟦g1⟧");
    }
}
