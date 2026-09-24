package ua.bookloom.llm.verify;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.util.Modules;
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
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ChatMessage;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.ChatRole;
import ua.bookloom.api.llm.ModelSelection;
import ua.bookloom.api.llm.ProviderConfig;
import ua.bookloom.api.llm.ProviderConfigs;
import ua.bookloom.api.llm.ProviderKind;
import ua.bookloom.api.llm.ProviderVerifier;
import ua.bookloom.api.llm.StageOutcome;
import ua.bookloom.api.llm.StageStatus;
import ua.bookloom.api.llm.VerificationPolicy;
import ua.bookloom.api.llm.VerificationReport;
import ua.bookloom.llm.GatedChatModel;
import ua.bookloom.llm.InMemoryProviderConfigs;
import ua.bookloom.llm.LlmModule;
import ua.bookloom.llm.gate.InferenceGate;
import ua.bookloom.llm.provider.ProviderClientFactory;
import ua.bookloom.llm.retry.RetryPolicy;

/** Proves a verifier's retry wait releases the same gate used by regular chat. */
class ProviderVerifierGateTest {

    private static final String MODELS_PATH = "/v1/models";
    private static final String CHAT_PATH = "/v1/chat/completions";
    private static final String MODELS_REPLY =
            "{\"object\":\"list\",\"data\":[{\"id\":\"test-model\",\"object\":\"model\"}]}";
    private static final String CHAT_REPLY =
            "{\"model\":\"test-model\",\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"{\\\"status\\\":\\\"ok\\\"}\"},\"finish_reason\":\"stop\"}]}";
    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-09-22T12:00:00Z"), ZoneOffset.UTC);

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
    void verify_releasesSharedGateWhileWaitingToRetrySoChatRunsFirst() throws Exception {
        final RetryWaitContext context = retryWaitContext();
        try {
            assertChatRunsBeforeVerificationRetry(context);
        } finally {
            context.allowRetry().countDown();
            context.executor().shutdownNow();
        }
    }

    private RetryWaitContext retryWaitContext() {
        final InMemoryProviderConfigs configs = new InMemoryProviderConfigs();
        final InferenceGate gate = new InferenceGate();
        final ProviderConfig config = new ProviderConfig(
                "lmstudio",
                ProviderKind.OPENAI_COMPATIBLE,
                URI.create(server.baseUrl() + "/v1"),
                Duration.ofSeconds(2),
                Duration.ofSeconds(3));
        assertThat(configs.register(config).isOk()).isTrue();
        stubRetryingDiscovery();
        server.stubFor(post(urlEqualTo(CHAT_PATH)).willReturn(okJson(CHAT_REPLY)));

        final CountDownLatch retryWaitStarted = new CountDownLatch(1);
        final CountDownLatch allowRetry = new CountDownLatch(1);
        final RetryPolicy retryPolicy =
                new RetryPolicy(FIXED_CLOCK, () -> 0.5, delay -> holdRetry(retryWaitStarted, allowRetry));
        final Injector injector = injector(configs, gate, retryPolicy);
        final ProviderVerifier verifier = injector.getInstance(ProviderVerifier.class);
        final ProviderClientFactory clients = injector.getInstance(ProviderClientFactory.class);
        final GatedChatModel chatModel = new GatedChatModel(clients.create(config), "test-model", gate, retryPolicy);
        return new RetryWaitContext(
                verifier, chatModel, retryWaitStarted, allowRetry, Executors.newSingleThreadExecutor());
    }

    private void assertChatRunsBeforeVerificationRetry(RetryWaitContext context) throws Exception {
        final Future<Result<VerificationReport>> verification = context.executor()
                .submit(() -> context.verifier()
                        .verify(new ModelSelection("lmstudio", "test-model"), VerificationPolicy.PREFLIGHT));
        assertThat(context.retryWaitStarted().await(2, TimeUnit.SECONDS)).isTrue();
        final Result<ChatResponse> chatResult = context.chatModel()
                .chat(new ChatRequest(
                        List.of(new ChatMessage(ChatRole.USER, "Run while verification is backing off."))));

        assertThat(chatResult.isOk()).isTrue();
        server.verify(2, getRequestedFor(urlEqualTo(MODELS_PATH)));
        server.verify(1, postRequestedFor(urlEqualTo(CHAT_PATH)));
        context.allowRetry().countDown();
        assertThat(Objects.requireNonNull(verification.get(3, TimeUnit.SECONDS).data(), "report")
                        .stages())
                .extracting(StageOutcome::status)
                .containsExactly(StageStatus.PASSED, StageStatus.PASSED, StageStatus.PASSED);
        server.verify(3, getRequestedFor(urlEqualTo(MODELS_PATH)));
    }

    private void stubRetryingDiscovery() {
        server.stubFor(get(urlEqualTo(MODELS_PATH))
                .inScenario("verification retry wait")
                .whenScenarioStateIs(Scenario.STARTED)
                .willReturn(okJson(MODELS_REPLY))
                .willSetStateTo("probe complete"));
        server.stubFor(get(urlEqualTo(MODELS_PATH))
                .inScenario("verification retry wait")
                .whenScenarioStateIs("probe complete")
                .willReturn(aResponse()
                        .withStatus(500)
                        .withHeader("Retry-After", "3")
                        .withBody("{}"))
                .willSetStateTo("verification retry"));
        server.stubFor(get(urlEqualTo(MODELS_PATH))
                .inScenario("verification retry wait")
                .whenScenarioStateIs("verification retry")
                .willReturn(okJson(MODELS_REPLY)));
    }

    private static void holdRetry(CountDownLatch started, CountDownLatch allowRetry) throws InterruptedException {
        started.countDown();
        if (!allowRetry.await(4, TimeUnit.SECONDS)) {
            throw new InterruptedException("test retry release timed out");
        }
    }

    private static Injector injector(ProviderConfigs configs, InferenceGate gate, RetryPolicy retryPolicy) {
        return Guice.createInjector(Modules.override(new LlmModule()).with(new AbstractModule() {
            @Override
            protected void configure() {
                bind(ProviderConfigs.class).toInstance(configs);
                bind(InferenceGate.class).toInstance(gate);
                bind(RetryPolicy.class).toInstance(retryPolicy);
            }
        }));
    }

    private record RetryWaitContext(
            ProviderVerifier verifier,
            GatedChatModel chatModel,
            CountDownLatch retryWaitStarted,
            CountDownLatch allowRetry,
            ExecutorService executor) {}
}
