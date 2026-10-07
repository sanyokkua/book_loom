package ua.bookloom.ui.control;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import ua.bookloom.ui.FxTestBase;

/** The accumulator applies what arrived between two pulses as one move; the pulse is driven by hand. */
@SuppressWarnings("NullAway.Init")
class PixelFlushTest extends FxTestBase {

    @Override
    public void start(final javafx.stage.Stage stage) {
        // no scene needed: the timer is driven by calling handle() directly
    }

    private final List<Double> moves = new ArrayList<>();

    // IF every event moved the view, THEN a burst of 200 events a second would lurch; a burst of four events between
    // two pulses is one move of their sum, and a pulse with nothing new moves nothing.
    @Test
    void handle_afterABurst_movesOnceByTheSumAndNotAgain() {
        final PixelFlush flush = new PixelFlush(pixels -> {
            moves.add(pixels);
            return pixels;
        });

        interact(() -> {
            flush.add(2);
            flush.add(3);
            flush.add(5);
            flush.add(10);
            flush.handle(1);
            flush.handle(2);
        });

        assertThat(moves).containsExactly(20.0);
    }

    // IF the opposite directions were not netted, THEN a reversal inside one frame would jitter; +10 and -4 flush as
    // +6.
    @Test
    void handle_opposingDeltasInOneFrame_flushTheirNet() {
        final PixelFlush flush = new PixelFlush(pixels -> {
            moves.add(pixels);
            return pixels;
        });

        interact(() -> {
            flush.add(10);
            flush.add(-4);
            flush.handle(1);
        });

        assertThat(moves).containsExactly(6.0);
    }
}
