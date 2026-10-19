package ua.bookloom.llm.client.openai;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.matching.StringValuePattern;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ChatMessage;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.ChatRole;
import ua.bookloom.api.llm.FinishReason;
import ua.bookloom.api.llm.ProviderConfig;
import ua.bookloom.api.llm.ProviderKind;
import ua.bookloom.llm.LlmModule;
import ua.bookloom.llm.http.HttpClients;
import ua.bookloom.llm.http.HttpExchange;

/**
 * A thinking model behind an OpenAI-compatible endpoint, at the HTTP seam: a capped reply it spent on reasoning — the
 * shape gemma4 answers through Ollama's {@code /v1} ({@code reasoning}) and LM Studio ({@code reasoning_content}) when
 * reasoning is not turned off — is asked for once more with room, and never reaches the reply text.
 */
class OpenAiCompatibleClientReasoningTest {

    private static final String MODEL_ID = "google/gemma-4-e4b";
    private static final String CHAT_PATH = "/v1/chat/completions";

    private final WireMockServer server =
            new WireMockServer(WireMockConfiguration.options().dynamicPort().bindAddress("127.0.0.1"));

    @BeforeEach
    void startServer() {
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop();
    }

    // A thinking model that ignores reasoning control spends the whole cap on reasoning: Ollama's /v1 names it
    // "reasoning", LM Studio "reasoning_content". The call is made once more with four times the cap.
    @ParameterizedTest
    @CsvSource({"reasoning", "reasoning_content"})
    void chat_reasoningUsedTheWholeCap_retriesOnceWithFourTimesTheCap(String reasoningField) {
        stubChatSequence(reasoningOnly(reasoningField), """
                {"model":"google/gemma-4-e4b","choices":[{"message":{"content":"{\\"target\\":\\"Вага\\"}","%s":"Thinking..."},"finish_reason":"stop"}]}
                """.formatted(reasoningField));

        final ChatResponse response = sendChat(cappedRequest(128, 8192));

        assertThat(response).isEqualTo(new ChatResponse("{\"target\":\"Вага\"}", FinishReason.STOP));
        server.verify(postRequestedFor(urlEqualTo(CHAT_PATH)).withRequestBody(maxTokens(128)));
        server.verify(postRequestedFor(urlEqualTo(CHAT_PATH)).withRequestBody(maxTokens(512)));
    }

    @Test
    void chat_reasoningUsedTheRaisedCapToo_returnsEmptyCompletionAfterTwoCalls() {
        stubChatSequence(reasoningOnly("reasoning_content"), reasoningOnly("reasoning_content"));

        assertChatError(cappedRequest(128, 8192), ErrorCode.emptyCompletion);
        server.verify(2, postRequestedFor(urlEqualTo(CHAT_PATH)));
    }

    @Test
    void chat_raisedCap_isBoundByTheContextWindow() {
        stubChatSequence(reasoningOnly("reasoning"), reasoningOnly("reasoning"));

        client().chat(MODEL_ID, cappedRequest(128, 300));

        server.verify(postRequestedFor(urlEqualTo(CHAT_PATH)).withRequestBody(maxTokens(300)));
    }

    @Test
    void chat_emptyReplyWithoutReasoning_isNotRetried() {
        stubChat("""
                {"model":"google/gemma-4-e4b","choices":[{"message":{"content":""},"finish_reason":"length"}]}
                """);

        assertChatError(cappedRequest(128, 8192), ErrorCode.emptyCompletion);
        server.verify(1, postRequestedFor(urlEqualTo(CHAT_PATH)));
    }

    private void stubChat(String body) {
        server.stubFor(post(urlEqualTo(CHAT_PATH))
                .willReturn(aResponse().withStatus(200).withBody(body)));
    }

    private void stubChatSequence(String first, String second) {
        server.stubFor(post(urlEqualTo(CHAT_PATH))
                .inScenario("reasoning")
                .whenScenarioStateIs(Scenario.STARTED)
                .willReturn(aResponse().withStatus(200).withBody(first))
                .willSetStateTo("second"));
        server.stubFor(post(urlEqualTo(CHAT_PATH))
                .inScenario("reasoning")
                .whenScenarioStateIs("second")
                .willReturn(aResponse().withStatus(200).withBody(second)));
    }

    private static String reasoningOnly(String reasoningField) {
        return """
                {"model":"google/gemma-4-e4b","choices":[{"message":{"content":"","%s":"Thinking Process: 1. Analyze"},"finish_reason":"length"}]}
                """.formatted(reasoningField);
    }

    private static ChatRequest cappedRequest(int maxOutputTokens, int contextWindow) {
        return new ChatRequest(
                List.of(new ChatMessage(ChatRole.USER, "hello")),
                null,
                null,
                false,
                contextWindow,
                null,
                maxOutputTokens,
                null,
                null);
    }

    private static StringValuePattern maxTokens(int cap) {
        return matchingJsonPath("$.max_tokens", equalTo(Integer.toString(cap)));
    }

    private ChatResponse sendChat(ChatRequest request) {
        final Result<ChatResponse> result = client().chat(MODEL_ID, request).result();
        assertThat(result.isOk()).as("chat result: " + result.error()).isTrue();
        return Objects.requireNonNull(result.data(), "chat response");
    }

    private void assertChatError(ChatRequest request, ErrorCode expected) {
        final Result<ChatResponse> result = client().chat(MODEL_ID, request).result();
        assertThat(Objects.requireNonNull(result.error(), "error").code()).isEqualTo(expected);
    }

    private OpenAiCompatibleClient client() {
        final ProviderConfig config = new ProviderConfig(
                "lmstudio",
                ProviderKind.OPENAI_COMPATIBLE,
                URI.create(server.baseUrl() + "/v1"),
                Duration.ofSeconds(2),
                Duration.ofSeconds(2));
        return new OpenAiCompatibleClient(
                config, new HttpExchange(new HttpClients()), new LlmModule().objectMapper(), System::nanoTime);
    }
}
