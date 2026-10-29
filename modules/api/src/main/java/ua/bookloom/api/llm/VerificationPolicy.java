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
    CONNECTION_AND_MODELS,

    /**
     * Asks the server's health route first and fails while it says it is still loading a model, then runs the
     * connection and models stages. Only the wake of a run paused on a loading model asks it, because a llama-server
     * answers {@code 503} on every route while it loads and its health route is the one that says when it is done.
     */
    HEALTH_AND_MODELS
}
