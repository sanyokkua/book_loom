package ua.bookloom.pipeline;

/** How one wait of an automatic recovery ended. */
enum RecoveryWake {
    /** The wait ran its full length: the provider is due a probe. */
    DUE,
    /** The person resumed the run, by Retry now, Skip segment or Resume. */
    RESUMED,
    /** The person paused the run during the wait, which holds it until they resume it. */
    HELD,
    /** The run was stopped. */
    CANCELLED
}
