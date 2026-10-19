package ua.bookloom.api.pipeline;

import java.time.Duration;
import java.util.Set;
import ua.bookloom.api.Result;

/**
 * A synchronous translation run that can be controlled at safe boundaries by another thread.
 */
public interface TranslationJob {

    /**
     * Runs the job on the calling thread and returns its terminal report.
     *
     * @return a successful result carrying a report even when the job ends cancelled or failed, or a boundary error
     */
    Result<JobReport> run();

    /** Requests a pause at the next supported boundary. */
    void pause();

    /** Resumes a paused job. */
    void resume();

    /**
     * Resumes a job paused on a provider error without sending the failing call again: the segment it was for is
     * flagged with that error and the run goes on with the next one. Ignored unless the job is paused; after a pause
     * that names no failing segment it resumes like {@link #resume()}.
     */
    void skipSegment();

    /** How long an outage may last before a run stops waking by itself and waits for the person. */
    Duration DEFAULT_MAX_OUTAGE = Duration.ofHours(12);

    /**
     * Sets how a pause on a provider error checks the provider before the run resumes by itself, for outages of up to
     * {@link #DEFAULT_MAX_OUTAGE}; until it is set the provider counts as reachable at every wake.
     *
     * @param probe the non-null probe of the run's provider and model
     */
    default void recoverWith(ProviderProbe probe) {
        recoverWith(probe, DEFAULT_MAX_OUTAGE);
    }

    /**
     * Sets how a pause on a provider error checks the provider before the run resumes by itself, and how long an
     * outage may last before the run stops waking and waits for the person.
     *
     * @param probe the non-null probe of the run's provider and model
     * @param maxOutage the non-null, positive longest outage the run waits through by itself
     */
    void recoverWith(ProviderProbe probe, Duration maxOutage);

    /** Requests cancellation at the next safe point. */
    void cancel();

    /**
     * Replaces the pause points applied from the next boundary onward.
     *
     * @param points the non-null set of enabled pause points
     */
    void pauseAt(Set<PausePoint> points);

    /**
     * Returns the current lifecycle state.
     *
     * @return the current state
     */
    JobState state();

    /**
     * Adds a listener for ordered events emitted on the job thread.
     *
     * @param listener the non-null event listener
     * @return a subscription that removes the listener
     */
    Subscription subscribe(JobListener listener);
}
