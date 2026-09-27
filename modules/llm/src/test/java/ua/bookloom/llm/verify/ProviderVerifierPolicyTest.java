package ua.bookloom.llm.verify;

import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.util.Modules;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Iterator;
import java.util.Objects;
import java.util.function.LongSupplier;
import java.util.stream.LongStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ModelSelection;
import ua.bookloom.api.llm.ProviderConfig;
import ua.bookloom.api.llm.ProviderConfigs;
import ua.bookloom.api.llm.ProviderKind;
import ua.bookloom.api.llm.ProviderVerifier;
import ua.bookloom.api.llm.StageOutcome;
import ua.bookloom.api.llm.StageStatus;
import ua.bookloom.api.llm.VerificationPolicy;
import ua.bookloom.api.llm.VerificationReport;
import ua.bookloom.api.llm.VerificationStage;
import ua.bookloom.llm.InMemoryProviderConfigs;
import ua.bookloom.llm.LlmModule;
import ua.bookloom.llm.gate.InferenceGate;
import ua.bookloom.llm.retry.RetryPolicy;

/** Exercises the verifier's measured stage durations, models count, and the two shorter cumulative policies. */
class ProviderVerifierPolicyTest {

    private static final String OLLAMA_VERSION_PATH = "/api/version";
    private static final String OLLAMA_TAGS_PATH = "/api/tags";
    private static final String OLLAMA_CHAT_PATH = "/api/chat";
    private static final String OPENAI_MODELS_PATH = "/v1/models";
    private static final String OPENAI_CHAT_PATH = "/v1/chat/completions";
    private static final String MODEL_ID = "test-model";
    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-09-22T12:00:00Z"), ZoneOffset.UTC);

    private final WireMockServer server =
            new WireMockServer(WireMockConfiguration.options().dynamicPort());
    private InMemoryProviderConfigs configs;

    @BeforeEach
    void startServer() {
        server.start();
        configs = new InMemoryProviderConfigs();
    }

    @AfterEach
    void stopServer() {
        server.stop();
    }

    // Every stage that runs reports its own measured duration, and the models stage reports its listed count.
    @Test
    void verify_fullPolicy_reportsEachStagesElapsedTimeAndModelsCount() {
        registerProvider("ollama", ProviderKind.OLLAMA, "");
        server.stubFor(get(urlEqualTo(OLLAMA_VERSION_PATH)).willReturn(okJson("{\"version\":\"0.6.0\"}")));
        server.stubFor(get(urlEqualTo(OLLAMA_TAGS_PATH))
                .willReturn(okJson("{\"models\":[{\"name\":\"gemma4:e4b-mlx\"},{\"name\":\"a\"},{\"name\":\"b\"}]}")));
        server.stubFor(
                post(urlEqualTo(OLLAMA_CHAT_PATH))
                        .withRequestBody(equalToJson("""
                        {"model":"test-model","messages":[{"role":"user","content":"Return exactly one JSON object: {\\"status\\":\\"ok\\"}."}],"stream":false,"format":{"type":"object","properties":{"status":{"const":"ok"}},"required":["status"],"additionalProperties":false},"think":false}
                        """))
                        .willReturn(
                                okJson(
                                        "{\"model\":\"test-model\",\"message\":{\"role\":\"assistant\",\"content\":\"{\\\"status\\\":\\\"ok\\\"}\"},\"done\":true,\"done_reason\":\"stop\"}")));

        final VerificationReport report = report(
                verifier(scriptedNanoTime(0L, 41_000_000L, 41_000_000L, 41_500_000L, 41_500_000L, 1_241_500_000L))
                        .verify(new ModelSelection("ollama", "gemma4:e4b-mlx"), VerificationPolicy.FULL));

        assertThat(report.stages().get(0).elapsed()).isEqualTo(Duration.ofMillis(41));
        assertThat(report.stages().get(1).count()).isEqualTo(3);
        assertThat(report.stages().get(2).elapsed()).isEqualTo(Duration.ofMillis(1200));
    }

    // A connection check runs only the connection stage.
    @Test
    void verify_connectionPolicy_runsOnlyTheConnectionStage() {
        registerProvider("lmstudio", ProviderKind.OPENAI_COMPATIBLE, "/v1");
        server.stubFor(get(urlEqualTo(OPENAI_MODELS_PATH)).willReturn(okJson("""
                {"object":"list","data":[]}
                """)));

        final VerificationReport report = report(verifier(scriptedNanoTime(0L, 12_000_000L))
                .verify(new ModelSelection("lmstudio", MODEL_ID), VerificationPolicy.CONNECTION));

        assertThat(report.stages()).extracting(StageOutcome::stage).containsExactly(VerificationStage.CONNECTION);
        assertThat(report.stages().getFirst().elapsed()).isEqualTo(Duration.ofMillis(12));
        server.verify(0, postRequestedFor(urlEqualTo(OPENAI_CHAT_PATH)));
    }

    // A models check runs the connection stage first, then models, and never reaches inference.
    @Test
    void verify_connectionAndModelsPolicy_stopsAfterModelsWhenListed() {
        registerProvider("lmstudio", ProviderKind.OPENAI_COMPATIBLE, "/v1");
        server.stubFor(get(urlEqualTo(OPENAI_MODELS_PATH)).willReturn(okJson("""
                {"object":"list","data":[{"id":"google/gemma-4-e4b"},{"id":"other"}]}
                """)));

        final VerificationReport report = report(verifier(scriptedNanoTime(0L, 0L, 0L, 0L))
                .verify(
                        new ModelSelection("lmstudio", "google/gemma-4-e4b"),
                        VerificationPolicy.CONNECTION_AND_MODELS));

        assertThat(report.stages())
                .extracting(StageOutcome::stage)
                .containsExactly(VerificationStage.CONNECTION, VerificationStage.MODELS);
        assertThat(report.stages())
                .extracting(StageOutcome::status)
                .containsExactly(StageStatus.PASSED, StageStatus.PASSED);
        assertThat(report.stages().get(1).count()).isEqualTo(2);
        server.verify(0, postRequestedFor(urlEqualTo(OPENAI_CHAT_PATH)));
    }

    // A soft-passed models stage from a malformed listing still reports a zero count and never reaches inference.
    @Test
    void verify_connectionAndModelsPolicy_softPassesOnMalformedListing() {
        registerProvider("lmstudio", ProviderKind.OPENAI_COMPATIBLE, "/v1");
        server.stubFor(get(urlEqualTo(OPENAI_MODELS_PATH)).willReturn(okJson("{\"data\":\"nope\"}")));

        final VerificationReport report = report(verifier(scriptedNanoTime(0L, 0L, 0L, 0L))
                .verify(new ModelSelection("lmstudio", MODEL_ID), VerificationPolicy.CONNECTION_AND_MODELS));

        assertThat(report.stages())
                .extracting(StageOutcome::status)
                .containsExactly(StageStatus.PASSED, StageStatus.SOFT_PASS);
        assertThat(report.stages().get(1).note()).isEqualTo("model list unavailable");
        assertThat(report.stages().get(1).count()).isZero();
        server.verify(0, postRequestedFor(urlEqualTo(OPENAI_CHAT_PATH)));
    }

    // A failed connection stage under the shorter policy still stops before ever reaching discovery.
    @Test
    void verify_connectionAndModelsPolicy_stopsAtFailedConnection() throws IOException {
        final ProviderConfig config = new ProviderConfig(
                "ollama", ProviderKind.OLLAMA, closedEndpoint(), Duration.ofSeconds(2), Duration.ofSeconds(2));
        assertThat(configs.register(config).isOk()).isTrue();

        final VerificationReport report = report(verifier(scriptedNanoTime(0L, 0L))
                .verify(new ModelSelection("ollama", MODEL_ID), VerificationPolicy.CONNECTION_AND_MODELS));

        assertThat(report.stages()).extracting(StageOutcome::stage).containsExactly(VerificationStage.CONNECTION);
        assertThat(report.stages().getFirst().status()).isEqualTo(StageStatus.FAILED);
        assertThat(error(report.stages().getFirst()).code()).isEqualTo(ErrorCode.unreachable);
    }

    private static URI closedEndpoint() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return URI.create("http://127.0.0.1:" + socket.getLocalPort());
        }
    }

    private void registerProvider(String id, ProviderKind kind, String pathSuffix) {
        final ProviderConfig config = new ProviderConfig(
                id, kind, URI.create(server.baseUrl() + pathSuffix), Duration.ofSeconds(2), Duration.ofSeconds(3));
        assertThat(configs.register(config).isOk()).isTrue();
    }

    private ProviderVerifier verifier(LongSupplier nanoTime) {
        return injector(nanoTime).getInstance(ProviderVerifier.class);
    }

    private Injector injector(LongSupplier nanoTime) {
        return Guice.createInjector(Modules.override(new LlmModule()).with(new AbstractModule() {
            @Override
            protected void configure() {
                bind(ProviderConfigs.class).toInstance(configs);
                bind(InferenceGate.class).toInstance(new InferenceGate());
                bind(RetryPolicy.class).toInstance(new RetryPolicy(FIXED_CLOCK, () -> 0.5, delay -> {}));
                bind(LongSupplier.class).toInstance(nanoTime);
            }
        }));
    }

    private static LongSupplier scriptedNanoTime(long... values) {
        final Iterator<Long> answers = LongStream.of(values).boxed().iterator();
        return answers::next;
    }

    private static VerificationReport report(Result<VerificationReport> result) {
        assertThat(result.isOk()).as("verification result: " + result.error()).isTrue();
        return Objects.requireNonNull(result.data(), "verification report");
    }

    private static AppError error(StageOutcome outcome) {
        return Objects.requireNonNull(outcome.error(), "stage error");
    }
}
