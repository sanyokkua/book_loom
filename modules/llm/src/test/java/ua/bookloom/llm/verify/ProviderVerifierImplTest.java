package ua.bookloom.llm.verify;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
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

/** Exercises provider verification, retries, and shared-gate behavior against both real HTTP dialect clients. */
class ProviderVerifierImplTest {

    private static final String OLLAMA_VERSION_PATH = "/api/version";
    private static final String OLLAMA_TAGS_PATH = "/api/tags";
    private static final String OLLAMA_CHAT_PATH = "/api/chat";
    private static final String OPENAI_MODELS_PATH = "/v1/models";
    private static final String OPENAI_CHAT_PATH = "/v1/chat/completions";
    private static final String MODEL_ID = "test-model";
    private static final String OPENAI_MODEL_REPLY =
            "{\"object\":\"list\",\"data\":[{\"id\":\"test-model\",\"object\":\"model\"}]}";
    private static final String OPENAI_CHAT_REPLY =
            "{\"model\":\"test-model\",\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"{\\\"status\\\":\\\"ok\\\"}\"},\"finish_reason\":\"stop\"}]}";
    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-09-22T12:00:00Z"), ZoneOffset.UTC);

    private final WireMockServer server =
            new WireMockServer(WireMockConfiguration.options().dynamicPort());
    private final List<Duration> retryDelays = new ArrayList<>();
    private InMemoryProviderConfigs configs;
    private InferenceGate gate;
    private RetryPolicy retryPolicy;

    @BeforeEach
    void startServer() {
        server.start();
        configs = new InMemoryProviderConfigs();
        gate = new InferenceGate();
        retryPolicy = newRetryPolicy(retryDelays::add);
    }

    @AfterEach
    void stopServer() {
        server.stop();
    }

    @Test
    void verify_ollamaConnectionModelsAndInferenceAllPass() {
        registerProvider(configs, "ollama", ProviderKind.OLLAMA, "");
        server.stubFor(get(urlEqualTo(OLLAMA_VERSION_PATH)).willReturn(okJson("{\"version\":\"0.6.0\"}")));
        server.stubFor(
                get(urlEqualTo(OLLAMA_TAGS_PATH)).willReturn(okJson("{\"models\":[{\"name\":\"test-model\"}]}")));
        server.stubFor(
                post(urlEqualTo(OLLAMA_CHAT_PATH))
                        .withRequestBody(equalToJson("""
                                        {"model":"test-model","messages":[{"role":"user","content":"Return exactly one JSON object: {\\"status\\":\\"ok\\"}."}],"stream":false,"format":{"type":"object","properties":{"status":{"const":"ok"}},"required":["status"],"additionalProperties":false},"think":false}
                                        """))
                        .willReturn(
                                okJson(
                                        "{\"model\":\"test-model\",\"message\":{\"role\":\"assistant\",\"content\":\"{\\\"status\\\":\\\"ok\\\"}\"},\"done\":true,\"done_reason\":\"stop\"}")));

        final VerificationReport report = report(verifier(configs, gate, retryPolicy)
                .verify(new ModelSelection("ollama", MODEL_ID), VerificationPolicy.FULL));

        assertThat(report.stages())
                .extracting(StageOutcome::stage)
                .containsExactly(VerificationStage.CONNECTION, VerificationStage.MODELS, VerificationStage.INFERENCE);
        assertThat(report.stages())
                .extracting(StageOutcome::status)
                .containsExactly(StageStatus.PASSED, StageStatus.PASSED, StageStatus.PASSED);
        assertThat(report.isPassed()).isTrue();
        server.verify(1, getRequestedFor(urlEqualTo(OLLAMA_VERSION_PATH)));
        server.verify(1, getRequestedFor(urlEqualTo(OLLAMA_TAGS_PATH)));
        server.verify(1, postRequestedFor(urlEqualTo(OLLAMA_CHAT_PATH)));
    }

    @Test
    void verify_unlistedOllamaModelFailsBeforeChat() {
        registerProvider(configs, "ollama", ProviderKind.OLLAMA, "");
        server.stubFor(get(urlEqualTo(OLLAMA_VERSION_PATH)).willReturn(okJson("{\"version\":\"0.6.0\"}")));
        server.stubFor(
                get(urlEqualTo(OLLAMA_TAGS_PATH)).willReturn(okJson("{\"models\":[{\"name\":\"another-model\"}]}")));

        final VerificationReport report = report(verifier(configs, gate, retryPolicy)
                .verify(new ModelSelection("ollama", MODEL_ID), VerificationPolicy.FULL));

        assertThat(report.stages())
                .extracting(StageOutcome::stage)
                .containsExactly(VerificationStage.CONNECTION, VerificationStage.MODELS);
        assertThat(report.stages())
                .extracting(StageOutcome::status)
                .containsExactly(StageStatus.PASSED, StageStatus.FAILED);
        assertThat(error(report.stages().get(1)).code()).isEqualTo(ErrorCode.modelUnavailable);
        server.verify(0, postRequestedFor(urlEqualTo(OLLAMA_CHAT_PATH)));
    }

    @Test
    void verify_authenticationFailureStopsAfterConnection() {
        registerProvider(configs, "ollama", ProviderKind.OLLAMA, "");
        server.stubFor(
                get(urlEqualTo(OLLAMA_VERSION_PATH)).willReturn(aResponse().withStatus(401)));

        final VerificationReport report = report(verifier(configs, gate, retryPolicy)
                .verify(new ModelSelection("ollama", MODEL_ID), VerificationPolicy.FULL));

        assertThat(report.stages()).extracting(StageOutcome::stage).containsExactly(VerificationStage.CONNECTION);
        assertThat(report.stages()).extracting(StageOutcome::status).containsExactly(StageStatus.FAILED);
        assertThat(error(report.stages().getFirst()).code()).isEqualTo(ErrorCode.auth);
        assertThat(server.getAllServeEvents()).hasSize(1);
    }

    @Test
    void verify_malformedOpenAiDiscoverySoftPassesAndInferenceSucceeds() {
        registerProvider(configs, "lmstudio", ProviderKind.OPENAI_COMPATIBLE, "/v1");
        server.stubFor(get(urlEqualTo(OPENAI_MODELS_PATH)).willReturn(okJson("{\"data\":\"nope\"}")));
        server.stubFor(post(urlEqualTo(OPENAI_CHAT_PATH)).willReturn(okJson(OPENAI_CHAT_REPLY)));

        final VerificationReport report = report(verifier(configs, gate, retryPolicy)
                .verify(new ModelSelection("lmstudio", MODEL_ID), VerificationPolicy.FULL));

        assertThat(report.stages())
                .extracting(StageOutcome::status)
                .containsExactly(StageStatus.PASSED, StageStatus.SOFT_PASS, StageStatus.PASSED);
        assertThat(report.stages().get(1).note()).isEqualTo("model list unavailable");
        assertThat(error(report.stages().get(1)).code()).isEqualTo(ErrorCode.discoveryFailed);
        assertThat(retryDelays).containsExactly(Duration.ofMillis(500), Duration.ofSeconds(1));
        server.verify(4, getRequestedFor(urlEqualTo(OPENAI_MODELS_PATH)));
        server.verify(1, postRequestedFor(urlEqualTo(OPENAI_CHAT_PATH)));
    }

    @Test
    void verify_emptyOllamaListMapsUnknownModelInferenceToModelUnavailable() {
        registerProvider(configs, "ollama", ProviderKind.OLLAMA, "");
        server.stubFor(get(urlEqualTo(OLLAMA_VERSION_PATH)).willReturn(okJson("{\"version\":\"0.6.0\"}")));
        server.stubFor(get(urlEqualTo(OLLAMA_TAGS_PATH)).willReturn(okJson("{\"models\":[]}")));
        server.stubFor(post(urlEqualTo(OLLAMA_CHAT_PATH))
                .willReturn(aResponse().withStatus(404).withBody("{\"error\":\"model 'test-model' not found\"}")));

        final VerificationReport report = report(verifier(configs, gate, retryPolicy)
                .verify(new ModelSelection("ollama", MODEL_ID), VerificationPolicy.FULL));

        assertThat(report.stages())
                .extracting(StageOutcome::status)
                .containsExactly(StageStatus.PASSED, StageStatus.SOFT_PASS, StageStatus.FAILED);
        assertThat(report.stages().get(1).note()).isEqualTo("model list unavailable");
        assertThat(report.stages().get(1).error()).isNull();
        assertThat(error(report.stages().get(2)).code()).isEqualTo(ErrorCode.modelUnavailable);
    }

    @Test
    void verify_preflightRunsStructuredInferenceWhenModelIsListed() {
        registerProvider(configs, "lmstudio", ProviderKind.OPENAI_COMPATIBLE, "/v1");
        server.stubFor(get(urlEqualTo(OPENAI_MODELS_PATH)).willReturn(okJson(OPENAI_MODEL_REPLY)));
        server.stubFor(post(urlEqualTo(OPENAI_CHAT_PATH)).willReturn(okJson(OPENAI_CHAT_REPLY)));

        final VerificationReport report = report(verifier(configs, gate, retryPolicy)
                .verify(new ModelSelection("lmstudio", MODEL_ID), VerificationPolicy.PREFLIGHT));

        assertThat(report.stages())
                .extracting(StageOutcome::stage)
                .containsExactly(VerificationStage.CONNECTION, VerificationStage.MODELS, VerificationStage.INFERENCE);
        assertThat(report.stages())
                .extracting(StageOutcome::status)
                .containsExactly(StageStatus.PASSED, StageStatus.PASSED, StageStatus.PASSED);
        assertThat(report.stages().get(2).note()).isEqualTo("structured output: supported");
        server.verify(2, getRequestedFor(urlEqualTo(OPENAI_MODELS_PATH)));
        server.verify(1, postRequestedFor(urlEqualTo(OPENAI_CHAT_PATH)));
    }

    // A server can be reachable and infer normally while not honoring the requested schema control.
    @Test
    void verify_preflightFormatRejected_plainProbePassesNotConfirmed() {
        registerProvider(configs, "lmstudio", ProviderKind.OPENAI_COMPATIBLE, "/v1");
        server.stubFor(get(urlEqualTo(OPENAI_MODELS_PATH)).willReturn(okJson(OPENAI_MODEL_REPLY)));
        server.stubFor(post(urlEqualTo(OPENAI_CHAT_PATH))
                .inScenario("structured probe capability")
                .whenScenarioStateIs(Scenario.STARTED)
                .willReturn(aResponse().withStatus(400).withBody("{\"error\":\"response_format unsupported\"}"))
                .willSetStateTo("plain probe"));
        server.stubFor(post(urlEqualTo(OPENAI_CHAT_PATH))
                .inScenario("structured probe capability")
                .whenScenarioStateIs("plain probe")
                .willReturn(okJson(OPENAI_CHAT_REPLY)));

        final VerificationReport report = report(verifier(configs, gate, retryPolicy)
                .verify(new ModelSelection("lmstudio", MODEL_ID), VerificationPolicy.PREFLIGHT));

        assertThat(report.stages().get(2).status()).isEqualTo(StageStatus.PASSED);
        assertThat(report.stages().get(2).note()).isEqualTo("structured output: not confirmed");
        server.verify(2, postRequestedFor(urlEqualTo(OPENAI_CHAT_PATH)));
    }

    @Test
    void verify_preflightRetriesThreeFailedDiscoveryAttemptsThenInfers() {
        registerProvider(configs, "lmstudio", ProviderKind.OPENAI_COMPATIBLE, "/v1");
        stubThreeDiscoveryFailuresAfterProbe();
        server.stubFor(post(urlEqualTo(OPENAI_CHAT_PATH)).willReturn(okJson(OPENAI_CHAT_REPLY)));

        final VerificationReport report = report(verifier(configs, gate, retryPolicy)
                .verify(new ModelSelection("lmstudio", MODEL_ID), VerificationPolicy.PREFLIGHT));

        assertThat(report.stages())
                .extracting(StageOutcome::status)
                .containsExactly(StageStatus.PASSED, StageStatus.SOFT_PASS, StageStatus.PASSED);
        assertThat(report.stages().get(1).note()).isEqualTo("model list unavailable");
        assertThat(error(report.stages().get(1)).code()).isEqualTo(ErrorCode.discoveryFailed);
        assertThat(retryDelays).containsExactly(Duration.ofMillis(500), Duration.ofSeconds(1));
        server.verify(4, getRequestedFor(urlEqualTo(OPENAI_MODELS_PATH)));
        server.verify(1, postRequestedFor(urlEqualTo(OPENAI_CHAT_PATH)));
    }

    @Test
    void verify_unknownProviderReturnsValidationWithoutTraffic() {
        final Result<VerificationReport> result = verifier(configs, gate, retryPolicy)
                .verify(new ModelSelection("unknown-provider", MODEL_ID), VerificationPolicy.FULL);

        assertThat(result.isErr()).isTrue();
        assertThat(Objects.requireNonNull(result.error(), "error").code()).isEqualTo(ErrorCode.validation);
        assertThat(server.getAllServeEvents()).isEmpty();
    }

    @Test
    void verify_sanitizedBlankInferenceFailsWithEmptyCompletion() {
        registerProvider(configs, "ollama", ProviderKind.OLLAMA, "");
        server.stubFor(get(urlEqualTo(OLLAMA_VERSION_PATH)).willReturn(okJson("{\"version\":\"0.6.0\"}")));
        server.stubFor(
                get(urlEqualTo(OLLAMA_TAGS_PATH)).willReturn(okJson("{\"models\":[{\"name\":\"test-model\"}]}")));
        server.stubFor(
                post(urlEqualTo(OLLAMA_CHAT_PATH))
                        .willReturn(
                                okJson(
                                        "{\"model\":\"test-model\",\"message\":{\"role\":\"assistant\",\"content\":\"<think>only reasoning</think>\"},\"done\":true,\"done_reason\":\"stop\"}")));

        final VerificationReport report = report(verifier(configs, gate, retryPolicy)
                .verify(new ModelSelection("ollama", MODEL_ID), VerificationPolicy.FULL));

        assertThat(report.stages())
                .extracting(StageOutcome::status)
                .containsExactly(StageStatus.PASSED, StageStatus.PASSED, StageStatus.FAILED);
        assertThat(error(report.stages().get(2)).code()).isEqualTo(ErrorCode.emptyCompletion);
    }

    @Test
    void verify_preservesNonModelNotFoundInferenceError() {
        registerProvider(configs, "lmstudio", ProviderKind.OPENAI_COMPATIBLE, "/v1");
        server.stubFor(get(urlEqualTo(OPENAI_MODELS_PATH)).willReturn(okJson(OPENAI_MODEL_REPLY)));
        server.stubFor(post(urlEqualTo(OPENAI_CHAT_PATH)).willReturn(aResponse().withStatus(401)));

        final VerificationReport report = report(verifier(configs, gate, retryPolicy)
                .verify(new ModelSelection("lmstudio", MODEL_ID), VerificationPolicy.FULL));

        assertThat(report.stages())
                .extracting(StageOutcome::status)
                .containsExactly(StageStatus.PASSED, StageStatus.PASSED, StageStatus.FAILED);
        assertThat(error(report.stages().get(2)).code()).isEqualTo(ErrorCode.auth);
    }

    @Test
    void llmModuleBindsVerifierWithoutOpeningAConnection() {
        final ProviderVerifier verifier = Guice.createInjector(new LlmModule()).getInstance(ProviderVerifier.class);

        assertThat(verifier).isNotNull();
        assertThat(server.getAllServeEvents()).isEmpty();
    }

    private void stubThreeDiscoveryFailuresAfterProbe() {
        server.stubFor(get(urlEqualTo(OPENAI_MODELS_PATH))
                .inScenario("probe before discovery failures")
                .whenScenarioStateIs(Scenario.STARTED)
                .willReturn(okJson(OPENAI_MODEL_REPLY))
                .willSetStateTo("probe complete"));
        server.stubFor(get(urlEqualTo(OPENAI_MODELS_PATH))
                .inScenario("probe before discovery failures")
                .whenScenarioStateIs("probe complete")
                .willReturn(aResponse().withStatus(500).withBody("{}"))
                .willSetStateTo("first discovery failure"));
        server.stubFor(get(urlEqualTo(OPENAI_MODELS_PATH))
                .inScenario("probe before discovery failures")
                .whenScenarioStateIs("first discovery failure")
                .willReturn(aResponse().withStatus(500).withBody("{}"))
                .willSetStateTo("second discovery failure"));
        server.stubFor(get(urlEqualTo(OPENAI_MODELS_PATH))
                .inScenario("probe before discovery failures")
                .whenScenarioStateIs("second discovery failure")
                .willReturn(aResponse().withStatus(500).withBody("{}")));
    }

    private ProviderVerifier verifier(
            ProviderConfigs providerConfigs, InferenceGate inferenceGate, RetryPolicy policy) {
        return injector(providerConfigs, inferenceGate, policy).getInstance(ProviderVerifier.class);
    }

    private Injector injector(ProviderConfigs providerConfigs, InferenceGate inferenceGate, RetryPolicy policy) {
        return Guice.createInjector(Modules.override(new LlmModule()).with(new AbstractModule() {
            @Override
            protected void configure() {
                bind(ProviderConfigs.class).toInstance(providerConfigs);
                bind(InferenceGate.class).toInstance(inferenceGate);
                bind(RetryPolicy.class).toInstance(policy);
            }
        }));
    }

    private ProviderConfig registerProvider(
            ProviderConfigs providerConfigs, String id, ProviderKind kind, String pathSuffix) {
        final ProviderConfig config = new ProviderConfig(
                id, kind, URI.create(server.baseUrl() + pathSuffix), Duration.ofSeconds(2), Duration.ofSeconds(3));
        assertThat(providerConfigs.register(config).isOk()).isTrue();
        return config;
    }

    private static RetryPolicy newRetryPolicy(RetryPolicy.Sleeper sleeper) {
        return new RetryPolicy(FIXED_CLOCK, () -> 0.5, sleeper);
    }

    private static VerificationReport report(Result<VerificationReport> result) {
        assertThat(result.isOk()).isTrue();
        return Objects.requireNonNull(result.data(), "verification report");
    }

    private static AppError error(StageOutcome outcome) {
        return Objects.requireNonNull(outcome.error(), "stage error");
    }
}
