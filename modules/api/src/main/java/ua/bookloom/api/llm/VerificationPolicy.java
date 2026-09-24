package ua.bookloom.api.llm;

/** Selects how much work a provider verification performs. */
public enum VerificationPolicy {
    /** Runs all applicable verification stages. */
    FULL,

    /** Checks the connection and model list, running inference only after a soft pass. */
    PREFLIGHT
}
