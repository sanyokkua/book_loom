package ua.bookloom.api.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

    // Covers: FR-DOC-01
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

    @Test
    void masked_shipsEqualToSourceInner_withNoPlaceholders() {
        final Segment segment = segment("unit:0", "unit", 0, null, null);

        assertThat(segment.masked()).isEqualTo(segment.sourceInner());
        assertThat(segment.placeholders()).isEmpty();
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
