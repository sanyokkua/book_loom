package ua.bookloom.api.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * {@link ReviewCounts}'s guards: no count is negative, and the flagged records with no machine target are a part of
 * the flagged records.
 */
class ReviewCountsTest {

    @Test
    void constructor_negativeCount_isRejected() {
        assertThatIllegalArgumentException().isThrownBy(() -> new ReviewCounts(5, 1, 1, -1, 0, 0, 0, 0));
    }

    @Test
    void constructor_flaggedWithoutTargetWithinFlagged_isAccepted() {
        // 3 of the 3 flagged records without a machine target is the most the invariant allows
        assertThat(new ReviewCounts(3, 0, 0, 3, 0, 0, 0, 3).flaggedWithoutTarget())
                .isEqualTo(3);
    }

    @ParameterizedTest
    @ValueSource(ints = {4, -1})
    void constructor_flaggedWithoutTargetAboveFlaggedOrNegative_isRejected(final int flaggedWithoutTarget) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new ReviewCounts(3, 0, 0, 3, 0, 0, 0, flaggedWithoutTarget));
    }
}
