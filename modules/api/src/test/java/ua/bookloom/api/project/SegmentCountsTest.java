package ua.bookloom.api.project;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/**
 * {@code SegmentCounts}'s non-negative invariant.
 */
class SegmentCountsTest {

    @Test
    void constructor_negativeCount_throws() {
        assertThatThrownBy(() -> new SegmentCounts(-1, 0, 0, 0, 0)).isInstanceOf(IllegalArgumentException.class);
    }
}
