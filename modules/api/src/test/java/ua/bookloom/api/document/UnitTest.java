package ua.bookloom.api.document;

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
