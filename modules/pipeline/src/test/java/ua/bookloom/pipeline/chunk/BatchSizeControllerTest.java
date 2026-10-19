package ua.bookloom.pipeline.chunk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ua.bookloom.pipeline.chunk.BatchSizeController.Failure;

class BatchSizeControllerTest {

    @ParameterizedTest
    @CsvSource({"16,8", "9,4", "2,1", "1,1"})
    void recordFailure_anySize_halvesDownToTheMinimum(final int initial, final int expected) {
        final BatchSizeController controller = new BatchSizeController(1, 16, initial, 3);

        controller.recordFailure(Failure.OMISSION);

        assertThat(controller.size()).isEqualTo(expected);
    }

    @Test
    void recordClean_streakShortOfThreshold_keepsSize() {
        final BatchSizeController controller = new BatchSizeController(1, 16, 4, 3);

        controller.recordClean();
        controller.recordClean();

        assertThat(controller.size()).isEqualTo(4);
    }

    @Test
    void recordClean_streakReachesThreshold_growsByOne() {
        final BatchSizeController controller = new BatchSizeController(1, 16, 4, 3);

        controller.recordClean();
        controller.recordClean();
        controller.recordClean();

        assertThat(controller.size()).isEqualTo(5);
    }

    @Test
    void recordClean_atMaximum_staysThere() {
        final BatchSizeController controller = new BatchSizeController(1, 4, 4, 1);

        controller.recordClean();

        assertThat(controller.size()).isEqualTo(4);
    }

    @Test
    void recordFailure_afterAlmostCompleteStreak_restartsTheStreak() {
        final BatchSizeController controller = new BatchSizeController(1, 16, 8, 3);
        controller.recordClean();
        controller.recordClean();

        controller.recordFailure(Failure.MERGE);
        controller.recordClean();

        assertThat(controller.size()).isEqualTo(4);
    }

    @Test
    void constructor_initialAboveMaximum_isRejected() {
        assertThatThrownBy(() -> new BatchSizeController(1, 4, 5, 3)).isInstanceOf(IllegalArgumentException.class);
    }
}
