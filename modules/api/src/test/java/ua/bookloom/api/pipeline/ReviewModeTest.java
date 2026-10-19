package ua.bookloom.api.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * {@link ReviewMode}'s pause-point pairing.
 */
class ReviewModeTest {

    @Test
    void pausePoints_unattended_isEmpty() {
        assertThat(ReviewMode.UNATTENDED.pausePoints()).isEmpty();
    }

    @Test
    void pausePoints_assisted_isOnFlaggedAndOnError() {
        assertThat(ReviewMode.ASSISTED.pausePoints()).isEqualTo(Set.of(PausePoint.ON_FLAGGED, PausePoint.ON_ERROR));
    }

    @Test
    void pausePoints_manual_isAfterSegmentOnFlaggedAndOnError() {
        assertThat(ReviewMode.MANUAL.pausePoints())
                .isEqualTo(Set.of(PausePoint.AFTER_SEGMENT, PausePoint.ON_FLAGGED, PausePoint.ON_ERROR));
    }
}
