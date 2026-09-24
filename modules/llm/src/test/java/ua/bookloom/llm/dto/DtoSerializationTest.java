package ua.bookloom.llm.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.inject.Guice;
import com.google.inject.Injector;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import ua.bookloom.llm.LlmModule;

/** Serialization contracts for the provider-wire DTOs and their shared mapper. */
class DtoSerializationTest {

    // Missing nullable controls are absent rather than explicit nulls or unsupported fields.
    @Test
    void ollamaChatRequest_withoutOptionalSettings_omitsOptionalKeys() {
        final ObjectMapper mapper = mapper();
        final JsonNode bodyWithNullTemperature = mapper.valueToTree(new OllamaChatRequest(
                "gemma4:e4b-mlx",
                List.of(new OllamaChatRequest.Message("user", "hello")),
                false,
                new OllamaChatRequest.Options(null),
                null,
                null));
        final JsonNode bodyWithoutOptions = mapper.valueToTree(new OllamaChatRequest(
                "gemma4:e4b-mlx", List.of(new OllamaChatRequest.Message("user", "hello")), false, null, null, null));

        assertThat(bodyWithNullTemperature.has("options")).isFalse();
        assertThat(bodyWithNullTemperature.has("format")).isFalse();
        assertThat(bodyWithNullTemperature.has("think")).isFalse();
        assertThat(bodyWithoutOptions.has("options")).isFalse();
    }

    // A supplied temperature is the only member serialized inside Ollama options.
    @Test
    void ollamaChatRequest_withTemperature_serializesOnlyTemperatureOption() {
        final JsonNode body = mapper().valueToTree(new OllamaChatRequest(
                "gemma4:e4b-mlx",
                List.of(new OllamaChatRequest.Message("user", "hello")),
                false,
                new OllamaChatRequest.Options(0.2),
                null,
                null));

        assertThat(body.path("options").toString()).isEqualTo("{\"temperature\":0.2}");
    }

    // The native thinking control is top-level and remains absent unless a caller explicitly requests it.
    @Test
    void ollamaChatRequest_reasoningDisabled_serializesTopLevelThinkFalse() {
        final JsonNode body = mapper().valueToTree(new OllamaChatRequest(
                "gemma4:e4b-mlx", List.of(new OllamaChatRequest.Message("user", "hello")), false, null, null, false));

        assertThat(body.path("think").isBoolean()).isTrue();
        assertThat(body.path("think").asBoolean()).isFalse();
    }

    // Unknown provider fields do not block reading the reply fields the engine consumes.
    @Test
    void openAiChatResponse_withReasoningToolsAndUsage_readsContentAndFinishReason() throws Exception {
        final OpenAiChatResponse response = mapper().readValue("""
                {
                  "model": "google/gemma-4-e4b",
                  "choices": [{
                    "message": {
                      "role": "assistant",
                      "content": "{\\\"segments\\\":[]}",
                      "reasoning_content": "thinking...",
                      "tool_calls": [{"id": "call_1"}]
                    },
                    "finish_reason": "stop"
                  }],
                  "usage": {"prompt_tokens": 3, "completion_tokens": 5}
                }
                """, OpenAiChatResponse.class);

        final OpenAiChatResponse.Choice choice =
                Objects.requireNonNull(response.choices(), "choices").getFirst();
        final OpenAiChatResponse.Message message = Objects.requireNonNull(choice.message(), "message");

        assertThat(response.choices()).hasSize(1);
        assertThat(message.content()).isEqualTo("{\"segments\":[]}");
        assertThat(choice.finishReason()).isEqualTo("stop");
    }

    // Optional response collections stay absent when a provider omits them.
    @Test
    void openAiChatResponse_withoutChoices_keepsCollectionAbsent() throws Exception {
        final OpenAiChatResponse response = mapper().readValue("{}", OpenAiChatResponse.class);

        assertThat(response.choices()).isNull();
    }

    // Present model listings are copied while omitted listings remain nullable for provider validation.
    @Test
    void modelListings_withAndWithoutEntries_preservesNullableCollections() throws Exception {
        final ObjectMapper mapper = mapper();
        final OllamaTagsResponse ollamaWithModels = mapper.readValue("""
                {"models":[{"name":"gemma4:e4b-mlx"}]}
                """, OllamaTagsResponse.class);
        final OpenAiModelsResponse openAiWithModels = mapper.readValue("""
                {"data":[{"id":"google/gemma-4-e4b"}]}
                """, OpenAiModelsResponse.class);
        final OllamaTagsResponse ollamaWithoutModels = mapper.readValue("{}", OllamaTagsResponse.class);
        final OpenAiModelsResponse openAiWithoutModels = mapper.readValue("{}", OpenAiModelsResponse.class);

        assertThat(Objects.requireNonNull(ollamaWithModels.models(), "models")
                        .getFirst()
                        .name())
                .isEqualTo("gemma4:e4b-mlx");
        assertThat(Objects.requireNonNull(openAiWithModels.data(), "data")
                        .getFirst()
                        .id())
                .isEqualTo("google/gemma-4-e4b");
        assertThat(ollamaWithoutModels.models()).isNull();
        assertThat(openAiWithoutModels.data()).isNull();
    }

    // Mutable input and accessor trees cannot change a structured-output DTO after construction.
    @Test
    void jsonSchemaDto_withMutableTrees_doesNotExposeChanges() {
        final ObjectMapper mapper = mapper();
        final ObjectNode sourceSchema = mapper.createObjectNode().put("type", "object");
        final OpenAiChatRequest.JsonSchemaDto schemaDto =
                new OpenAiChatRequest.JsonSchemaDto("draft", true, sourceSchema);

        sourceSchema.put("type", "string");
        final ObjectNode exposedSchema = (ObjectNode) schemaDto.schema();
        exposedSchema.put("type", "array");

        assertThat(schemaDto.schema().path("type").asText()).isEqualTo("object");
        assertThat(mapper.valueToTree(schemaDto).path("schema").path("type").asText())
                .isEqualTo("object");
    }

    // A malformed model listing fails deserialization instead of masquerading as an empty listing.
    @Test
    void openAiModelsResponse_withNonArrayData_failsDeserialization() {
        assertThatThrownBy(() -> mapper().readValue("{\"data\":\"nope\"}", OpenAiModelsResponse.class))
                .isInstanceOf(JsonProcessingException.class);
    }

    // The module shares one mapper configured to tolerate fields a provider adds later.
    @Test
    void objectMapper_fromLlmModule_isSingletonAndIgnoresUnknownFields() {
        final Injector injector = Guice.createInjector(new LlmModule());
        final ObjectMapper first = injector.getInstance(ObjectMapper.class);

        assertThat(first).isSameAs(injector.getInstance(ObjectMapper.class));
        assertThat(first.isEnabled(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES))
                .isFalse();
    }

    private static ObjectMapper mapper() {
        return Guice.createInjector(new LlmModule()).getInstance(ObjectMapper.class);
    }
}
