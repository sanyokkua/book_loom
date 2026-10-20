package ua.bookloom.api.pipeline;

/** Where one announced step is. */
public enum StepState {
    /** The step began. */
    STARTED,
    /** The step ended; the event carries its {@link StepResult}. */
    ENDED
}
