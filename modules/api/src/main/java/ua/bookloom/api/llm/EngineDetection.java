package ua.bookloom.api.llm;

import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Which server engine answers at a provider's address, as found on Save, Test or Detect again.
 *
 * @param engine the engine found, or the person's "Treat this server as..." choice when {@code source} is
 *     {@link Source#OVERRIDE}; non-null
 * @param version the version the server reported (Ollama {@code 0.40.11}, llama-server {@code b11669}), or null when it
 *     did not say
 * @param router whether a llama-server serves several models by id ({@code "role":"router"})
 * @param probes how many GET requests the detection sent; 0 for a known host or the person's choice
 * @param source whether the engine was detected or chosen by the person; non-null
 * @param suitability whether the app found can translate a book; non-null
 * @param offerNativeConnection whether to offer, once, Ollama's own connection to a provider saved as
 *     OpenAI-compatible; never true while the person's earlier "Keep as is" stands
 */
public record EngineDetection(
        ServerEngine engine,
        @Nullable String version,
        boolean router,
        int probes,
        Source source,
        Suitability suitability,
        boolean offerNativeConnection) {

    /** Rejects missing parts and a negative probe count. */
    public EngineDetection {
        Objects.requireNonNull(engine, "engine");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(suitability, "suitability");
        if (probes < 0) {
            throw new IllegalArgumentException("probes must not be negative");
        }
    }

    /** Where the engine came from. */
    public enum Source {
        /** Found by the shape of the server's replies or by its host. */
        DETECTED("detected"),
        /** The person's "Treat this server as..." choice; no request was sent. */
        OVERRIDE("override");

        private final String token;

        Source(final String token) {
            this.token = token;
        }

        /**
         * The word the {@code engine detected} log line names this source by.
         *
         * @return {@code detected} or {@code override}
         */
        public String token() {
            return token;
        }
    }

    /** Whether the server found can be used to translate a book. */
    public enum Suitability {
        /** A server that can translate. */
        SUITABLE,
        /** An app whose server refuses the answer schema every translation call needs (GPT4All). */
        NOT_SUITABLE_STRUCTURED,
        /** An app whose "model" is a workspace that chats over the person's documents (AnythingLLM). */
        NOT_SUITABLE_DOCUMENTS,
        /** A hosted API with its own request shape that is not built yet (Anthropic, Gemini); OpenRouter reaches it. */
        NOT_SUPPORTED_YET
    }
}
