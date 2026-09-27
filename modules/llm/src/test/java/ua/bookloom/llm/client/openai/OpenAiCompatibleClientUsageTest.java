package ua.bookloom.llm.client.openai;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import java.net.URI;
import java.time.Duration;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.function.LongSupplier;
import java.util.stream.LongStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ChatMessage;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.ChatRole;
import ua.bookloom.api.llm.ProviderConfig;
import ua.bookloom.api.llm.ProviderKind;
import ua.bookloom.api.llm.TokenUsage;
import ua.bookloom.llm.LlmModule;
import ua.bookloom.llm.http.HttpClients;
import ua.bookloom.llm.http.HttpExchange;

/** Proves the OpenAI-compatible client never sends a context field and reports wall-clock-measured usage. */
class OpenAiCompatibleClientUsageTest {

    private static final String MODEL_ID = "google/gemma-4-e4b";
    private static final String CHAT_PATH = "/v1/chat/completions";

    private final WireMockServer server =
            new WireMockServer(WireMockConfiguration.options().dynamicPort());

    @BeforeEach
    void startServer() {
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop();
    }

    // The OpenAI chat shape has no context-size field, so a request carrying one must still omit it entirely.
    @Test
    void chat_requestCarriesContextWindow_neverSendsAContextField() {
        stubChat("""
                {"model":"google/gemma-4-e4b","choices":[{"message":{"content":"reply"},"finish_reason":"stop"}]}
                """);

        sendChat(
                client(() -> 0L),
                new ChatRequest(List.of(new ChatMessage(ChatRole.USER, "hello")), 0.2, null, null, 8192, null));

        server.verify(postRequestedFor(urlEqualTo(CHAT_PATH)).withRequestBody(equalToJson("""
                        {"model":"google/gemma-4-e4b","messages":[{"role":"user","content":"hello"}],"stream":false,"temperature":0.2}
                        """)));
        server.verify(postRequestedFor(urlEqualTo(CHAT_PATH))
                .withRequestBody(matchingJsonPath("$[?(!@.num_ctx && !@.n_ctx && !@.context_length && !@.options)]")));
    }

    // Reported token counts plus the measured wall-clock duration become the response's usage.
    @Test
    void chat_usageReported_carriesCountsAndMeasuredWallClockDuration() {
        stubChat("""
                {"model":"google/gemma-4-e4b","choices":[{"message":{"content":"reply"},"finish_reason":"stop"}],"usage":{"prompt_tokens":640,"completion_tokens":75,"total_tokens":715}}
                """);

        final ChatResponse response = sendChat(client(scriptedNanoTime(0L, 2_500_000_000L)), messageOnlyRequest());

        assertThat(response.usage()).isEqualTo(new TokenUsage(640, 75, Duration.ofMillis(2500)));
    }

    // No reported usage object means no token usage, whatever the measured duration was.
    @Test
    void chat_noUsageReported_carriesNullUsage() {
        stubChat("""
                {"model":"google/gemma-4-e4b","choices":[{"message":{"content":"reply"},"finish_reason":"stop"}]}
                """);

        assertThat(sendChat(client(() -> 0L), messageOnlyRequest()).usage()).isNull();
    }

    // A configured 1 s timeout is replaced by the 200 s a 400-token expectation scales to.
    @Test
    void chat_expectedOutputScalesTimeoutPastAShortConfiguredFloor_getsItsReply() {
        server.stubFor(post(urlEqualTo(CHAT_PATH))
                .willReturn(aResponse()
                        .withFixedDelay(1500)
                        .withBody(
                                "{\"model\":\"google/gemma-4-e4b\",\"choices\":[{\"message\":{\"content\":\"reply\"},\"finish_reason\":\"stop\"}]}")
                        .withHeader("Content-Type", "application/json")));

        final ChatResponse response = sendChat(
                client(() -> 0L, Duration.ofSeconds(1)),
                new ChatRequest(List.of(new ChatMessage(ChatRole.USER, "hello")), null, null, null, null, 400));

        assertThat(response.content()).isEqualTo("reply");
    }

    // With no expected output, a 1 s configured timeout is kept exactly, so the delayed reply is never seen.
    @Test
    void chat_noExpectedOutputWithShortConfiguredTimeout_failsAtTheConfiguredFloor() {
        server.stubFor(post(urlEqualTo(CHAT_PATH))
                .willReturn(aResponse().withFixedDelay(1500).withBody("{}")));

        final Result<ChatResponse> result = client(() -> 0L, Duration.ofSeconds(1))
                .chat(MODEL_ID, messageOnlyRequest())
                .result();

        assertTimedOutAtOneSecond(result);
    }

    // A one-token expectation still scales to less than the 1 s floor, so the floor still applies.
    @Test
    void chat_tinyExpectedOutputWithShortConfiguredTimeout_failsAtTheConfiguredFloor() {
        server.stubFor(post(urlEqualTo(CHAT_PATH))
                .willReturn(aResponse().withFixedDelay(1500).withBody("{}")));

        final Result<ChatResponse> result = client(() -> 0L, Duration.ofSeconds(1))
                .chat(
                        MODEL_ID,
                        new ChatRequest(List.of(new ChatMessage(ChatRole.USER, "hello")), null, null, null, null, 1))
                .result();

        assertTimedOutAtOneSecond(result);
    }

    private static void assertTimedOutAtOneSecond(Result<ChatResponse> result) {
        final AppError error = Objects.requireNonNull(result.error(), "error");
        assertThat(error.code()).isEqualTo(ErrorCode.timeout);
        assertThat(error.details()).contains("timeoutMs=1000");
    }

    private void stubChat(String body) {
        server.stubFor(post(urlEqualTo(CHAT_PATH)).willReturn(okJson(body)));
    }

    private ChatResponse sendChat(OpenAiCompatibleClient client, ChatRequest request) {
        final Result<ChatResponse> result = client.chat(MODEL_ID, request).result();
        assertThat(result.isOk())
                .as("OpenAI-compatible chat result: " + result.error())
                .isTrue();
        return Objects.requireNonNull(result.data(), "chat response");
    }

    private OpenAiCompatibleClient client(LongSupplier nanoTime) {
        return client(nanoTime, Duration.ofSeconds(2));
    }

    private OpenAiCompatibleClient client(LongSupplier nanoTime, Duration requestTimeout) {
        final ProviderConfig config = new ProviderConfig(
                "lmstudio",
                ProviderKind.OPENAI_COMPATIBLE,
                URI.create(server.baseUrl() + "/v1"),
                Duration.ofSeconds(2),
                requestTimeout);
        return new OpenAiCompatibleClient(
                config, new HttpExchange(new HttpClients()), new LlmModule().objectMapper(), nanoTime);
    }

    private static LongSupplier scriptedNanoTime(long... values) {
        final Iterator<Long> answers = LongStream.of(values).boxed().iterator();
        return answers::next;
    }

    private static ChatRequest messageOnlyRequest() {
        return new ChatRequest(List.of(new ChatMessage(ChatRole.USER, "hello")));
    }
}
