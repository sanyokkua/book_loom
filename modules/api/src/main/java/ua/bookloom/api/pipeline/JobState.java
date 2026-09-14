package ua.bookloom.api.pipeline;

/**
 * Lifecycle states of a translation job.
 */
public enum JobState {

    /** Constructed but not started. */
    NEW,

    /** Actively translating or exporting. */
    RUNNING,

    /** Waiting at a configured or requested boundary. */
    PAUSED,

    /** Finished and written successfully. */
    COMPLETED,

    /** Stopped by cancellation. */
    CANCELLED,

    /** Stopped because an unrecoverable error reached the job boundary. */
    FAILED
}
