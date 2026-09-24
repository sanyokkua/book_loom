package ua.bookloom.llm.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * The JSON request body for an OpenAI-compatible chat-completions endpoint.
 *
 * @param model non-null model identifier
 * @param messages non-null messages, copied to preserve order and prevent caller mutation
 * @param stream whether the provider should stream; inference clients send {@code false}
 * @param temperature optional generation control, omitted when absent
 * @param responseFormat optional strict structured-output mode, omitted when absent
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record OpenAiChatRequest(
        @JsonProperty("model") String model,
        @JsonProperty("messages") List<Message> messages,
        @JsonProperty("stream") boolean stream,
        @JsonProperty("temperature") @Nullable Double temperature,
        @JsonProperty("response_format") @Nullable ResponseFormatDto responseFormat) {

    /** Rejects incomplete requests and protects the message order from caller mutation. */
    public OpenAiChatRequest {
        Objects.requireNonNull(model, "model");
        Objects.requireNonNull(messages, "messages");
        messages = List.copyOf(messages);
    }

    /**
     * One speaker and text pair in an OpenAI-compatible conversation.
     *
     * @param role non-null speaker label accepted by the provider
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
     * Requests the provider's strict JSON-schema response mode.
     *
     * @param type non-null format discriminator, normally {@code json_schema}
     * @param jsonSchema non-null schema descriptor
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ResponseFormatDto(
            @JsonProperty("type") String type,
            @JsonProperty("json_schema") JsonSchemaDto jsonSchema) {

        /** Rejects incomplete response-format descriptions. */
        public ResponseFormatDto {
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(jsonSchema, "jsonSchema");
        }
    }

    /**
     * The schema name, strictness, and JSON schema sent inside {@code response_format}.
     *
     * @param name non-null name identifying this schema to the provider
     * @param strict whether the provider must enforce the schema strictly
     * @param schema non-null JSON schema tree
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record JsonSchemaDto(
            @JsonProperty("name") String name,
            @JsonProperty("strict") boolean strict,
            @JsonProperty("schema") JsonNode schema) {

        /** Rejects incomplete input and keeps later tree mutations from changing this request. */
        public JsonSchemaDto {
            Objects.requireNonNull(name, "name");
            schema = Objects.requireNonNull(schema, "schema").deepCopy();
        }

        /**
         * Returns a detached tree so callers cannot mutate the schema stored in this request.
         *
         * @return an independent JSON tree
         */
        @Override
        @JsonProperty("schema")
        public JsonNode schema() {
            return schema.deepCopy();
        }
    }
}
