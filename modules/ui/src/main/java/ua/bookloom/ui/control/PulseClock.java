package ua.bookloom.ui.control;

import java.util.function.LongConsumer;
import javafx.animation.AnimationTimer;

/**
 * Where the animation pulses of the scroll code come from: JavaFX's own in the app, a hand-driven one in the replay
 * tests, so that a recorded gesture can be played against exact frame times.
 */
@FunctionalInterface
interface PulseClock {

    /**
     * Makes a ticker that stays stopped until started.
     *
     * @param onPulse called with the pulse time in nanoseconds on every pulse while the ticker runs
     * @return a stopped ticker
     */
    Ticker create(LongConsumer onPulse);

    /** The pulses of the JavaFX toolkit. */
    static PulseClock animation() {
        return onPulse -> new Ticker() {
            private final AnimationTimer timer = new AnimationTimer() {
                @Override
                public void handle(final long now) {
                    onPulse.accept(now);
                }
            };

            @Override
            public void start() {
                timer.start();
            }

            @Override
            public void stop() {
                timer.stop();
            }
        };
    }

    /** A running or stopped source of pulses. */
    interface Ticker {

        void start();

        void stop();
    }
}
