package ua.bookloom.api.llm;

import java.util.Objects;

/** The program serving a provider's models; it selects request fields, error rows and routes, not the client. */
public enum ServerEngine {
    /** Ollama, native or behind its OpenAI-compatible routes. */
    OLLAMA("Ollama"),
    /** LM Studio. */
    LM_STUDIO("LM Studio"),
    /** llama.cpp's llama-server. */
    LLAMA_CPP("llama-server"),
    /** OpenRouter, hosted or on a custom domain. */
    OPENROUTER("OpenRouter"),
    /** Unsloth Studio. */
    UNSLOTH("Unsloth"),
    /** Any other server; it gets no control until a check proves it. */
    GENERIC("an OpenAI-compatible server");

    private final String label;

    ServerEngine(final String label) {
        this.label = Objects.requireNonNull(label, "label");
    }

    /**
     * The engine's name in an English sentence such as "The server at this address is LM Studio, not Ollama."; the
     * window words its own languages from the constant, not from this.
     *
     * @return the product's own spelling; never null
     */
    public String label() {
        return label;
    }
}
