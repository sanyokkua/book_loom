package ua.bookloom.ui.control;

import java.util.function.DoubleUnaryOperator;
import javafx.animation.AnimationTimer;
import lombok.extern.slf4j.Slf4j;

/**
 * Collects the pixel deltas of a trackpad or a momentum tail between two animation pulses and applies their sum once
 * per pulse. macOS delivers about two hundred such events a second at uneven gaps; applying each as it comes makes a
 * list lurch, applying the sum once per frame moves it smoothly and exactly as far as the events add up to. The
 * system already eased the gesture, so nothing is eased here.
 */
@Slf4j
final class PixelFlush extends AnimationTimer {

    private final DoubleUnaryOperator mover;
    private double pending;
    private boolean running;

    /**
     * @param mover moves the view by the given pixels and answers how far it really moved
     */
    PixelFlush(final DoubleUnaryOperator mover) {
        this.mover = mover;
    }

    void add(final double pixels) {
        pending += pixels;
        if (log.isTraceEnabled()) {
            log.trace("pixel scroll accumulated {} px, pending {} px", pixels, pending);
        }
        if (!running) {
            running = true;
            start();
        }
    }

    void halt() {
        stop();
        running = false;
        pending = 0;
    }

    /** One pulse: applies what was collected since the last, and stops the timer once a pulse finds nothing. */
    @Override
    public void handle(final long now) {
        if (pending == 0) {
            halt();
            return;
        }
        final double flushed = pending;
        pending = 0;
        final double moved = mover.applyAsDouble(flushed);
        if (log.isTraceEnabled()) {
            log.trace("pixel scroll flushed {} px, moved {} px", flushed, moved);
        }
    }
}
