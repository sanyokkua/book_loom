package ua.bookloom.api.pipeline;

/**
 * Boundaries at which a caller may ask a job to pause automatically.
 */
public enum PausePoint {

    /** After each decided segment. */
    AFTER_SEGMENT,

    /** After the last segment in a non-empty section. */
    AFTER_SECTION,

    /** After translation, before revision or the end. */
    BETWEEN_STAGES,

    /** When an otherwise terminal error occurs. */
    ON_ERROR,

    /** When a segment is flagged. */
    ON_FLAGGED
}
