package ua.bookloom.api.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * {@link ReviewMode}'s threshold and pause-point pairing.
 */
class ReviewModeTest {

    @ParameterizedTest
    @CsvSource({"UNATTENDED,0.60", "ASSISTED,0.75", "MANUAL,0.85"})
    void threshold_eachMode_matchesSpecifiedValue(final ReviewMode mode, final double expected) {
        assertThat(mode.threshold()).isEqualTo(expected);
    }

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
