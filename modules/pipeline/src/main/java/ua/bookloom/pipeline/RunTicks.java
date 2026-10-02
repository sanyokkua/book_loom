package ua.bookloom.pipeline;

import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/** Calls a run's watchdog on a cadence of its own, apart from the job thread it watches. */
@FunctionalInterface
interface RunTicks {

    /** One daemon thread per run, so a watchdog never keeps the application alive and dies with the run. */
    RunTicks DAEMON = (period, tick) -> {
        final ScheduledExecutorService ticker = Executors.newSingleThreadScheduledExecutor(
                Thread.ofPlatform().daemon().name("bookloom-stall-watchdog").factory());
        final ScheduledFuture<?> ticking =
                ticker.scheduleWithFixedDelay(tick, period.toMillis(), period.toMillis(), TimeUnit.MILLISECONDS);
        return () -> {
            ticking.cancel(false);
            ticker.shutdownNow();
        };
    };

    /**
     * Starts calling {@code tick} every {@code period}.
     *
     * @param period the non-null cadence
     * @param tick what to call; it must not throw, or a scheduled executor would stop calling it
     * @return what stops the cadence
     */
    Runnable start(Duration period, Runnable tick);
}
