package ua.bookloom.api.pipeline;

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
