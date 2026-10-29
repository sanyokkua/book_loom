package ua.bookloom.api.llm;

/** The program serving a provider's models; it selects request fields, error rows and routes, not the client. */
public enum ServerEngine {
    /** Ollama, native or behind its OpenAI-compatible routes. */
    OLLAMA,
    /** LM Studio. */
    LM_STUDIO,
    /** llama.cpp's llama-server. */
    LLAMA_CPP,
    /** OpenRouter, hosted or on a custom domain. */
    OPENROUTER,
    /** Unsloth Studio. */
    UNSLOTH,
    /** Any other server; it gets no control until a check proves it. */
    GENERIC
}
