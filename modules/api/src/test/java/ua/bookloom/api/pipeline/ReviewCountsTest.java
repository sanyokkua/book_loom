package ua.bookloom.api.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * {@link ReviewCounts}'s guards: no count is negative, and the flagged records with no machine target are a part of
 * the flagged records.
 */
class ReviewCountsTest {

    @Test
    void constructor_negativeCount_isRejected() {
        assertThatIllegalArgumentException().isThrownBy(() -> new ReviewCounts(5, 1, 1, -1, 0, 0, 0, 0, 0));
    }

    @Test
    void constructor_flaggedWithoutTargetWithinFlagged_isAccepted() {
        // 3 of the 3 flagged records without a machine target is the most the invariant allows
        assertThat(new ReviewCounts(3, 0, 0, 3, 0, 0, 0, 3, 0).flaggedWithoutTarget())
                .isEqualTo(3);
    }

    @ParameterizedTest
    @ValueSource(ints = {4, -1})
    void constructor_flaggedWithoutTargetAboveFlaggedOrNegative_isRejected(final int flaggedWithoutTarget) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new ReviewCounts(3, 0, 0, 3, 0, 0, 0, flaggedWithoutTarget, 0));
    }

    @Test
    void constructor_negativeSuspicious_isRejected() {
        assertThatIllegalArgumentException().isThrownBy(() -> new ReviewCounts(5, 1, 1, 0, 0, 0, 0, 0, 0, -1));
    }

    @Test
    void constructor_withoutSuspicious_countsNone() {
        assertThat(new ReviewCounts(5, 1, 1, 0, 0, 0, 0, 0, 0).suspicious()).isZero();
    }

    @ParameterizedTest(name = "changed {0}, waiting {1}")
    @CsvSource({"-1, 0", "0, -1"})
    void constructor_negativeProofreadList_isRejected(final int changed, final int waiting) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new ReviewCounts(5, 1, 1, 0, 0, 0, 0, 0, 0, 0, 0, changed, waiting));
    }

    // The desk counts its own lists by the rules it lists them by; every other count stays as the records gave it.
    @Test
    void withDeskLists_threeTwoOne_replacesOnlyTheDesksLists() {
        final ReviewCounts counts = new ReviewCounts(100, 90, 2, 4, 1, 0, 3, 1, 5, 0, 1).withDeskLists(3, 2, 1);

        assertThat(counts).isEqualTo(new ReviewCounts(100, 90, 2, 4, 1, 0, 3, 1, 5, 3, 1, 2, 1));
    }
}
