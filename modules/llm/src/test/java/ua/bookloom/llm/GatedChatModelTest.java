package ua.bookloom.llm;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
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
import ua.bookloom.api.llm.ResponseFormat;
import ua.bookloom.llm.gate.InferenceGate;
import ua.bookloom.llm.http.HttpClients;
import ua.bookloom.llm.http.HttpExchange;
import ua.bookloom.llm.provider.ProviderClientFactory;
import ua.bookloom.llm.retry.RetryPolicy;

/** Exercises retries and gate placement against the real OpenAI-compatible HTTP boundary. */
class GatedChatModelTest {

    private static final String CHAT_PATH = "/v1/chat/completions";
    private static final String OLLAMA_CHAT_PATH = "/api/chat";
    private static final String MODEL_ID = "test-model";
    private static final String REPLY =
            "{\"model\":\"test-model\",\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"translated\"},\"finish_reason\":\"stop\"}]}";
    private static final String OLLAMA_REPLY =
            "{\"model\":\"test-model\",\"message\":{\"role\":\"assistant\",\"content\":\"translated\"},\"done_reason\":\"stop\"}";
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-22T12:00:00Z"), ZoneOffset.UTC);

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
    void chat_twoUpstreamFailuresThenSuccess_sendsThreeRequests() {
        server.stubFor(post(urlEqualTo(CHAT_PATH))
                .inScenario("two failures then success")
                .whenScenarioStateIs(Scenario.STARTED)
                .willReturn(aResponse().withStatus(503).withBody("{}"))
                .willSetStateTo("second failure"));
        server.stubFor(post(urlEqualTo(CHAT_PATH))
                .inScenario("two failures then success")
                .whenScenarioStateIs("second failure")
                .willReturn(aResponse().withStatus(503).withBody("{}"))
                .willSetStateTo("success"));
        server.stubFor(post(urlEqualTo(CHAT_PATH))
                .inScenario("two failures then success")
                .whenScenarioStateIs("success")
                .willReturn(aResponse().withStatus(200).withBody(REPLY)));
        final List<Duration> delays = new java.util.ArrayList<>();

        final Result<ChatResponse> result = model(delays::add).chat(request());

        assertThat(result.data()).extracting(ChatResponse::content).isEqualTo("translated");
        assertThat(delays).containsExactly(Duration.ofMillis(500), Duration.ofSeconds(1));
        server.verify(3, postRequestedFor(urlEqualTo(CHAT_PATH)));
    }

    @Test
    void chat_threeUpstreamFailures_returnsLastTypedErrorAfterThreeRequests() {
        server.stubFor(post(urlEqualTo(CHAT_PATH))
                .willReturn(aResponse().withStatus(503).withBody("{}")));

        final Result<ChatResponse> result = model(ignored -> {}).chat(request());

        assertThat(Objects.requireNonNull(result.error(), "error").code()).isEqualTo(ErrorCode.upstream);
        server.verify(3, postRequestedFor(urlEqualTo(CHAT_PATH)));
    }

    @Test
    void chat_authenticationFailure_isNotRetried() {
        server.stubFor(post(urlEqualTo(CHAT_PATH))
                .willReturn(aResponse().withStatus(401).withBody("{}")));

        final Result<ChatResponse> result = model(ignored -> {}).chat(request());

        assertThat(Objects.requireNonNull(result.error(), "error").code()).isEqualTo(ErrorCode.auth);
        server.verify(1, postRequestedFor(urlEqualTo(CHAT_PATH)));
    }

    // An explicitly unsupported schema is a capability downgrade, not a transport retry or a backoff.
    @Test
    void chat_structuredOutputRejected_retriesOnceWithoutResponseFormat() {
        server.stubFor(post(urlEqualTo(CHAT_PATH))
                .inScenario("format capability")
                .whenScenarioStateIs(Scenario.STARTED)
                .willReturn(aResponse().withStatus(400).withBody("{\"error\":\"response_format is unsupported\"}"))
                .willSetStateTo("format removed"));
        server.stubFor(post(urlEqualTo(CHAT_PATH))
                .inScenario("format capability")
                .whenScenarioStateIs("format removed")
                .willReturn(aResponse().withStatus(200).withBody(REPLY)));
        final List<Duration> delays = new java.util.ArrayList<>();

        final Result<ChatResponse> result = model(delays::add).chat(structuredRequest());

        assertThat(result.isOk()).isTrue();
        assertThat(delays).isEmpty();
        assertThat(server.getAllServeEvents())
                .extracting(event -> event.getRequest().getBodyAsString())
                .anySatisfy(body -> assertThat(body).contains("response_format"))
                .anySatisfy(body -> assertThat(body).doesNotContain("response_format"));
    }

    // A normal validation failure must retain all controls and return after its first provider request.
    @Test
    void chat_unrelatedValidationFailure_doesNotDowngradeControls() {
        server.stubFor(post(urlEqualTo(CHAT_PATH))
                .willReturn(aResponse().withStatus(400).withBody("{\"error\":\"temperature is invalid\"}")));

        final Result<ChatResponse> result = model(ignored -> {}).chat(structuredRequest());

        assertThat(Objects.requireNonNull(result.error(), "error").code()).isEqualTo(ErrorCode.validation);
        server.verify(1, postRequestedFor(urlEqualTo(CHAT_PATH)));
    }

    // A native thinking-control rejection retries once immediately without think:false.
    @Test
    void chat_ollamaThinkRejected_retriesOnceWithoutThinkingControl() {
        server.stubFor(post(urlEqualTo(OLLAMA_CHAT_PATH))
                .inScenario("think capability")
                .whenScenarioStateIs(Scenario.STARTED)
                .willReturn(aResponse().withStatus(400).withBody("{\"error\":\"unknown field think\"}"))
                .willSetStateTo("think removed"));
        server.stubFor(post(urlEqualTo(OLLAMA_CHAT_PATH))
                .inScenario("think capability")
                .whenScenarioStateIs("think removed")
                .willReturn(aResponse().withStatus(200).withBody(OLLAMA_REPLY)));
        final ChatRequest request =
                new ChatRequest(List.of(new ChatMessage(ChatRole.USER, "Translate this.")), null, null, false);

        final Result<ChatResponse> result = ollamaModel(ignored -> {}).chat(request);

        assertThat(result.isOk()).isTrue();
        assertThat(server.getAllServeEvents())
                .extracting(event -> event.getRequest().getBodyAsString())
                .anySatisfy(body -> assertThat(body).contains("\"think\":false"))
                .anySatisfy(body -> assertThat(body).doesNotContain("\"think\""));
    }

    @Test
    void chat_rateLimitPassesRetryAfterDelayToSleeper() {
        server.stubFor(post(urlEqualTo(CHAT_PATH))
                .inScenario("rate limit")
                .whenScenarioStateIs(Scenario.STARTED)
                .willReturn(aResponse()
                        .withStatus(429)
                        .withHeader("Retry-After", "2")
                        .withBody("{}"))
                .willSetStateTo("ready"));
        server.stubFor(post(urlEqualTo(CHAT_PATH))
                .inScenario("rate limit")
                .whenScenarioStateIs("ready")
                .willReturn(aResponse().withStatus(200).withBody(REPLY)));
        final List<Duration> delays = new java.util.ArrayList<>();

        final Result<ChatResponse> result = model(delays::add).chat(request());

        assertThat(result.isOk()).isTrue();
        assertThat(delays).containsExactly(Duration.ofSeconds(2));
        server.verify(2, postRequestedFor(urlEqualTo(CHAT_PATH)));
    }

    @Test
    void chat_concurrentCallsNeverOverlapAtProvider() throws Exception {
        server.stubFor(post(urlEqualTo(CHAT_PATH))
                .willReturn(aResponse().withStatus(200).withFixedDelay(450).withBody(REPLY)));
        final GatedChatModel model = model(ignored -> {});
        final ExecutorService executor = Executors.newFixedThreadPool(2);
        final CountDownLatch ready = new CountDownLatch(2);
        final CountDownLatch start = new CountDownLatch(1);
        try {
            final Future<Result<ChatResponse>> first = executor.submit(() -> concurrentChat(model, ready, start));
            final Future<Result<ChatResponse>> second = executor.submit(() -> concurrentChat(model, ready, start));
            assertThat(ready.await(2, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            awaitRequestCount(1);
            Thread.sleep(100);
            assertThat(server.getAllServeEvents()).hasSize(1);
            assertThat(first.get(3, TimeUnit.SECONDS).isOk()).isTrue();
            assertThat(second.get(3, TimeUnit.SECONDS).isOk()).isTrue();
            server.verify(2, postRequestedFor(urlEqualTo(CHAT_PATH)));
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void chat_secondCallReachesProviderWhileFirstCallSleeps() throws Exception {
        stubFirstCallRetryScenario();
        final CountDownLatch sleepStarted = new CountDownLatch(1);
        final CountDownLatch allowRetry = new CountDownLatch(1);
        final ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            final GatedChatModel model = model(delay -> {
                sleepStarted.countDown();
                if (!allowRetry.await(4, TimeUnit.SECONDS)) {
                    throw new InterruptedException("test release timed out");
                }
            });
            final Future<Result<ChatResponse>> first = executor.submit(() -> model.chat(request()));
            assertThat(sleepStarted.await(2, TimeUnit.SECONDS)).isTrue();

            final Result<ChatResponse> second = model.chat(request());

            assertThat(second.isOk()).isTrue();
            server.verify(2, postRequestedFor(urlEqualTo(CHAT_PATH)));
            allowRetry.countDown();
            assertThat(first.get(2, TimeUnit.SECONDS).isOk()).isTrue();
            server.verify(3, postRequestedFor(urlEqualTo(CHAT_PATH)));
        } finally {
            allowRetry.countDown();
            executor.shutdownNow();
        }
    }

    private void stubFirstCallRetryScenario() {
        server.stubFor(post(urlEqualTo(CHAT_PATH))
                .inScenario("first call retry")
                .whenScenarioStateIs(Scenario.STARTED)
                .willReturn(aResponse()
                        .withStatus(429)
                        .withHeader("Retry-After", "3")
                        .withBody("{}"))
                .willSetStateTo("first call retrying"));
        server.stubFor(post(urlEqualTo(CHAT_PATH))
                .inScenario("first call retry")
                .whenScenarioStateIs("first call retrying")
                .willReturn(aResponse().withStatus(200).withBody(REPLY))
                .willSetStateTo("first call done"));
        server.stubFor(post(urlEqualTo(CHAT_PATH))
                .inScenario("first call retry")
                .whenScenarioStateIs("first call done")
                .willReturn(aResponse().withStatus(200).withBody(REPLY)));
    }

    private GatedChatModel model(RetryPolicy.Sleeper sleeper) {
        final ProviderConfig config = new ProviderConfig(
                "test",
                ProviderKind.OPENAI_COMPATIBLE,
                URI.create(server.baseUrl() + "/v1"),
                Duration.ofSeconds(2),
                Duration.ofSeconds(3));
        final ProviderClientFactory clients =
                new ProviderClientFactory(new HttpExchange(new HttpClients()), new LlmModule().objectMapper());
        final RetryPolicy retryPolicy = new RetryPolicy(CLOCK, () -> 0.5, sleeper);
        return new GatedChatModel(clients.create(config), MODEL_ID, new InferenceGate(), retryPolicy);
    }

    private GatedChatModel ollamaModel(RetryPolicy.Sleeper sleeper) {
        final ProviderConfig config = new ProviderConfig(
                "ollama",
                ProviderKind.OLLAMA,
                URI.create(server.baseUrl()),
                Duration.ofSeconds(2),
                Duration.ofSeconds(3));
        final ProviderClientFactory clients =
                new ProviderClientFactory(new HttpExchange(new HttpClients()), new LlmModule().objectMapper());
        final RetryPolicy retryPolicy = new RetryPolicy(CLOCK, () -> 0.5, sleeper);
        return new GatedChatModel(clients.create(config), MODEL_ID, new InferenceGate(), retryPolicy);
    }

    private static Result<ChatResponse> concurrentChat(GatedChatModel model, CountDownLatch ready, CountDownLatch start)
            throws InterruptedException {
        ready.countDown();
        if (!start.await(2, TimeUnit.SECONDS)) {
            return Result.err(AppError.of(ErrorCode.cancelled, "Cancelled", "Test start was not released."));
        }
        return model.chat(request());
    }

    private void awaitRequestCount(int expected) throws InterruptedException {
        final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (System.nanoTime() < deadline && server.getAllServeEvents().size() < expected) {
            Thread.sleep(10);
        }
        assertThat(server.getAllServeEvents()).hasSize(expected);
    }

    private static ChatRequest request() {
        return new ChatRequest(List.of(new ChatMessage(ChatRole.USER, "Translate this.")));
    }

    private static ChatRequest structuredRequest() {
        return new ChatRequest(
                List.of(new ChatMessage(ChatRole.USER, "Translate this.")),
                0.2,
                new ResponseFormat("draft", "{\"type\":\"object\"}"),
                false);
    }
}
