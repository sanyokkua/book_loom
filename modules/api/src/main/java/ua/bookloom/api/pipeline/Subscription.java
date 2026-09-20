package ua.bookloom.api.pipeline;

/**
 * A handle for removing a job-event subscription.
 */
@FunctionalInterface
public interface Subscription {

    /** Removes the associated listener. */
    void unsubscribe();
}
