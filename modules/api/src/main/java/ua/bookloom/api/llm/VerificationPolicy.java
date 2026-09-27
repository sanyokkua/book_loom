package ua.bookloom.api.llm;

/** Selects how much work a provider verification performs. */
public enum VerificationPolicy {
    /** Runs all applicable verification stages. */
    FULL,

    /** Checks the connection and model list, running inference only after a soft pass. */
    PREFLIGHT,

    /** Runs the connection stage only. */
    CONNECTION,

    /** Runs the connection stage, then the models stage. */
    CONNECTION_AND_MODELS
}
