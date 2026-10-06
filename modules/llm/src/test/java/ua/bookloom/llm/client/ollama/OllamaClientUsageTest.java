package ua.bookloom.llm.client.ollama;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
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

/** Exercises the Ollama-native client's context-size request shaping and reported token usage. */
class OllamaClientUsageTest {

    private static final String MODEL_ID = "gemma4:e4b-mlx";
    private static final String CHAT_PATH = "/api/chat";

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

    @ParameterizedTest
    @MethodSource("contextWindowRequests")
    void chat_temperatureAndOrContextWindow_postsExpectedNativeOptions(ChatRequest request, String expectedBody) {
        stubChat("""
                {"model":"gemma4:e4b-mlx","message":{"content":"reply"},"done_reason":"stop"}
                """);

        sendChat(request);

        server.verify(postRequestedFor(urlEqualTo(CHAT_PATH)).withRequestBody(equalToJson(expectedBody)));
    }

    @ParameterizedTest
    @MethodSource("usageReplies")
    void chat_usageFieldsVary_carriesTheReportedTokenUsage(String body, TokenUsage expected) {
        stubChat(body);

        assertThat(sendChat(messageOnlyRequest()).usage()).isEqualTo(expected);
    }

    // The timeout arithmetic is proven in RequestTimeoutsTest; this proves the wire honours it: a reply five
    // times slower than the configured second is abandoned at that second.
    @Test
    void chat_replySlowerThanTheConfiguredTimeout_failsAtTheConfiguredTimeout() {
        server.stubFor(post(urlEqualTo(CHAT_PATH))
                .willReturn(aResponse().withFixedDelay(5000).withBody("{}")));

        final Result<ChatResponse> result = client(Duration.ofSeconds(1))
                .chat(MODEL_ID, messageOnlyRequest())
                .result();

        assertTimedOutAtOneSecond(result);
    }

    private static void assertTimedOutAtOneSecond(Result<ChatResponse> result) {
        final AppError error = Objects.requireNonNull(result.error(), "error");
        assertThat(error.code()).isEqualTo(ErrorCode.timeout);
        assertThat(error.details()).contains("timeoutMs=1000");
    }

    private static Stream<Arguments> contextWindowRequests() {
        return Stream.of(
                arguments(
                        new ChatRequest(List.of(new ChatMessage(ChatRole.USER, "hello")), 0.2, null, null, 8192, null),
                        """
                        {"model":"gemma4:e4b-mlx","messages":[{"role":"user","content":"hello"}],"stream":true,"options":{"temperature":0.2,"num_ctx":8192}}
                        """),
                arguments(
                        new ChatRequest(List.of(new ChatMessage(ChatRole.USER, "hello")), null, null, null, 8192, null),
                        """
                        {"model":"gemma4:e4b-mlx","messages":[{"role":"user","content":"hello"}],"stream":true,"options":{"num_ctx":8192}}
                        """),
                arguments(
                        new ChatRequest(
                                List.of(new ChatMessage(ChatRole.USER, "hello")), null, null, null, 8192, null, 80),
                        """
                        {"model":"gemma4:e4b-mlx","messages":[{"role":"user","content":"hello"}],"stream":true,"options":{"num_ctx":8192,"num_predict":80}}
                        """),
                arguments(
                        new ChatRequest(
                                List.of(new ChatMessage(ChatRole.USER, "hello")), null, null, null, null, null, 80),
                        """
                        {"model":"gemma4:e4b-mlx","messages":[{"role":"user","content":"hello"}],"stream":true,"options":{"num_predict":80}}
                        """));
    }

    private static Stream<Arguments> usageReplies() {
        return Stream.of(
                arguments("""
                        {"model":"gemma4:e4b-mlx","message":{"content":"reply"},"done_reason":"stop","prompt_eval_count":812,"eval_count":96,"eval_duration":3200000000}
                        """, new TokenUsage(812, 96, Duration.ofMillis(3200))),
                arguments("""
                        {"model":"gemma4:e4b-mlx","message":{"content":"reply"},"done_reason":"stop"}
                        """, null),
                arguments("""
                        {"model":"gemma4:e4b-mlx","message":{"content":"reply"},"done_reason":"stop","eval_count":40}
                        """, new TokenUsage(null, 40, null)),
                arguments("""
                        {"model":"gemma4:e4b-mlx","message":{"content":"reply"},"done_reason":"stop","prompt_eval_count":812,"eval_count":96,"eval_duration":3200000000,"prompt_eval_duration":450000000}
                        """, new TokenUsage(812, 96, Duration.ofMillis(3200), Duration.ofMillis(450), null)),
                arguments("""
                        {"model":"gemma4:e4b-mlx","message":{"content":"reply"},"done_reason":"stop","prompt_eval_duration":450000000}
                        """, new TokenUsage(null, null, null, Duration.ofMillis(450), null)));
    }

    private void stubChat(String body) {
        server.stubFor(post(urlEqualTo(CHAT_PATH)).willReturn(okJson(body)));
    }

    private ChatResponse sendChat(ChatRequest request) {
        return sendChat(client(), request);
    }

    private ChatResponse sendChat(OllamaClient client, ChatRequest request) {
        final Result<ChatResponse> result = client.chat(MODEL_ID, request).result();
        assertThat(result.isOk()).as("Ollama chat result: " + result.error()).isTrue();
        return Objects.requireNonNull(result.data(), "chat response");
    }

    private OllamaClient client() {
        return client(Duration.ofSeconds(2));
    }

    private OllamaClient client(Duration requestTimeout) {
        final ProviderConfig config = new ProviderConfig(
                "ollama", ProviderKind.OLLAMA, URI.create(server.baseUrl()), Duration.ofSeconds(2), requestTimeout);
        return new OllamaClient(config, new HttpExchange(new HttpClients()), new LlmModule().objectMapper());
    }

    private static ChatRequest messageOnlyRequest() {
        return new ChatRequest(List.of(new ChatMessage(ChatRole.USER, "hello")));
    }
}
