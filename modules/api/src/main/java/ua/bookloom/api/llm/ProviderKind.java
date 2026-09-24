package ua.bookloom.api.llm;

/** The wire dialect used to communicate with a language-model provider. */
public enum ProviderKind {
    /** Ollama's native API. */
    OLLAMA,

    /** An API compatible with the OpenAI chat-completions interface. */
    OPENAI_COMPATIBLE
}
