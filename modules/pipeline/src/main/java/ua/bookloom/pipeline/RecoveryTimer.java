package ua.bookloom.pipeline;

import java.time.Duration;

/**
 * Lets a recovery wait pass. In production the whole wait is spent on the job's condition, where a person's Retry now,
 * Pause or Stop ends it early; a test's timer advances its scripted clock instead and leaves nothing to wait, so a
 * twelve-hour outage is replayed in milliseconds. Bound in {@link PipelineModule}, so a whole-application test can
 * replace it.
 */
@FunctionalInterface
public interface RecoveryTimer {

    /** The production timer: the condition waits the whole delay. */
    RecoveryTimer REAL = Duration::toNanos;

    /**
     * Starts a wait of {@code delay}.
     *
     * @param delay the non-null wait the schedule chose
     * @return the nanoseconds still to wait on the job's condition
     */
    long nanosToWait(Duration delay);
}
