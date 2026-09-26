package ua.bookloom.ui.state;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A {@link TickSource} that never fires by itself: the test fires the captured tick, so the 100 ms cadence is driven
 * by the test and never by a clock.
 */
final class ManualTicks implements TickSource {

    private final AtomicReference<Runnable> tick = new AtomicReference<>();
    private final AtomicInteger starts = new AtomicInteger();
    private final AtomicInteger stops = new AtomicInteger();
    private volatile boolean stopperThrows;

    @Override
    public Runnable start(final Runnable onTick) {
        tick.set(onTick);
        starts.incrementAndGet();
        return () -> {
            stops.incrementAndGet();
            if (stopperThrows) {
                throw new IllegalStateException("the cadence could not be stopped");
            }
        };
    }

    /** Runs the tick the runner registered, on the calling thread. */
    void fire() {
        Objects.requireNonNull(tick.get(), "the runner never started its cadence")
                .run();
    }

    /** Makes the stopper of every cadence started afterwards throw, after counting the stop. */
    void failOnStop() {
        stopperThrows = true;
    }

    int starts() {
        return starts.get();
    }

    int stops() {
        return stops.get();
    }
}
