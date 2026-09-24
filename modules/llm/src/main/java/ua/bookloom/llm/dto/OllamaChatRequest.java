package ua.bookloom.llm.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * The JSON request body for Ollama's native chat endpoint.
 *
 * @param model non-null model identifier
 * @param messages non-null messages, copied to preserve order and prevent caller mutation
 * @param stream whether Ollama should stream the reply; inference clients send {@code false}
 * @param options optional native generation controls, omitted when absent
 * @param format optional structured-output format, omitted when absent
 * @param think optional native reasoning-output control, omitted when unsupported or unspecified
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record OllamaChatRequest(
        @JsonProperty("model") String model,
        @JsonProperty("messages") List<Message> messages,
        @JsonProperty("stream") boolean stream,
        @JsonProperty("options") @Nullable Options options,
        @JsonProperty("format") @Nullable JsonNode format,
        @JsonProperty("think") @Nullable Boolean think) {

    /** Rejects incomplete requests, omits empty options, and protects message order from caller mutation. */
    public OllamaChatRequest {
        Objects.requireNonNull(model, "model");
        Objects.requireNonNull(messages, "messages");
        messages = List.copyOf(messages);
        if (options != null && options.temperature() == null) {
            options = null;
        }
    }

    /**
     * One speaker and text pair in a native Ollama conversation.
     *
     * @param role non-null speaker label accepted by Ollama
     * @param content non-null message text
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Message(
            @JsonProperty("role") String role,
            @JsonProperty("content") String content) {

        /** Rejects incomplete wire messages. */
        public Message {
            Objects.requireNonNull(role, "role");
            Objects.requireNonNull(content, "content");
        }
    }

    /**
     * Ollama-native optional generation controls used by this change.
     *
     * @param temperature nullable so an unspecified temperature is omitted from the wire body
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Options(
            @JsonProperty("temperature") @Nullable Double temperature) {}
}
