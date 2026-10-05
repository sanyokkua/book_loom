package ua.bookloom.api.pipeline;

/**
 * A filter over a project's review queue.
 */
public enum ReviewFilter {

    /** Every flagged segment. */
    ALL_FLAGGED,

    /** Flagged segments whose findings concern a name. */
    NAMES,

    /** Flagged segments whose findings concern an omission. */
    OMISSIONS,

    /** Flagged segments whose findings concern a foreign passage kept untranslated. */
    FOREIGN_KEPT,

    /** Accepted segments no person has reviewed that the final audit doubts, each with the check that fired. */
    SUSPICIOUS,

    /** Every segment, regardless of status. */
    ALL_SEGMENTS
}
