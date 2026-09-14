package ua.bookloom.api.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * {@code Unit}'s construction-time invariants.
 */
class UnitTest {

    private static final SkeletonHandle SKELETON = new SkeletonHandle("skeleton-1");

    @Test
    void segments_returned_isUnmodifiable() {
        final Unit unit =
                new Unit("OEBPS/c01.xhtml", 0, "OEBPS/c01.xhtml", "application/xhtml+xml", SKELETON, List.of());

        assertThatThrownBy(() -> unit.segments().add(null)).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void withSegments_newList_changesOnlySegments() {
        final Segment originalSegment = new Segment(
                "unit:0",
                "unit",
                0,
                SegmentKind.PARAGRAPH,
                "Original.",
                "Original.",
                java.util.Map.of(),
                "hash",
                null,
                null,
                new NodeAnchor(List.of(0), 0),
                null,
                SegmentStatus.PENDING,
                0.0);
        final Unit original = new Unit("unit", 0, "unit.md", "text/markdown", SKELETON, List.of(originalSegment));

        final Segment replacement = originalSegment.withDecision(SegmentStatus.ACCEPTED, "Translated.");
        final Unit updated = original.withSegments(List.of(replacement));

        assertThat(updated.id()).isEqualTo(original.id());
        assertThat(updated.order()).isEqualTo(original.order());
        assertThat(updated.href()).isEqualTo(original.href());
        assertThat(updated.mediaType()).isEqualTo(original.mediaType());
        assertThat(updated.skeleton()).isEqualTo(original.skeleton());
        assertThat(updated.segments()).containsExactly(replacement);
        assertThat(original.segments()).containsExactly(originalSegment);
    }

    // Suppressed deliberately: NullAway forbids this call statically, which is exactly why the runtime guard must
    // still exist and be proven for a caller reaching this record without that analysis.
    @SuppressWarnings("NullAway")
    @Test
    void constructor_nullId_isRejected() {
        assertThatNullPointerException()
                .isThrownBy(() -> new Unit(null, 0, "OEBPS/c01.xhtml", "application/xhtml+xml", SKELETON, List.of()));
    }

    @SuppressWarnings("NullAway")
    @Test
    void constructor_nullSkeleton_isRejected() {
        assertThatNullPointerException()
                .isThrownBy(() ->
                        new Unit("OEBPS/c01.xhtml", 0, "OEBPS/c01.xhtml", "application/xhtml+xml", null, List.of()));
    }
}
