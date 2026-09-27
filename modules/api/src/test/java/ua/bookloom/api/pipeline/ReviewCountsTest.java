package ua.bookloom.api.pipeline;

import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;

/**
 * {@link ReviewCounts}'s negative-count guard.
 */
class ReviewCountsTest {

    @Test
    void constructor_negativeCount_isRejected() {
        assertThatIllegalArgumentException().isThrownBy(() -> new ReviewCounts(5, 1, 1, -1, 0, 0, 0));
    }
}
