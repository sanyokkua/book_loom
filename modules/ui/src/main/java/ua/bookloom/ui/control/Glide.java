package ua.bookloom.ui.control;

import javafx.animation.AnimationTimer;

/**
 * One view's running glide: a target distance followed exponentially, frame by frame, so the view eases towards where
 * the notches add up to and stops exactly there, or at the view's end, whichever comes first.
 */
final class Glide extends AnimationTimer {

    private static final double NANOS_PER_SECOND = 1e9;
    private static final double FIRST_FRAME_SECONDS = 1.0 / 60;

    private final Scroller scroller;
    private double remaining;
    private long lastFrame;
    private boolean running;

    Glide(final Scroller scroller) {
        this.scroller = scroller;
    }

    void add(final double pixels) {
        remaining =
                ScrollGlide.retarget(remaining, pixels, Math.max(1, scroller.viewport()) * ScrollGlide.AHEAD_VIEWPORTS);
        if (!running) {
            running = true;
            lastFrame = 0;
            start();
        }
    }

    void halt() {
        stop();
        running = false;
        remaining = 0;
    }

    @Override
    public void handle(final long now) {
        final double seconds = lastFrame == 0 ? FIRST_FRAME_SECONDS : (now - lastFrame) / NANOS_PER_SECOND;
        lastFrame = now;
        final double step = ScrollGlide.followStep(remaining, Math.max(seconds, FIRST_FRAME_SECONDS / 4));
        remaining -= step;
        final double moved = scroller.moveBy(step);
        if (remaining == 0 || moved == 0) {
            halt();
        }
    }
}
