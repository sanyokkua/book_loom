package ua.bookloom.ui.control;

import java.util.function.DoubleConsumer;

/**
 * Collects the pixel deltas of a trackpad or a momentum tail between two animation pulses and applies their sum once
 * per pulse. macOS delivers about two hundred such events a second at uneven gaps; applying each as it comes makes a
 * list lurch, applying the sum once per frame moves it smoothly and exactly as far as the events add up to. The
 * system already eased the gesture, so nothing is eased here. Logs nothing: it runs on the FX thread per event.
 */
final class PixelFlush {

    private final DoubleConsumer mover;
    private final PulseClock.Ticker ticker;
    private double pending;
    private boolean running;

    /**
     * @param mover moves the view by the given pixels, as far as it can
     */
    PixelFlush(final DoubleConsumer mover) {
        this(mover, PulseClock.animation());
    }

    PixelFlush(final DoubleConsumer mover, final PulseClock clock) {
        this.mover = mover;
        this.ticker = clock.create(this::handle);
    }

    void add(final double pixels) {
        pending += pixels;
        if (!running) {
            running = true;
            ticker.start();
        }
    }

    /** What has been added and not yet moved; positive down the content. */
    double pending() {
        return pending;
    }

    void halt() {
        ticker.stop();
        running = false;
        pending = 0;
    }

    /** One pulse: applies what was collected since the last, and stops the ticker once a pulse finds nothing. */
    void handle(final long now) {
        if (pending == 0) {
            halt();
            return;
        }
        final double flushed = pending;
        pending = 0;
        mover.accept(flushed);
    }
}
