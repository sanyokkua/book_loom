package ua.bookloom.llm.client.ollama;

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
import ua.bookloom.api.llm.FinishReason;
import ua.bookloom.api.llm.ModelInfo;
import ua.bookloom.api.llm.ProviderConfig;
import ua.bookloom.api.llm.ProviderKind;
import ua.bookloom.api.llm.ReasoningLevel;
import ua.bookloom.api.llm.ResponseFormat;
import ua.bookloom.api.pipeline.CallKind;
import ua.bookloom.llm.LlmModule;
import ua.bookloom.llm.http.HttpClients;
import ua.bookloom.llm.http.HttpExchange;

/** Proves the native client against a configured, real local Ollama service. */
@Tag("liveLocal")
@EnabledIfEnvironmentVariable(named = "BOOKLOOM_LIVE_OLLAMA_URL", matches = ".+")
class OllamaLiveTest {

    // The thinking request shape (15i.X2): a call with thinking on is accepted by the real server and still answers
    // inside the cap it was given plus the room for its reasoning.
    @Test
    void chat_thinkingOn_isAcceptedAndStillAnswers() {
        final Result<ChatResponse> result = client().chat(
                        modelId(),
                        new ChatRequest(
                                List.of(new ChatMessage(ChatRole.USER, "Answer with the single word: yes")),
                                0.0,
                                null,
                                ReasoningLevel.ON,
                                8192,
                                null,
                                200,
                                null,
                                CallKind.DRAFT))
                .result();

        final String content = responseContent(result);
        assertThat(content).isNotBlank().doesNotContain("<think>").doesNotContain("</think>");
    }

    private static final int CACHE_PROMPT_REPEATS = 110;
    private static final int CACHE_NUM_CTX = 8192;
    private static final int CACHE_REPLY_CAP = 16;
    private static final String MODEL_ENV = "BOOKLOOM_LIVE_OLLAMA_MODEL";
    private static final String DEFAULT_MODEL = "gemma4:e4b-mlx";
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

    // A reachable native endpoint proves the probe shape still matches Ollama.
    @Test
    void probe_realOllama_returnsSuccess() {
        final Result<Boolean> result = client().probe().result();

        assertThat(result.isOk()).as("Ollama probe result: " + result.error()).isTrue();
        assertThat(result.data()).isTrue();
    }

    // The real /api/tags response must include the model selected for the remaining checks.
    @Test
    void listModels_configuredModel_containsConfiguredModel() {
        final Result<List<ModelInfo>> result = client().listModels().result();

        assertThat(result.isOk())
                .as("Ollama model-list result: " + result.error())
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

    // A real generation call at the effective context size reports a normal finish and real usage figures.
    @Test
    void chat_realOllamaWithContext8192_reportsNormalFinishAndTokenUsage() {
        final Result<ChatResponse> result = client().chat(
                        modelId(),
                        new ChatRequest(
                                List.of(new ChatMessage(
                                        ChatRole.USER,
                                        "Return only {\"target\":\"" + TOKEN_REPLY + "\"} with no other text.")),
                                0.0,
                                TOKEN_FORMAT,
                                null,
                                8192,
                                null))
                .result();

        final ChatResponse response = Objects.requireNonNull(result.data(), "chat response");
        assertThat(result.isOk()).as("Ollama chat result: " + result.error()).isTrue();
        assertThat(response.finishReason()).isEqualTo(FinishReason.STOP);
        assertThat(response.usage()).isNotNull();
        assertThat(response.usage().completion()).isGreaterThan(0);
        assertThat(response.usage().generation()).isPositive();
    }

    // The same long system prompt sent twice must be served from the server's prompt cache the second time (15h.C5).
    @Test
    void chat_sameSystemPromptTwice_secondReplyReportsCachedPromptTokens() {
        final String system = "You are a careful assistant for the fictional town of Ashvale. "
                + "The town keeps a ledger of lanterns, bridges and weather. ".repeat(CACHE_PROMPT_REPEATS);

        chatWithSystem(system, "Say one word about lanterns.");
        final ChatResponse second = chatWithSystem(system, "Say one word about bridges.");

        assertThat(second.usage()).isNotNull();
        assertThat(second.usage().cachedPrompt()).isNotNull().isGreaterThan(0);
    }

    private static ChatResponse chatWithSystem(String system, String user) {
        final Result<ChatResponse> result = client().chat(
                        modelId(),
                        new ChatRequest(
                                List.of(new ChatMessage(ChatRole.SYSTEM, system), new ChatMessage(ChatRole.USER, user)),
                                0.0,
                                null,
                                null,
                                CACHE_NUM_CTX,
                                null,
                                CACHE_REPLY_CAP))
                .result();
        assertThat(result.isOk()).as("Ollama chat result: " + result.error()).isTrue();
        return Objects.requireNonNull(result.data(), "chat response");
    }

    private static OllamaClient client() {
        final ProviderConfig config = new ProviderConfig(
                "ollama",
                ProviderKind.OLLAMA,
                URI.create(
                        Objects.requireNonNull(System.getenv("BOOKLOOM_LIVE_OLLAMA_URL"), "BOOKLOOM_LIVE_OLLAMA_URL")),
                ProviderConfig.DEFAULT_CONNECT_TIMEOUT,
                ProviderConfig.DEFAULT_REQUEST_TIMEOUT);
        return new OllamaClient(config, new HttpExchange(new HttpClients()), new LlmModule().objectMapper());
    }

    private static String modelId() {
        return System.getenv().getOrDefault(MODEL_ENV, DEFAULT_MODEL);
    }

    private static String responseContent(final Result<ChatResponse> result) {
        assertThat(result.isOk()).as("Ollama chat result: " + result.error()).isTrue();
        return Objects.requireNonNull(result.data(), "chat response").content();
    }
}
