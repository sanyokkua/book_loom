package ua.bookloom.ui.state;

import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;

/**
 * The production cadence: one daemon scheduler thread per run at a fixed period.
 *
 * <p>Per run, and not a task in the background pool, because a paused run parks its own pool thread inside
 * {@code run()} for as long as the pause lasts and the pool has only two threads: a cadence queued behind it could
 * never run, and the log and figures would stop moving exactly when the person is watching them.
 */
@Slf4j
final class FixedRateTicks implements TickSource {

    /** The most often progress and log lines reach the FX thread; five thousand segments must not mean five thousand runs. */
    static final long PERIOD_MILLIS = 100;

    private static final String THREAD_NAME = "bookloom-run-ticker";

    @Override
    public Runnable start(final Runnable tick) {
        Objects.requireNonNull(tick, "tick");
        log.debug("starting the run cadence, period {} ms", PERIOD_MILLIS);
        final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(
                Thread.ofPlatform().daemon().name(THREAD_NAME).factory());
        final ScheduledFuture<?> schedule =
                scheduler.scheduleAtFixedRate(tick, PERIOD_MILLIS, PERIOD_MILLIS, TimeUnit.MILLISECONDS);
        return () -> {
            schedule.cancel(false);
            scheduler.shutdownNow();
        };
    }
}
