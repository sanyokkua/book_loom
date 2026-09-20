package ua.bookloom.api.pipeline;

/**
 * Receives one lifecycle event from a translation job.
 */
@FunctionalInterface
public interface JobListener {

    /**
     * Handles an event in the order the job emitted it.
     *
     * @param event the non-null event
     */
    void onEvent(JobEvent event);
}
