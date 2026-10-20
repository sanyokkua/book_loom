package ua.bookloom.api.pipeline;

/** How one step ended. */
public enum StepResult {
    /** The step ran and finished. */
    DONE,
    /** The step needed nothing and was done at once with no model call. */
    NO_CALL,
    /** The step ended without its outcome. */
    FAILED
}
