package ua.bookloom.api.llm;

/** Where a model's stored speed was measured. */
public enum MeasuredFrom {
    /** The Settings inference test. */
    TEST,
    /** An earlier translation run on this computer. */
    RUN
}
