package ua.bookloom.api.llm;

/**
 * How much a call lets a model think: the intent a call asks for, and after the model's profile is applied, the level the
 * model is sent (design D20).
 */
public enum ReasoningLevel {
    OFF,
    LOW,
    ON;

    /**
     * Whether the model reasons at all, which decides the output-cap headroom a reply needs.
     *
     * @return {@code true} for {@link #LOW} and {@link #ON}, {@code false} for {@link #OFF}
     */
    public boolean isThinking() {
        return this != OFF;
    }
}
