package ua.bookloom.llm.client.ollama;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import java.io.IOException;
import java.net.ServerSocket;
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
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ChatMessage;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.ChatRole;
import ua.bookloom.api.llm.FinishReason;
import ua.bookloom.api.llm.ModelInfo;
import ua.bookloom.api.llm.ProviderConfig;
import ua.bookloom.api.llm.ProviderKind;
import ua.bookloom.api.llm.ResponseFormat;
import ua.bookloom.llm.LlmModule;
import ua.bookloom.llm.http.HttpClients;
import ua.bookloom.llm.http.HttpExchange;

/** Verifies Ollama-native request and response behavior at the real HTTP boundary. */
class OllamaClientTest {

    private static final String MODEL_ID = "gemma4:e4b-mlx";
    private static final String CHAT_PATH = "/api/chat";
    private static final String SCHEMA = """
            {"type":"object","properties":{"segments":{"type":"array"}},"required":["segments"]}
            """;
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

    @Test
    void kind_reportsOllamaDialect() {
        assertThat(client().kind()).isEqualTo(ProviderKind.OLLAMA);
    }

    @Test
    void chat_withTemperatureAndFormat_postsOnlySupportedNativeFields() {
        stubChat("""
                {"model":"gemma4:e4b-mlx","message":{"content":"<think>private reasoning</think>{\\\"segments\\\":[]}","thinking":"ignored"},"done_reason":"stop"}
                """);

        final ChatResponse response = sendChat(formattedRequest());

        assertThat(response.content()).isEqualTo("{\"segments\":[]}");
        assertThat(response.finishReason()).isEqualTo(FinishReason.STOP);
        server.verify(postRequestedFor(urlEqualTo(CHAT_PATH)).withRequestBody(equalToJson("""
                        {
                          "model":"gemma4:e4b-mlx",
                          "messages":[{"role":"system","content":"Translate faithfully."},{"role":"user","content":"hello"},{"role":"assistant","content":"prior"}],
                          "stream":false,
                          "options":{"temperature":0.2},
                          "format":{"type":"object","properties":{"segments":{"type":"array"}},"required":["segments"]}
                        }
                        """)));
    }

    @Test
    void chat_withoutOptionalSettings_omitsOptionsAndFormat() {
        stubChat("""
                {"model":"gemma4:e4b-mlx","message":{"content":"reply"},"done_reason":"stop"}
                """);

        sendChat(messageOnlyRequest());

        server.verify(postRequestedFor(urlEqualTo(CHAT_PATH)).withRequestBody(equalToJson("""
                        {"model":"gemma4:e4b-mlx","messages":[{"role":"user","content":"hello"}],"stream":false}
                        """)));
    }

    @Test
    void chat_acrossRequests_neverCallsShow() {
        stubChat("""
                {"model":"gemma4:e4b-mlx","message":{"content":"reply"},"done_reason":"stop"}
                """);

        sendChat(messageOnlyRequest());
        sendChat(messageOnlyRequest());

        server.verify(2, postRequestedFor(urlEqualTo(CHAT_PATH)));
        server.verify(0, getRequestedFor(urlEqualTo("/api/show")));
    }

    @ParameterizedTest
    @CsvSource({"stop, STOP", "length, LENGTH", "unload, OTHER"})
    void chat_doneReason_mapsStopLengthAndUnknown(String doneReason, FinishReason expected) {
        stubChat("""
                {"model":"gemma4:e4b-mlx","message":{"content":"reply"},"done_reason":"%s"}
                """.formatted(doneReason));

        final ChatResponse response = sendChat(messageOnlyRequest());

        assertThat(response.finishReason()).isEqualTo(expected);
    }

    @Test
    void chat_withoutDoneReason_returnsOtherFinish() {
        stubChat("""
                {"model":"gemma4:e4b-mlx","message":{"content":"reply"}}
                """);

        assertThat(sendChat(messageOnlyRequest()).finishReason()).isEqualTo(FinishReason.OTHER);
    }

    @Test
    void chat_doneReasonLoadWithBlankContent_returnsEmptyCompletion() {
        stubChat("""
                {"model":"gemma4:e4b-mlx","message":{"content":"  \\n","thinking":"ignored"},"done_reason":"load"}
                """);

        assertChatError(messageOnlyRequest(), ErrorCode.emptyCompletion);
    }

    @Test
    void chat_reasoningOnlyContent_returnsSuccessfulEmptyReplyWithStopFinish() {
        stubChat("""
                {"model":"gemma4:e4b-mlx","message":{"content":"<think>private reasoning</think>"},"done_reason":"stop"}
                """);
        assertThat(sendChat(messageOnlyRequest())).isEqualTo(new ChatResponse("", FinishReason.STOP));
    }

    @Test
    void chat_modelMismatch_returnsTheReceivedReply() {
        stubChat("""
                {"model":"different-model","message":{"content":"reply"},"done_reason":"stop"}
                """);

        final ChatResponse response = sendChat(messageOnlyRequest());

        assertThat(response.content()).isEqualTo("reply");
    }

    @ParameterizedTest
    @MethodSource("malformedChatBodies")
    void chat_malformedResponse_returnsInternal(String body) {
        stubChat(body);

        assertChatError(messageOnlyRequest(), ErrorCode.internal);
    }

    @ParameterizedTest
    @MethodSource("invalidSchemas")
    void chat_invalidSchema_returnsValidationWithoutSendingRequest(String schema) {
        final ChatRequest request = new ChatRequest(
                List.of(new ChatMessage(ChatRole.USER, "hello")), 0.2, new ResponseFormat("draft", schema));

        assertChatError(request, ErrorCode.validation);

        server.verify(0, postRequestedFor(urlEqualTo(CHAT_PATH)));
    }

    @Test
    void listModels_validTags_returnsIdsInServerOrder() {
        server.stubFor(get(urlEqualTo("/api/tags")).willReturn(okJson("""
                {"models":[{"name":"gemma4:e4b-mlx","size":1},{"name":"llama3.2:3b","size":2}]}
                """)));

        final Result<List<ModelInfo>> result = client().listModels().result();

        assertThat(result.isOk()).isTrue();
        assertThat(Objects.requireNonNull(result.data(), "models"))
                .containsExactly(new ModelInfo("gemma4:e4b-mlx"), new ModelInfo("llama3.2:3b"));
    }

    @Test
    void listModels_emptyTags_succeedsWithoutModels() {
        server.stubFor(get(urlEqualTo("/api/tags")).willReturn(okJson("{\"models\":[]}")));

        final Result<List<ModelInfo>> result = client().listModels().result();

        assertThat(result.isOk()).isTrue();
        assertThat(Objects.requireNonNull(result.data(), "models")).isEmpty();
    }

    @ParameterizedTest
    @MethodSource("malformedTagBodies")
    void listModels_malformedTags_returnsDiscoveryFailed(String body) {
        server.stubFor(get(urlEqualTo("/api/tags")).willReturn(okJson(body)));

        assertThat(Objects.requireNonNull(client().listModels().result().error(), "error")
                        .code())
                .isEqualTo(ErrorCode.discoveryFailed);
    }

    @Test
    void listModels_serverError_mapsToDiscoveryFailed() {
        server.stubFor(get(urlEqualTo("/api/tags"))
                .willReturn(aResponse().withStatus(503).withBody("private")));

        assertThat(Objects.requireNonNull(client().listModels().result().error(), "error")
                        .code())
                .isEqualTo(ErrorCode.discoveryFailed);
    }

    @Test
    void listModels_authFailure_preservesAuth() {
        server.stubFor(get(urlEqualTo("/api/tags"))
                .willReturn(aResponse().withStatus(401).withBody("private")));

        assertThat(Objects.requireNonNull(client().listModels().result().error(), "error")
                        .code())
                .isEqualTo(ErrorCode.auth);
    }

    @Test
    void listModels_timeout_preservesTimeout() {
        server.stubFor(get(urlEqualTo("/api/tags"))
                .willReturn(aResponse().withStatus(200).withFixedDelay(1000).withBody("{\"models\":[]}")));

        final Result<List<ModelInfo>> result = client(URI.create(server.baseUrl()), Duration.ofMillis(100))
                .listModels()
                .result();

        assertThat(Objects.requireNonNull(result.error(), "error").code()).isEqualTo(ErrorCode.timeout);
    }

    @Test
    void listModels_closedEndpoint_preservesUnreachable() throws IOException {
        final Result<List<ModelInfo>> result =
                client(closedEndpoint(), Duration.ofSeconds(2)).listModels().result();

        assertThat(Objects.requireNonNull(result.error(), "error").code()).isEqualTo(ErrorCode.unreachable);
    }

    @Test
    void listModels_interruptedRequest_preservesCancellation() {
        final Result<List<ModelInfo>> result;
        final boolean interruptRestored;
        try {
            Thread.currentThread().interrupt();
            result = client().listModels().result();
            interruptRestored = Thread.interrupted();
        } finally {
            Thread.interrupted();
        }

        assertThat(Objects.requireNonNull(result.error(), "error").code()).isEqualTo(ErrorCode.cancelled);
        assertThat(interruptRestored).isTrue();
    }

    @ParameterizedTest
    @MethodSource("chatHttpFailures")
    void chat_httpFailures_returnTypedCodes(int status, String body, ErrorCode expected) {
        stubChatStatus(status, body);

        final Result<ChatResponse> result =
                client().chat(MODEL_ID, messageOnlyRequest()).result();

        final AppError error = Objects.requireNonNull(result.error(), "error");
        assertThat(error.code()).isEqualTo(expected);
        assertThat(error.details()).doesNotContain(body);
    }

    @Test
    void chat_modelNotFound404_returnsModelNotFound() {
        stubChatStatus(404, "{\"error\":\"model 'nope:latest' not found\"}");

        assertChatError(messageOnlyRequest(), ErrorCode.modelNotFound);
    }

    @Test
    void chat_closedEndpoint_returnsUnreachable() throws IOException {
        final URI closedEndpoint = closedEndpoint();

        final Result<ChatResponse> result = client(closedEndpoint, Duration.ofSeconds(2))
                .chat(MODEL_ID, messageOnlyRequest())
                .result();

        assertThat(Objects.requireNonNull(result.error(), "error").code()).isEqualTo(ErrorCode.unreachable);
    }

    private void stubChat(String body) {
        server.stubFor(post(urlEqualTo(CHAT_PATH)).willReturn(okJson(body)));
    }

    private void stubChatStatus(int status, String body) {
        server.stubFor(post(urlEqualTo(CHAT_PATH))
                .willReturn(aResponse().withStatus(status).withBody(body)));
    }

    private ChatResponse sendChat(ChatRequest request) {
        final Result<ChatResponse> result = client().chat(MODEL_ID, request).result();
        assertThat(result.isOk()).as("Ollama chat result: " + result.error()).isTrue();
        return Objects.requireNonNull(result.data(), "chat response");
    }

    private void assertChatError(ChatRequest request, ErrorCode expected) {
        final Result<ChatResponse> result = client().chat(MODEL_ID, request).result();
        assertThat(Objects.requireNonNull(result.error(), "error").code()).isEqualTo(expected);
    }

    private OllamaClient client() {
        return client(URI.create(server.baseUrl()), Duration.ofSeconds(2));
    }

    private OllamaClient client(URI endpoint, Duration requestTimeout) {
        final ProviderConfig config =
                new ProviderConfig("ollama", ProviderKind.OLLAMA, endpoint, Duration.ofSeconds(2), requestTimeout);
        return new OllamaClient(config, new HttpExchange(new HttpClients()), new LlmModule().objectMapper());
    }

    private static ChatRequest messageOnlyRequest() {
        return new ChatRequest(List.of(new ChatMessage(ChatRole.USER, "hello")));
    }

    private static ChatRequest formattedRequest() {
        return new ChatRequest(
                List.of(
                        new ChatMessage(ChatRole.SYSTEM, "Translate faithfully."),
                        new ChatMessage(ChatRole.USER, "hello"),
                        new ChatMessage(ChatRole.ASSISTANT, "prior")),
                0.2,
                new ResponseFormat("draft", SCHEMA));
    }

    private static Stream<String> malformedChatBodies() {
        return Stream.of("not-json", "null", "{}", "{\"message\":{}}", "{\"message\":{\"content\":null}}");
    }

    private static Stream<String> invalidSchemas() {
        return Stream.of("{not-json", "[]");
    }

    private static Stream<String> malformedTagBodies() {
        return Stream.of("not-json", "null", "{\"models\":null}", "{\"models\":\"nope\"}", "{\"models\":[{}]}");
    }

    private static Stream<Arguments> chatHttpFailures() {
        return Stream.of(
                arguments(401, "private auth body", ErrorCode.auth),
                arguments(403, "private auth body", ErrorCode.auth),
                arguments(429, "private rate body", ErrorCode.rateLimited),
                arguments(503, "private server body", ErrorCode.upstream),
                arguments(400, "{\"error\":\"context length exceeded\"}", ErrorCode.contextWindow),
                arguments(400, "private invalid body", ErrorCode.validation));
    }

    private static URI closedEndpoint() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return URI.create("http://127.0.0.1:" + socket.getLocalPort());
        }
    }
}
