package ua.bookloom.api.pipeline;

/**
 * Boundaries at which a caller may ask a job to pause automatically.
 */
public enum PausePoint {

    /** After each decided segment. */
    AFTER_SEGMENT,

    /** After the last segment in a non-empty section. */
    AFTER_SECTION,

    /** After translation and before export. */
    BETWEEN_STAGES,

    /** When an otherwise terminal error occurs. */
    ON_ERROR
}
