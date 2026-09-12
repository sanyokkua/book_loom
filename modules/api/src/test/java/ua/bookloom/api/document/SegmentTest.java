package ua.bookloom.api.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * {@code Segment}'s shape and construction-time invariants.
 */
class SegmentTest {

    private static final SkeletonAnchor ANCHOR = new NodeAnchor(List.of(0), 0);

    private static Segment segment(
            String id, String unit, int order, @Nullable String prevKey, @Nullable String nextKey) {
        return new Segment(
                id,
                unit,
                order,
                SegmentKind.PARAGRAPH,
                "Text.",
                "Text.",
                Map.of(),
                "hash",
                prevKey,
                nextKey,
                ANCHOR,
                null,
                SegmentStatus.PENDING,
                0.0);
    }

    // WHEN a unit yields segments unit:0, unit:1, unit:2, THEN unit:1 reports prevKey=unit:0/nextKey=unit:2, and
    // unit:0/unit:2 report null at their respective end.
    @Test
    void neighbours_documentOrderSiblings_reportPrevAndNextWithNullAtEnds() {
        final Segment first = segment("unit:0", "unit", 0, null, "unit:1");
        final Segment second = segment("unit:1", "unit", 1, "unit:0", "unit:2");
        final Segment third = segment("unit:2", "unit", 2, "unit:1", null);

        assertThat(second.prevKey()).isEqualTo("unit:0");
        assertThat(second.nextKey()).isEqualTo("unit:2");
        assertThat(first.prevKey()).isNull();
        assertThat(third.nextKey()).isNull();
    }

    // WHEN a segment is masked, the system SHALL carry a masked form distinct from its source
    // content, together with the ordered map from each emitted token to the fragment it replaced.
    // Replaces an assertion that `masked` always equalled `sourceInner`, which this change makes false — and which
    // proved nothing even while it was true, because it read back values the test's own helper had just written.
    @Test
    void masked_maskedSegment_carriesItsTokensAndFragmentsWithoutTouchingSourceInner() {
        // Built by insertion rather than Map.of, whose iteration order is unspecified — first-appearance order is
        // the property being asserted, so the fixture must not depend on a map that does not promise one.
        final Map<String, String> fragments = new LinkedHashMap<>();
        fragments.put("g0", "<em>");
        fragments.put("g1", "</em>");
        final Segment segment = new Segment(
                "unit:0",
                "unit",
                0,
                SegmentKind.PARAGRAPH,
                "He opened the <em>old</em> door.",
                "He opened the ⟦g0⟧old⟦g1⟧ door.",
                fragments,
                "hash",
                null,
                null,
                ANCHOR,
                null,
                SegmentStatus.PENDING,
                0.0);

        assertThat(segment.sourceInner()).isEqualTo("He opened the <em>old</em> door.");
        assertThat(segment.masked()).isEqualTo("He opened the ⟦g0⟧old⟦g1⟧ door.");
        assertThat(segment.placeholders()).containsExactly(entry("g0", "<em>"), entry("g1", "</em>"));
    }

    @Test
    void placeholders_returned_isUnmodifiable() {
        final Segment segment = segment("unit:0", "unit", 0, null, null);

        assertThatThrownBy(() -> segment.placeholders().put("k", "v"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @ParameterizedTest
    @ValueSource(doubles = {-0.1, 1.1})
    void constructor_confidenceOutsideUnitRange_isRejected(double confidence) {
        assertThatThrownBy(() -> new Segment(
                        "unit:0",
                        "unit",
                        0,
                        SegmentKind.PARAGRAPH,
                        "Text.",
                        "Text.",
                        Map.of(),
                        "hash",
                        null,
                        null,
                        ANCHOR,
                        null,
                        SegmentStatus.PENDING,
                        confidence))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // Suppressed deliberately: NullAway forbids this call statically, which is exactly why the runtime guard must
    // still exist and be proven for a caller reaching this record without that analysis.
    @SuppressWarnings("NullAway")
    @Test
    void constructor_nullId_isRejected() {
        assertThatNullPointerException()
                .isThrownBy(() -> new Segment(
                        null,
                        "unit",
                        0,
                        SegmentKind.PARAGRAPH,
                        "Text.",
                        "Text.",
                        Map.of(),
                        "hash",
                        null,
                        null,
                        ANCHOR,
                        null,
                        SegmentStatus.PENDING,
                        0.0));
    }

    @SuppressWarnings("NullAway")
    @Test
    void constructor_nullKind_isRejected() {
        assertThatNullPointerException()
                .isThrownBy(() -> new Segment(
                        "unit:0",
                        "unit",
                        0,
                        null,
                        "Text.",
                        "Text.",
                        Map.of(),
                        "hash",
                        null,
                        null,
                        ANCHOR,
                        null,
                        SegmentStatus.PENDING,
                        0.0));
    }
}
