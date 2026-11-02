package ua.bookloom.llm;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.client.ScenarioMappingBuilder;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.FailureReason;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ChatMessage;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.ChatRole;
import ua.bookloom.api.llm.ProviderConfig;
import ua.bookloom.api.llm.ProviderKind;
import ua.bookloom.api.llm.ReasoningLevel;
import ua.bookloom.api.llm.SamplingParams;
import ua.bookloom.api.pipeline.CallKind;
import ua.bookloom.llm.gate.InferenceGate;
import ua.bookloom.llm.http.HttpClients;
import ua.bookloom.llm.http.HttpExchange;
import ua.bookloom.llm.provider.ProviderClientFactory;
import ua.bookloom.llm.retry.RetryPolicy;

/**
 * The matched error row decides whether a failed call is sent again: a stopped reply or a crashed runner once more
 * after a state check, a looping model once more with a new seed, and a model that does not fit or is still loading
 * never. Only the chat path's POSTs are counted; the state check's GET is left unstubbed, and a 404 reads as reachable.
 */
class GatedChatModelRetryRulesTest {

    private static final String OPENAI = "OPENAI_COMPATIBLE, /v1, /v1/chat/completions, /v1/models";
    private static final String OLLAMA = "OLLAMA, '', /api/chat, /api/version";
    private static final String OPENAI_REPLY = "{\"model\":\"m\",\"choices\":[{\"message\":{\"role\":\"assistant\","
            + "\"content\":\"ok\"},\"finish_reason\":\"stop\"}]}";
    private static final String OLLAMA_REPLY =
            "{\"model\":\"m\",\"message\":{\"content\":\"ok\"},\"done\":true,\"done_reason\":\"stop\"}";
    private static final String REPEAT_LIMIT = "{\"error\":\"token repeat limit reached\"}";
    private static final String TERMINATED = "{\"error\":\"terminated\"}";
    private static final String RUNNER_CRASHED =
            "{\"error\":\"model runner has unexpectedly stopped, this may be due to resource limitations\"}";
    private static final String OUT_OF_MEMORY =
            "{\"error\":\"model requires 18.2 GiB but only 9.6 GiB are available\"}";
    private static final String MODEL_LOADING = "{\"error\":{\"message\":\"Loading model\"}}";
    private static final Pattern SEED = Pattern.compile("\"seed\"\\s*:\\s*(\\d+)");
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-29T12:00:00Z"), ZoneOffset.UTC);

    private final WireMockServer server =
            new WireMockServer(WireMockConfiguration.options().dynamicPort().bindAddress("127.0.0.1"));

    @BeforeEach
    void start() {
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop();
    }

    @ParameterizedTest
    @CsvSource({OPENAI, OLLAMA})
    void chat_repeatLimitThenAnswer_succeedsAfterTwoRequestsAndTheSecondCarriesANewSeed(
            final ProviderKind kind, final String basePath, final String chatPath, final String probePath) {
        stubChat(chatPath, List.of(failure(500, REPEAT_LIMIT), answer(kind)));

        final Result<ChatResponse> result = model(kind, basePath).chat(request());

        assertThat(result.isOk()).isTrue();
        final List<String> bodies = bodies(chatPath);
        assertThat(bodies).hasSize(2);
        assertThat(seedOf(bodies.get(1))).isPresent().isNotEqualTo(seedOf(bodies.get(0)));
    }

    @ParameterizedTest
    @CsvSource({OPENAI, OLLAMA})
    void chat_repeatLimitTwice_isUpstreamRepeatLimitAfterExactlyTwoRequests(
            final ProviderKind kind, final String basePath, final String chatPath, final String probePath) {
        stubChat(chatPath, List.of(failure(500, REPEAT_LIMIT), failure(500, REPEAT_LIMIT), answer(kind)));

        final Result<ChatResponse> result = model(kind, basePath).chat(request());

        assertFailed(result, ErrorCode.upstream, FailureReason.REPEAT_LIMIT);
        assertThat(chatRequests(chatPath)).isEqualTo(2);
    }

    @ParameterizedTest
    @CsvSource({OPENAI, OLLAMA})
    void chat_terminatedAs400ThenAnswer_succeedsAfterTwoRequests(
            final ProviderKind kind, final String basePath, final String chatPath, final String probePath) {
        stubChat(chatPath, List.of(failure(400, TERMINATED), answer(kind)));

        final Result<ChatResponse> result = model(kind, basePath).chat(request());

        assertThat(result.isOk()).isTrue();
        assertThat(chatRequests(chatPath)).isEqualTo(2);
    }

    @ParameterizedTest
    @CsvSource({
        "OPENAI_COMPATIBLE, /v1, /v1/chat/completions, 400",
        "OPENAI_COMPATIBLE, /v1, /v1/chat/completions, 200",
        "OLLAMA, '', /api/chat, 400"
    })
    void chat_terminatedTwice_isUpstreamTerminatedAfterExactlyTwoRequests(
            final ProviderKind kind, final String basePath, final String chatPath, final int status) {
        stubChat(chatPath, List.of(failure(status, TERMINATED), failure(status, TERMINATED), answer(kind)));

        final Result<ChatResponse> result = model(kind, basePath).chat(request());

        assertFailed(result, ErrorCode.upstream, FailureReason.TERMINATED);
        assertThat(chatRequests(chatPath)).isEqualTo(2);
    }

    @ParameterizedTest
    @CsvSource({OPENAI, OLLAMA})
    void chat_runnerCrashedTwice_isRunnerCrashedAfterExactlyTwoRequests(
            final ProviderKind kind, final String basePath, final String chatPath, final String probePath) {
        stubChat(chatPath, List.of(failure(500, RUNNER_CRASHED), failure(500, RUNNER_CRASHED), answer(kind)));

        final Result<ChatResponse> result = model(kind, basePath).chat(request());

        assertFailed(result, ErrorCode.upstream, FailureReason.RUNNER_CRASHED);
        assertThat(chatRequests(chatPath)).isEqualTo(2);
    }

    @ParameterizedTest
    @CsvSource({OPENAI, OLLAMA})
    void chat_outOfMemory_isModelUnavailableAfterOneRequest(
            final ProviderKind kind, final String basePath, final String chatPath, final String probePath) {
        stubChat(chatPath, List.of(failure(500, OUT_OF_MEMORY), answer(kind)));

        final Result<ChatResponse> result = model(kind, basePath).chat(request());

        assertFailed(result, ErrorCode.modelUnavailable, FailureReason.OUT_OF_MEMORY);
        assertThat(chatRequests(chatPath)).isEqualTo(1);
    }

    // A server that cannot even answer the state check is down: sending the body again would only fail the same way,
    // so the failure goes to the run, which waits for it as an outage.
    @ParameterizedTest
    @CsvSource({OPENAI, OLLAMA})
    void chat_terminatedWhileStateCheckFails_isUpstreamAfterOneRequest(
            final ProviderKind kind, final String basePath, final String chatPath, final String probePath) {
        stubChat(chatPath, List.of(failure(400, TERMINATED), answer(kind)));
        server.stubFor(get(urlEqualTo(probePath)).willReturn(aResponse().withStatus(503)));

        final Result<ChatResponse> result = model(kind, basePath).chat(request());

        assertFailed(result, ErrorCode.upstream, FailureReason.TERMINATED);
        assertThat(chatRequests(chatPath)).isEqualTo(1);
    }

    @ParameterizedTest
    @CsvSource({OPENAI, OLLAMA})
    void chat_modelLoading503_isModelUnavailableAfterOneRequest(
            final ProviderKind kind, final String basePath, final String chatPath, final String probePath) {
        stubChat(chatPath, List.of(failure(503, MODEL_LOADING), answer(kind)));

        final Result<ChatResponse> result = model(kind, basePath).chat(request());

        assertFailed(result, ErrorCode.modelUnavailable, FailureReason.MODEL_LOADING);
        assertThat(chatRequests(chatPath)).isEqualTo(1);
    }

    private static void assertFailed(
            final Result<ChatResponse> result, final ErrorCode code, final FailureReason reason) {
        assertThat(result.isErr()).isTrue();
        final AppError error = result.error();
        assertThat(error).extracting(AppError::code).isEqualTo(code);
        assertThat(FailureReason.in(error == null ? null : error.details())).contains(reason);
    }

    // Answers the n-th chat request with the n-th reply; the last reply answers every later one.
    private void stubChat(final String chatPath, final List<ResponseDefinitionBuilder> replies) {
        for (int index = 0; index < replies.size(); index++) {
            final ScenarioMappingBuilder mapping = post(urlEqualTo(chatPath))
                    .inScenario("chat")
                    .whenScenarioStateIs(index == 0 ? Scenario.STARTED : "request-" + index)
                    .willReturn(replies.get(index));
            server.stubFor(index + 1 < replies.size() ? mapping.willSetStateTo("request-" + (index + 1)) : mapping);
        }
    }

    private static ResponseDefinitionBuilder failure(final int status, final String body) {
        return aResponse().withStatus(status).withBody(body);
    }

    private static ResponseDefinitionBuilder answer(final ProviderKind kind) {
        return aResponse().withStatus(200).withBody(kind == ProviderKind.OLLAMA ? OLLAMA_REPLY : OPENAI_REPLY);
    }

    private int chatRequests(final String chatPath) {
        return server.findAll(postRequestedFor(urlEqualTo(chatPath))).size();
    }

    private List<String> bodies(final String chatPath) {
        return server.findAll(postRequestedFor(urlEqualTo(chatPath))).stream()
                .map(LoggedRequest::getBodyAsString)
                .toList();
    }

    private static Optional<String> seedOf(final String body) {
        final Matcher matcher = SEED.matcher(body);
        return matcher.find() ? Optional.of(matcher.group(1)) : Optional.empty();
    }

    private static ChatRequest request() {
        return new ChatRequest(
                List.of(new ChatMessage(ChatRole.USER, "Translate this.")),
                0.2,
                null,
                ReasoningLevel.OFF,
                8192,
                null,
                400,
                null,
                CallKind.DRAFT,
                new SamplingParams(null, null, null, null),
                null,
                null,
                null,
                false);
    }

    private GatedChatModel model(final ProviderKind kind, final String basePath) {
        final ProviderConfig config = new ProviderConfig(
                "test", kind, URI.create(server.baseUrl() + basePath), Duration.ofSeconds(5), Duration.ofSeconds(5));
        final ProviderClientFactory clients = new ProviderClientFactory(
                new HttpExchange(new HttpClients()), new LlmModule().objectMapper(), System::nanoTime);
        return new GatedChatModel(
                clients.create(config),
                "test-model",
                new InferenceGate(),
                new RetryPolicy(CLOCK, () -> 0.5, ignored -> {}));
    }
}
