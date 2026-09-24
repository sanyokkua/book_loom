package ua.bookloom.api.llm;

/** The outcome of a single provider-verification stage. */
public enum StageStatus {
    /** The stage completed successfully. */
    PASSED,

    /** The stage completed with a non-fatal limitation. */
    SOFT_PASS,

    /** The stage failed. */
    FAILED,

    /** The stage was not run. */
    SKIPPED
}
