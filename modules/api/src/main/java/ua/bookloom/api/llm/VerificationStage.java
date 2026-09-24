package ua.bookloom.api.llm;

/** The ordered checks performed while verifying a provider and model. */
public enum VerificationStage {
    /** Checks that the provider endpoint is reachable. */
    CONNECTION,

    /** Checks that the selected model is available. */
    MODELS,

    /** Checks that the selected model can complete an inference request. */
    INFERENCE
}
