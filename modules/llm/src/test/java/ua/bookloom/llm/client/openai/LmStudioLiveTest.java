package ua.bookloom.llm.client.openai;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.URI;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ChatMessage;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.ChatRole;
import ua.bookloom.api.llm.ModelInfo;
import ua.bookloom.api.llm.ProviderConfig;
import ua.bookloom.api.llm.ProviderKind;
import ua.bookloom.api.llm.ResponseFormat;
import ua.bookloom.llm.LlmModule;
import ua.bookloom.llm.http.HttpClients;
import ua.bookloom.llm.http.HttpExchange;

/** Proves the OpenAI-compatible client against a configured, real LM Studio service. */
@Tag("liveLocal")
@EnabledIfEnvironmentVariable(named = "BOOKLOOM_LIVE_LMSTUDIO_URL", matches = ".+")
class LmStudioLiveTest {

    private static final String MODEL_ENV = "BOOKLOOM_LIVE_LMSTUDIO_MODEL";
    private static final String DEFAULT_MODEL = "google/gemma-4-e4b";
    private static final String TOKEN_REPLY = "⟦g0⟧KEEP⟦g1⟧";
    private static final ResponseFormat JSON_FORMAT = new ResponseFormat("live_json", """
                    {
                      "type": "object",
                      "properties": {"ok": {"type": "boolean"}},
                      "required": ["ok"],
                      "additionalProperties": false
                    }
                    """);
    private static final ResponseFormat TOKEN_FORMAT = new ResponseFormat("live_placeholder", """
                    {
                      "type": "object",
                      "properties": {"target": {"type": "string"}},
                      "required": ["target"],
                      "additionalProperties": false
                    }
                    """);

    // A reachable compatible endpoint proves the probe shape still matches LM Studio.
    @Test
    void probe_realLmStudio_returnsSuccess() {
        final Result<Boolean> result = client().probe().result();

        assertThat(result.isOk())
                .as("LM Studio probe result: " + result.error())
                .isTrue();
        assertThat(result.data()).isTrue();
    }

    // The real /v1/models response must include the model selected for the remaining checks.
    @Test
    void listModels_configuredModel_containsConfiguredModel() {
        final Result<List<ModelInfo>> result = client().listModels().result();

        assertThat(result.isOk())
                .as("LM Studio model-list result: " + result.error())
                .isTrue();
        assertThat(Objects.requireNonNull(result.data(), "models")).contains(new ModelInfo(modelId()));
    }

    // A strict schema reply must survive production reply sanitisation and remain parseable JSON.
    @Test
    void chat_jsonSchemaRequest_returnsParsableObject() throws Exception {
        final Result<ChatResponse> result = client().chat(
                        modelId(),
                        new ChatRequest(
                                List.of(new ChatMessage(
                                        ChatRole.USER, "Return exactly {\"ok\":true} and no other text.")),
                                0.0,
                                JSON_FORMAT))
                .result();

        final String content = responseContent(result);
        assertThat(content).doesNotContain("<think>").doesNotContain("</think>").doesNotContain("```");
        final JsonNode reply = new LlmModule().objectMapper().readTree(content);
        assertThat(reply.path("ok").booleanValue()).isTrue();
    }

    // Tokens are application data: a real schema-constrained reply must return both in their original order.
    @Test
    void chat_placeholderRoundTrip_preservesEveryToken() throws Exception {
        final Result<ChatResponse> result = client().chat(
                        modelId(),
                        new ChatRequest(
                                List.of(new ChatMessage(
                                        ChatRole.USER,
                                        "Return only {\"target\":\"" + TOKEN_REPLY + "\"} with no other text.")),
                                0.0,
                                TOKEN_FORMAT))
                .result();

        final JsonNode reply = new LlmModule().objectMapper().readTree(responseContent(result));
        assertThat(reply.path("target").asText()).isEqualTo(TOKEN_REPLY);
    }

    private static OpenAiCompatibleClient client() {
        final URI origin = URI.create(
                Objects.requireNonNull(System.getenv("BOOKLOOM_LIVE_LMSTUDIO_URL"), "BOOKLOOM_LIVE_LMSTUDIO_URL"));
        final ProviderConfig config = new ProviderConfig(
                "lmstudio",
                ProviderKind.OPENAI_COMPATIBLE,
                origin.resolve("/v1"),
                ProviderConfig.DEFAULT_CONNECT_TIMEOUT,
                ProviderConfig.DEFAULT_REQUEST_TIMEOUT);
        return new OpenAiCompatibleClient(config, new HttpExchange(new HttpClients()), new LlmModule().objectMapper());
    }

    private static String modelId() {
        return System.getenv().getOrDefault(MODEL_ENV, DEFAULT_MODEL);
    }

    private static String responseContent(final Result<ChatResponse> result) {
        assertThat(result.isOk()).as("LM Studio chat result: " + result.error()).isTrue();
        return Objects.requireNonNull(result.data(), "chat response").content();
    }
}
