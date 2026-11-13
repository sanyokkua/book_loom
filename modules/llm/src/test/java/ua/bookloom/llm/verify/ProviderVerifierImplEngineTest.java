package ua.bookloom.llm.verify;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.util.Modules;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Objects;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.FailureReason;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.EngineDetection;
import ua.bookloom.api.llm.ModelSelection;
import ua.bookloom.api.llm.ProviderConfig;
import ua.bookloom.api.llm.ProviderConfigs;
import ua.bookloom.api.llm.ProviderKind;
import ua.bookloom.api.llm.ProviderVerifier;
import ua.bookloom.api.llm.ServerEngine;
import ua.bookloom.api.llm.StageOutcome;
import ua.bookloom.api.llm.StageStatus;
import ua.bookloom.api.llm.VerificationPolicy;
import ua.bookloom.api.llm.VerificationReport;
import ua.bookloom.api.persistence.ModelSettingsRepository;
import ua.bookloom.llm.LlmModule;
import ua.bookloom.llm.retry.RetryPolicy;
import ua.bookloom.llm.testing.FakeModelSettingsRepository;
import ua.bookloom.llm.testing.FakeProviderConfigs;

/** Proves on the wire that a Test detects the server engine, stores it, and fails an Ollama provider that is not one. */
class ProviderVerifierImplEngineTest {

    private static final String OLLAMA_VERSION_PATH = "/api/version";
    private static final String OLLAMA_TAGS_PATH = "/api/tags";
    private static final String OPENAI_MODELS_PATH = "/v1/models";
    private static final String MODEL_ID = "test-model";
    private static final String OPENAI_MODEL_REPLY =
            "{\"object\":\"list\",\"data\":[{\"id\":\"test-model\",\"object\":\"model\"}]}";
    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-10-29T12:00:00Z"), ZoneOffset.UTC);

    private final WireMockServer server =
            new WireMockServer(WireMockConfiguration.options().dynamicPort().bindAddress("127.0.0.1"));
    private FakeProviderConfigs configs;
    private ProviderVerifier verifier;

    @BeforeEach
    void startServer() {
        server.start();
        configs = new FakeProviderConfigs();
        verifier = verifier(configs);
    }

    @AfterEach
    void stopServer() {
        server.stop();
    }

    @Test
    void verify_ollamaKindOnLlamaServer_failsNamingLlamaServer() {
        registerProvider("ollama", ProviderKind.OLLAMA, "");
        stubLlamaServer();

        final VerificationReport report =
                report(verifier.verify(new ModelSelection("ollama", MODEL_ID), VerificationPolicy.CONNECTION));

        assertThat(report.stages()).extracting(StageOutcome::status).containsExactly(StageStatus.FAILED);
        assertThat(error(report.stages().get(0)).code()).isEqualTo(ErrorCode.validation);
        assertThat(error(report.stages().get(0)).message())
                .isEqualTo("The server at this address is llama-server, not Ollama.");
        // The window words the sentence in another language from the reason and the engine the details carry.
        assertThat(FailureReason.in(error(report.stages().get(0)).details())).contains(FailureReason.ENGINE_MISMATCH);
        assertThat(ServerEngine.in(error(report.stages().get(0)).details())).contains(ServerEngine.LLAMA_CPP);
        assertThat(engine(report).engine()).isEqualTo(ServerEngine.LLAMA_CPP);
    }

    @Test
    void verify_ollamaKindOnLmStudio_failsNamingLmStudio() {
        registerProvider("ollama", ProviderKind.OLLAMA, "");
        server.stubFor(get(anyUrl())
                .atPriority(10)
                .willReturn(okJson("{\"error\":\"Unexpected endpoint or method. (GET /api/version)\"}")));
        server.stubFor(get(urlEqualTo("/api/v0/models"))
                .atPriority(1)
                .willReturn(okJson("{\"object\":\"list\",\"data\":[{\"id\":\"google/gemma-4-e4b\","
                        + "\"type\":\"vlm\",\"compatibility_type\":\"mlx\"}]}")));

        final VerificationReport report =
                report(verifier.verify(new ModelSelection("ollama", MODEL_ID), VerificationPolicy.CONNECTION));

        assertThat(error(report.stages().get(0)).message())
                .isEqualTo("The server at this address is LM Studio, not Ollama.");
    }

    @Test
    void verify_registeredLlamaServer_storesEngineAndVersion() {
        registerProvider("llama", ProviderKind.OPENAI_COMPATIBLE, "/v1");
        stubLlamaServer();
        server.stubFor(get(urlEqualTo(OPENAI_MODELS_PATH)).willReturn(okJson(OPENAI_MODEL_REPLY)));

        final VerificationReport report =
                report(verifier.verify(new ModelSelection("llama", MODEL_ID), VerificationPolicy.CONNECTION));

        assertThat(report.isPassed()).isTrue();
        assertThat(stored("llama").engine()).isEqualTo(ServerEngine.LLAMA_CPP);
        assertThat(stored("llama").engineVersion()).isEqualTo("b11669");
    }

    @Test
    void verify_recoveryProbePolicy_sendsNoDetectionProbe() {
        registerProvider("llama", ProviderKind.OPENAI_COMPATIBLE, "/v1");
        stubLlamaServer();
        server.stubFor(get(urlEqualTo(OPENAI_MODELS_PATH)).willReturn(okJson(OPENAI_MODEL_REPLY)));

        final VerificationReport report = report(
                verifier.verify(new ModelSelection("llama", MODEL_ID), VerificationPolicy.CONNECTION_AND_MODELS));

        assertThat(report.engine()).isNull();
        server.verify(0, getRequestedFor(urlEqualTo("/health")));
        assertThat(stored("llama").engine()).isEqualTo(ServerEngine.GENERIC);
    }

    @Test
    void verify_ollamaBehindOpenAiKind_reportsOfferOnce() {
        final ProviderConfig saved = registerProvider("local", ProviderKind.OPENAI_COMPATIBLE, "/v1");
        stubOllamaBehindV1();

        final VerificationReport first =
                report(verifier.verify(new ModelSelection("local", MODEL_ID), VerificationPolicy.CONNECTION));
        assertThat(configs.save(declined(stored("local"))).isOk()).isTrue();
        final VerificationReport second =
                report(verifier.verify(new ModelSelection("local", MODEL_ID), VerificationPolicy.CONNECTION));

        assertThat(engine(first).offerNativeConnection()).isTrue();
        assertThat(engine(second).engine()).isEqualTo(ServerEngine.OLLAMA);
        assertThat(engine(second).offerNativeConnection()).isFalse();
        assertThat(stored("local").kind()).isEqualTo(saved.kind());
        assertThat(stored("local").engineChoice()).isNull();
    }

    @Test
    void detectAgain_afterKeepAsIs_noOfferAndNoEngineChoice() {
        registerProvider("local", ProviderKind.OPENAI_COMPATIBLE, "/v1");
        final ProviderConfig keptWithChoice =
                declined(stored("local")).withEngine(ServerEngine.GENERIC, ServerEngine.LLAMA_CPP, null);
        assertThat(configs.save(keptWithChoice).isOk()).isTrue();
        stubOllamaBehindV1();

        final Result<EngineDetection> result = verifier.detectEngine(stored("local"));

        final EngineDetection detection = Objects.requireNonNull(result.data(), "detection");
        assertThat(detection.engine()).isEqualTo(ServerEngine.OLLAMA);
        assertThat(detection.source()).isEqualTo(EngineDetection.Source.DETECTED);
        assertThat(detection.offerNativeConnection()).isFalse();
        assertThat(stored("local").engine()).isEqualTo(ServerEngine.OLLAMA);
        assertThat(stored("local").engineVersion()).isEqualTo("0.40.11");
        assertThat(stored("local").engineChoice()).isNull();
        assertThat(stored("local").nativeOfferDeclined()).isTrue();
    }

    @Test
    void verify_choiceSetOnSavedProvider_keepsChoiceAndSendsNoProbe() {
        registerProvider("office", ProviderKind.OPENAI_COMPATIBLE, "/v1");
        assertThat(configs.save(stored("office").withEngine(ServerEngine.GENERIC, ServerEngine.LLAMA_CPP, null))
                        .isOk())
                .isTrue();
        server.stubFor(get(urlEqualTo(OPENAI_MODELS_PATH)).willReturn(okJson(OPENAI_MODEL_REPLY)));

        final VerificationReport report =
                report(verifier.verify(new ModelSelection("office", MODEL_ID), VerificationPolicy.CONNECTION));

        assertThat(engine(report).source()).isEqualTo(EngineDetection.Source.OVERRIDE);
        assertThat(stored("office").effectiveEngine()).isEqualTo(ServerEngine.LLAMA_CPP);
        server.verify(0, getRequestedFor(urlEqualTo("/health")));
    }

    @Test
    void verify_draftAtAnotherAddress_reportsEngineAndStoresNothing() {
        registerProvider("local", ProviderKind.OPENAI_COMPATIBLE, "/v1");
        stubLlamaServer();
        server.stubFor(get(urlEqualTo("/other/v1/models")).willReturn(okJson(OPENAI_MODEL_REPLY)));
        final ProviderConfig draft = stored("local").withBaseUrl(URI.create(server.baseUrl() + "/other/v1"));

        final VerificationReport report = report(verifier.verify(draft, "", VerificationPolicy.CONNECTION));

        assertThat(report.engine()).isNotNull();
        assertThat(stored("local").engine()).isEqualTo(ServerEngine.GENERIC);
    }

    @Test
    void verify_gpt4allOwnedByHumanity_failsAtConnectionWithNotSuitableStructured() {
        registerProvider("gpt4all", ProviderKind.OPENAI_COMPATIBLE, "/v1");
        server.stubFor(get(urlEqualTo(OPENAI_MODELS_PATH))
                .willReturn(okJson("{\"data\":[{\"id\":\"Llama 3 8B Instruct\",\"object\":\"model\","
                        + "\"owned_by\":\"humanity\"}]}")));

        final VerificationReport report =
                report(verifier.verify(new ModelSelection("gpt4all", MODEL_ID), VerificationPolicy.FULL));

        assertThat(report.stages()).extracting(StageOutcome::status).containsExactly(StageStatus.FAILED);
        final AppError failure = error(report.stages().get(0));
        assertThat(failure.code()).isEqualTo(ErrorCode.validation);
        assertThat(FailureReason.in(failure.details())).contains(FailureReason.NOT_SUITABLE_STRUCTURED);
        assertThat(failure.message())
                .isEqualTo("This app does not support structured replies, so BookLoom cannot use it.");
        server.verify(0, postRequestedFor(anyUrl()));
        assertThat(stored("gpt4all").engine()).isEqualTo(ServerEngine.GENERIC);
        assertThat(stored("gpt4all").engineVersion()).isNull();
    }

    @Test
    void verify_anythingLlmOpenAiPath_failsAtConnectionWithNotSuitableDocuments() {
        registerProvider("anything", ProviderKind.OPENAI_COMPATIBLE, "/api/v1/openai");
        server.stubFor(get(urlEqualTo("/api/v1/openai/models"))
                .willReturn(okJson("{\"data\":[{\"id\":\"my-workspace\",\"object\":\"model\"}]}")));

        final VerificationReport report =
                report(verifier.verify(new ModelSelection("anything", MODEL_ID), VerificationPolicy.FULL));

        assertThat(report.stages()).extracting(StageOutcome::status).containsExactly(StageStatus.FAILED);
        final AppError failure = error(report.stages().get(0));
        assertThat(failure.code()).isEqualTo(ErrorCode.validation);
        assertThat(FailureReason.in(failure.details())).contains(FailureReason.NOT_SUITABLE_DOCUMENTS);
        assertThat(failure.message()).isEqualTo("This address chats with documents, not with a model.");
        server.verify(0, postRequestedFor(anyUrl()));
        assertThat(stored("anything").engine()).isEqualTo(ServerEngine.GENERIC);
    }

    @Test
    void verify_anythingLlmAskingForAKey_stillFailsWithNotSuitableDocuments() {
        registerProvider("anything", ProviderKind.OPENAI_COMPATIBLE, "/api/v1/openai");
        server.stubFor(get(urlEqualTo("/api/v1/openai/models"))
                .willReturn(aResponse().withStatus(403).withBody("{\"error\":\"No valid api key found.\"}")));

        final VerificationReport report =
                report(verifier.verify(new ModelSelection("anything", MODEL_ID), VerificationPolicy.CONNECTION));

        assertThat(FailureReason.in(error(report.stages().get(0)).details()))
                .contains(FailureReason.NOT_SUITABLE_DOCUMENTS);
    }

    private void stubLlamaServer() {
        server.stubFor(
                get(urlEqualTo(OLLAMA_VERSION_PATH)).willReturn(aResponse().withStatus(404)));
        server.stubFor(get(urlEqualTo("/health"))
                .willReturn(okJson("{\"status\":\"ok\"}").withHeader("Server", "llama.cpp")));
        server.stubFor(get(urlEqualTo("/props")).willReturn(okJson("{\"build_info\":\"b11669-620d2225\"}")));
    }

    private void stubOllamaBehindV1() {
        server.stubFor(get(urlEqualTo(OLLAMA_VERSION_PATH)).willReturn(okJson("{\"version\":\"0.40.11\"}")));
        server.stubFor(
                get(urlEqualTo(OLLAMA_TAGS_PATH)).willReturn(okJson("{\"models\":[{\"name\":\"test-model\"}]}")));
        server.stubFor(get(urlEqualTo(OPENAI_MODELS_PATH))
                .willReturn(okJson("{\"object\":\"list\",\"data\":[{\"id\":\"test-model\",\"object\":\"model\","
                        + "\"owned_by\":\"library\"}]}")));
    }

    private ProviderConfig stored(final String id) {
        return configs.find(id).orElseThrow();
    }

    private static ProviderConfig declined(final ProviderConfig config) {
        return new ProviderConfig(
                config.id(),
                config.kind(),
                config.baseUrl(),
                config.connectTimeout(),
                config.requestTimeout(),
                config.displayName(),
                config.auth(),
                config.apiKeyHeader(),
                config.credentialRef(),
                config.modelsPath(),
                config.chatPath(),
                config.headers(),
                config.customModels(),
                config.engine(),
                config.engineChoice(),
                config.engineVersion(),
                true);
    }

    private static EngineDetection engine(final VerificationReport report) {
        return Objects.requireNonNull(report.engine(), "detected engine");
    }

    private static ProviderVerifier verifier(final ProviderConfigs providerConfigs) {
        return Guice.createInjector(Modules.override(new LlmModule()).with(new AbstractModule() {
                    @Override
                    protected void configure() {
                        bind(ProviderConfigs.class).toInstance(providerConfigs);
                        bind(ModelSettingsRepository.class).toInstance(new FakeModelSettingsRepository());
                        bind(RetryPolicy.class).toInstance(new RetryPolicy(FIXED_CLOCK, () -> 0.5, delay -> {}));
                    }
                }))
                .getInstance(ProviderVerifier.class);
    }

    private ProviderConfig registerProvider(final String id, final ProviderKind kind, final String pathSuffix) {
        final ProviderConfig config = new ProviderConfig(
                id, kind, URI.create(server.baseUrl() + pathSuffix), Duration.ofSeconds(2), Duration.ofSeconds(3));
        assertThat(configs.save(config).isOk()).isTrue();
        return config;
    }

    private static VerificationReport report(final Result<VerificationReport> result) {
        assertThat(result.isOk()).isTrue();
        return Objects.requireNonNull(result.data(), "verification report");
    }

    private static AppError error(final StageOutcome outcome) {
        return Objects.requireNonNull(outcome.error(), "stage error");
    }
}
