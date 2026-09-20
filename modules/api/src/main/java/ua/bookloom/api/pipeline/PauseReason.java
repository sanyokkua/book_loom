package ua.bookloom.api.pipeline;

/**
 * The reason a job entered its paused state.
 */
public enum PauseReason {

    /** A caller requested a pause. */
    REQUESTED,

    /** The after-segment pause point was reached. */
    AFTER_SEGMENT,

    /** The after-section pause point was reached. */
    AFTER_SECTION,

    /** The between-stages pause point was reached. */
    BETWEEN_STAGES,

    /** An otherwise terminal error was encountered. */
    ON_ERROR
}
