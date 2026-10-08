package ua.bookloom.api.pipeline;

/** Which paragraphs an export's consistency pass checks against the paragraphs around them. */
public enum ConsistencyScope {
    /** Every paragraph on the Max quality dial, else the doubted ones; the default. */
    BY_DIAL,
    /** The paragraphs the run repaired or flagged and the ones the audit doubts. */
    DOUBTED,
    /** Every machine-translated paragraph, which costs one model call each. */
    EVERY_SEGMENT
}
