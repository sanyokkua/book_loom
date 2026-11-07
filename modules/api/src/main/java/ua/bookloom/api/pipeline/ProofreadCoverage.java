package ua.bookloom.api.pipeline;

/** Which decided segments the Proofread stage reads at one quality setting (design D15). */
public enum ProofreadCoverage {

    /** Fast: only the term sweep and the deferrals settled by code; no model call. */
    NONE,

    /** Balanced: windows around the marked and the doubted segments. */
    MARKED_AND_DOUBTFUL,

    /** Max: the marked and doubted windows, then a second round over every story paragraph. */
    MARKED_AND_DOUBTFUL_THEN_EVERY_PARAGRAPH
}
