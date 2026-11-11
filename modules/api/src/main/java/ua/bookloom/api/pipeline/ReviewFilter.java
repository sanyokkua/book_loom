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

    /**
     * Accepted segments no person has reviewed that the final audit doubts, that hold a repair-level finding the
     * Proofread stage left, or whose open gender deferral's gender has arrived and the text disagrees with it, and every
     * segment not flagged that holds a proofread mark no window has dealt with — the marks an export counts as waiting.
     */
    SUSPICIOUS,

    /** Segments whose proofread rewrite was kept, or waits for the person as a proposal. */
    CHANGED_BY_PROOFREAD,

    /** Segments that hold an open gender deferral: a character they name had no gender, or had it changed. */
    WAITING_ON_GENDER,

    /** Every segment, regardless of status. */
    ALL_SEGMENTS
}
