package ua.bookloom.ui.state;

/**
 * The cadence a run publishes on, behind a seam so a test fires the ticks itself instead of waiting on a clock.
 */
interface TickSource {

    /**
     * Starts calling {@code tick} periodically until the returned stopper runs.
     *
     * @param tick the work to run each period; must not throw
     * @return a stopper that ends the cadence; safe to call once
     */
    Runnable start(Runnable tick);
}
