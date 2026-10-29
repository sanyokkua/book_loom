package ua.bookloom.api.llm;

/** The ordered checks performed while verifying a provider and model. */
public enum VerificationStage {
    /** Checks that the provider endpoint is reachable. */
    CONNECTION,

    /** Checks that the selected model is available. */
    MODELS,

    /** Checks that the selected model can complete an inference request. */
    INFERENCE,

    /**
     * The optional Advanced features check: a few short calls with invented text that find out which request fields
     * the server honours (the answer schema, a thinking switch, the extra samplers, the answer-cap field). Only the
     * {@link VerificationPolicy#ADVANCED} policy runs it, because it uses the model.
     */
    ADVANCED
}
