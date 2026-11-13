package ua.bookloom.pipeline.memory;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.document.ByteSpanAnchor;
import ua.bookloom.api.document.PlaceholderPair;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.document.SegmentKind;
import ua.bookloom.api.document.SegmentStatus;

/**
 * A drop cap is shown to the model as one whole word and its marks are left out of the marks the reply must keep
 * ({@code specs/translation-pipeline/spec.md} "Show a drop cap to the model as one whole word"). Pinning test: the
 * fold is unchanged by the one repair.
 */
class DropCapsTest {

    private static final String MASKED = "⟦g0⟧“A⟦g1⟧bove all,” said his master.";

    @Test
    void fold_dropCapWord_shownWhole() {
        final Segment segment = segment(MASKED);

        final List<String> folded = DropCaps.tokensOf(segment);

        assertThat(folded).containsExactly("⟦g0⟧", "⟦g1⟧");
        assertThat(DropCaps.without(MASKED, folded)).isEqualTo("“Above all,” said his master.");
    }

    @Test
    void fold_pairAroundAWholeWord_isNoDropCap() {
        final Segment segment = segment("He opened the ⟦g0⟧old⟦g1⟧ door.");

        assertThat(DropCaps.tokensOf(segment)).isEmpty();
    }

    @Test
    void onlyFoldedMissing_replyWithoutTheDropCapMarks_isTrue() {
        assertThat(DropCaps.onlyFoldedMissing(MASKED, "«Понад усе», — сказав його господар.", List.of("⟦g0⟧", "⟦g1⟧")))
                .isTrue();
    }

    private static Segment segment(final String masked) {
        return new Segment(
                "ch01.xhtml:3",
                "ch01.xhtml",
                3,
                SegmentKind.PARAGRAPH,
                masked,
                masked,
                Map.of("g0", "<span class=\"calibre8\">", "g1", "</span>"),
                "hash",
                null,
                null,
                new ByteSpanAnchor(0, masked.length()),
                null,
                SegmentStatus.PENDING,
                0.0,
                null,
                List.of(new PlaceholderPair("⟦g0⟧", "⟦g1⟧", null)),
                List.of());
    }
}
