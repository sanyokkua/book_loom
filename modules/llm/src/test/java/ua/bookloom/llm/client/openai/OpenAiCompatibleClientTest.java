package ua.bookloom.llm.client.openai;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
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
import org.slf4j.LoggerFactory;
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

/** Verifies the OpenAI-compatible dialect at the real HTTP boundary. */
class OpenAiCompatibleClientTest {

    private static final String MODEL_ID = "google/gemma-4-e4b";
    private static final String CHAT_PATH = "/v1/chat/completions";
    private static final String MODELS_PATH = "/v1/models";
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
    void chat_fullRequestUsesStrictSchemaAndOmitsReasoningAndAuthorization() {
        stubChat("""
                {"model":"google/gemma-4-e4b","choices":[{"message":{"content":"reply"},"finish_reason":"stop"}]}
                """);

        final ChatResponse response = sendChat(formattedRequest());

        assertThat(response.content()).isEqualTo("reply");
        server.verify(postRequestedFor(urlEqualTo(CHAT_PATH))
                .withRequestBody(equalToJson("""
                        {
                          "model":"google/gemma-4-e4b",
                          "messages":[{"role":"system","content":"Translate faithfully."},{"role":"user","content":"hello"},{"role":"assistant","content":"prior"}],
                          "stream":false,
                          "temperature":0.2,
                          "response_format":{"type":"json_schema","json_schema":{"name":"draft","strict":true,"schema":{"type":"object","properties":{"segments":{"type":"array"}},"required":["segments"]}}}
                        }
                        """))
                .withoutHeader("Authorization"));
    }

    @Test
    void chat_withoutOptionalSettings_omitsTemperatureAndResponseFormat() {
        stubChat("""
                {"model":"google/gemma-4-e4b","choices":[{"message":{"content":"reply"},"finish_reason":"stop"}]}
                """);

        sendChat(messageOnlyRequest());

        server.verify(postRequestedFor(urlEqualTo(CHAT_PATH))
                .withRequestBody(equalToJson("""
                        {"model":"google/gemma-4-e4b","messages":[{"role":"user","content":"hello"}],"stream":false}
                        """))
                .withoutHeader("Authorization"));
    }

    @ParameterizedTest
    @CsvSource({"stop, STOP", "length, LENGTH", "content_filter, OTHER", "tool_calls, OTHER"})
    void chat_finishReason_mapsStopLengthAndOther(String wireReason, FinishReason expected) {
        stubChat("""
                {"model":"google/gemma-4-e4b","choices":[{"message":{"content":"reply"},"finish_reason":"%s"}]}
                """.formatted(wireReason));

        assertThat(sendChat(messageOnlyRequest()).finishReason()).isEqualTo(expected);
    }

    @Test
    void chat_missingFinishReason_returnsOther() {
        stubChat("""
                {"model":"google/gemma-4-e4b","choices":[{"message":{"content":"reply"}}]}
                """);

        assertThat(sendChat(messageOnlyRequest()).finishReason()).isEqualTo(FinishReason.OTHER);
    }

    @Test
    void chat_reasoningAndToolCallsAreIgnored() {
        stubChat("""
                {"model":"google/gemma-4-e4b","choices":[{"message":{"content":"<think>private reasoning</think>the answer","reasoning_content":"private reasoning","tool_calls":[{"id":"call_1"}]},"finish_reason":"stop"},{"message":{"content":"second choice"},"finish_reason":"stop"}]}
                """);

        assertThat(sendChat(messageOnlyRequest())).isEqualTo(new ChatResponse("the answer", FinishReason.STOP));
    }

    @Test
    void chat_reasoningOnlyContent_returnsSuccessfulEmptyReplyWithStopFinish() {
        stubChat("""
                {"model":"google/gemma-4-e4b","choices":[{"message":{"content":"<think>private reasoning</think>"},"finish_reason":"stop"}]}
                """);

        assertThat(sendChat(messageOnlyRequest())).isEqualTo(new ChatResponse("", FinishReason.STOP));
    }

    @Test
    void chat_modelMismatchUsesReplyAndEmitsOneWarnNamingBothModels() {
        stubChat("""
                {"model":"google/gemma-4-e4b","choices":[{"message":{"content":"reply"},"finish_reason":"stop"}]}
                """);
        final Logger logger = (Logger) LoggerFactory.getLogger(OpenAiCompatibleClient.class);
        final ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.setContext((LoggerContext) LoggerFactory.getILoggerFactory());
        appender.start();
        logger.addAppender(appender);

        try {
            final Result<ChatResponse> result = client().chat("google/gemma-4-e4b-typo", messageOnlyRequest())
                    .result();
            assertThat(result.isOk())
                    .as("OpenAI-compatible chat result: " + result.error())
                    .isTrue();
            final ChatResponse response = Objects.requireNonNull(result.data(), "chat response");

            assertThat(response.content()).isEqualTo("reply");
            assertThat(appender.list)
                    .filteredOn(event -> event.getLevel() == Level.WARN)
                    .singleElement()
                    .satisfies(event -> assertThat(event.getFormattedMessage())
                            .contains("google/gemma-4-e4b-typo", "google/gemma-4-e4b"));
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    @Test
    void chat_blankRawContent_returnsEmptyCompletion() {
        stubChat("""
                {"model":"google/gemma-4-e4b","choices":[{"message":{"content":"  \\n"},"finish_reason":"stop"}]}
                """);

        assertChatError(messageOnlyRequest(), ErrorCode.emptyCompletion);
    }

    @ParameterizedTest
    @MethodSource("malformedChatBodies")
    void chat_malformedResponse_returnsInternal(String body) {
        stubChatStatus(200, body);

        assertChatError(messageOnlyRequest(), ErrorCode.internal);
    }

    @ParameterizedTest
    @MethodSource("invalidSchemas")
    void chat_invalidSchema_returnsValidationWithoutSending(String schema) {
        final ChatRequest request = new ChatRequest(
                List.of(new ChatMessage(ChatRole.USER, "hello")), 0.2, new ResponseFormat("draft", schema));

        assertChatError(request, ErrorCode.validation);

        server.verify(0, postRequestedFor(urlEqualTo(CHAT_PATH)));
    }

    @Test
    void listModels_validListingReturnsIdsInServerOrder() {
        server.stubFor(get(urlEqualTo(MODELS_PATH)).willReturn(okJson("""
                {"object":"list","data":[{"id":"google/gemma-4-e4b"},{"id":"llama-3.2"}]}
                """)));

        final Result<List<ModelInfo>> result = client().listModels().result();

        assertThat(result.isOk()).isTrue();
        assertThat(Objects.requireNonNull(result.data(), "models"))
                .containsExactly(new ModelInfo("google/gemma-4-e4b"), new ModelInfo("llama-3.2"));
    }

    @Test
    void listModels_emptyListingSucceeds() {
        server.stubFor(get(urlEqualTo(MODELS_PATH)).willReturn(okJson("{\"data\":[]}")));

        final Result<List<ModelInfo>> result = client().listModels().result();

        assertThat(result.isOk()).isTrue();
        assertThat(Objects.requireNonNull(result.data(), "models")).isEmpty();
    }

    @ParameterizedTest
    @MethodSource("malformedListingBodies")
    void listModels_malformedListingReturnsDiscoveryFailed(String body) {
        server.stubFor(get(urlEqualTo(MODELS_PATH)).willReturn(okJson(body)));

        assertThat(Objects.requireNonNull(client().listModels().result().error(), "error")
                        .code())
                .isEqualTo(ErrorCode.discoveryFailed);
    }

    @Test
    void listModels_serverFailureMapsToDiscoveryFailed() {
        server.stubFor(get(urlEqualTo(MODELS_PATH))
                .willReturn(aResponse().withStatus(503).withBody("private")));

        assertThat(Objects.requireNonNull(client().listModels().result().error(), "error")
                        .code())
                .isEqualTo(ErrorCode.discoveryFailed);
    }

    @Test
    void listModels_authFailurePreservesAuth() {
        server.stubFor(get(urlEqualTo(MODELS_PATH))
                .willReturn(aResponse().withStatus(401).withBody("private")));

        assertThat(Objects.requireNonNull(client().listModels().result().error(), "error")
                        .code())
                .isEqualTo(ErrorCode.auth);
    }

    @Test
    void listModels_notFoundMapsToDiscoveryFailed() {
        server.stubFor(get(urlEqualTo(MODELS_PATH)).willReturn(aResponse().withStatus(404)));

        assertThat(Objects.requireNonNull(client().listModels().result().error(), "error")
                        .code())
                .isEqualTo(ErrorCode.discoveryFailed);
    }

    @Test
    void listModels_timeoutPreservesTimeout() {
        server.stubFor(get(urlEqualTo(MODELS_PATH))
                .willReturn(aResponse().withStatus(200).withFixedDelay(1000).withBody("{\"data\":[]}")));

        final Result<List<ModelInfo>> result = client(URI.create(server.baseUrl() + "/v1"), Duration.ofMillis(100))
                .listModels()
                .result();

        assertThat(Objects.requireNonNull(result.error(), "error").code()).isEqualTo(ErrorCode.timeout);
    }

    @Test
    void listModels_closedEndpointPreservesUnreachable() throws IOException {
        final Result<List<ModelInfo>> result =
                client(closedEndpoint(), Duration.ofSeconds(2)).listModels().result();

        assertThat(Objects.requireNonNull(result.error(), "error").code()).isEqualTo(ErrorCode.unreachable);
    }

    @Test
    void listModels_interruptedRequestPreservesCancellation() {
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
    void chat_httpFailuresReturnTypedErrorsWithoutBodies(int status, String body, ErrorCode expected) {
        stubChatStatus(status, body);

        final Result<ChatResponse> result =
                client().chat(MODEL_ID, messageOnlyRequest()).result();
        final AppError error = Objects.requireNonNull(result.error(), "error");

        assertThat(error.code()).isEqualTo(expected);
        assertThat(error.details()).doesNotContain(body);
    }

    private void stubChat(String body) {
        stubChatStatus(200, body);
    }

    private void stubChatStatus(int status, String body) {
        server.stubFor(post(urlEqualTo(CHAT_PATH))
                .willReturn(aResponse().withStatus(status).withBody(body)));
    }

    private ChatResponse sendChat(ChatRequest request) {
        final Result<ChatResponse> result = client().chat(MODEL_ID, request).result();
        assertThat(result.isOk())
                .as("OpenAI-compatible chat result: " + result.error())
                .isTrue();
        return Objects.requireNonNull(result.data(), "chat response");
    }

    private void assertChatError(ChatRequest request, ErrorCode expected) {
        final Result<ChatResponse> result = client().chat(MODEL_ID, request).result();
        assertThat(Objects.requireNonNull(result.error(), "error").code()).isEqualTo(expected);
    }

    private OpenAiCompatibleClient client() {
        return client(URI.create(server.baseUrl() + "/v1"), Duration.ofSeconds(2));
    }

    private OpenAiCompatibleClient client(URI endpoint, Duration requestTimeout) {
        final ProviderConfig config = new ProviderConfig(
                "lmstudio", ProviderKind.OPENAI_COMPATIBLE, endpoint, Duration.ofSeconds(2), requestTimeout);
        return new OpenAiCompatibleClient(config, new HttpExchange(new HttpClients()), new LlmModule().objectMapper());
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
                new ResponseFormat("draft", SCHEMA),
                false);
    }

    private static Stream<String> malformedChatBodies() {
        return Stream.of(
                "not-json",
                "<html>proxy error</html>",
                "null",
                "{}",
                "{\"choices\":[]}",
                "{\"choices\":[{\"message\":{}}]}");
    }

    private static Stream<String> invalidSchemas() {
        return Stream.of("{not-json", "[]");
    }

    private static Stream<String> malformedListingBodies() {
        return Stream.of("not-json", "null", "{}", "{\"data\":null}", "{\"data\":\"nope\"}", "{\"data\":[{}]}");
    }

    private static Stream<Arguments> chatHttpFailures() {
        return Stream.of(
                arguments(401, "private auth body", ErrorCode.auth),
                arguments(403, "private auth body", ErrorCode.auth),
                arguments(404, "private not found body", ErrorCode.modelNotFound),
                arguments(429, "private rate body", ErrorCode.rateLimited),
                arguments(503, "private server body", ErrorCode.upstream),
                arguments(400, "context length exceeded", ErrorCode.contextWindow),
                arguments(400, "private invalid body", ErrorCode.validation));
    }

    private static URI closedEndpoint() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return URI.create("http://127.0.0.1:" + socket.getLocalPort() + "/v1");
        }
    }
}
