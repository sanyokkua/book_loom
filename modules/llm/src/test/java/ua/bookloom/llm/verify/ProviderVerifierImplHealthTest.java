package ua.bookloom.llm.verify;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.FailureReason;
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
import ua.bookloom.api.persistence.ModelSettingsRepository;
import ua.bookloom.llm.LlmModule;
import ua.bookloom.llm.gate.InferenceGate;
import ua.bookloom.llm.retry.RetryPolicy;
import ua.bookloom.llm.testing.FakeModelSettingsRepository;
import ua.bookloom.llm.testing.FakeProviderConfigs;

/** The wake of a run paused on a loading llama-server asks its health route before its model list. */
class ProviderVerifierImplHealthTest {

    private static final String HEALTH_PATH = "/health";
    private static final String MODELS_PATH = "/v1/models";
    private static final String MODEL_LIST = "{\"object\":\"list\",\"data\":[{\"id\":\"test-model\"}]}";
    private static final ModelSelection SELECTION = new ModelSelection("llama", "test-model");
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-29T12:00:00Z"), ZoneOffset.UTC);

    private final WireMockServer server =
            new WireMockServer(WireMockConfiguration.options().dynamicPort().bindAddress("127.0.0.1"));

    @BeforeEach
    void start() {
        server.start();
        server.stubFor(get(urlEqualTo(MODELS_PATH)).willReturn(okJson(MODEL_LIST)));
    }

    @AfterEach
    void stop() {
        server.stop();
    }

    @Test
    void verify_healthSaysLoading_failsWithModelLoadingBeforeModels() {
        server.stubFor(get(urlEqualTo(HEALTH_PATH))
                .willReturn(aResponse().withStatus(503).withBody("{\"status\":\"loading model\"}")));

        final VerificationReport report = report(verifier().verify(SELECTION, VerificationPolicy.HEALTH_AND_MODELS));

        assertThat(report.stages()).extracting(StageOutcome::stage).containsExactly(VerificationStage.CONNECTION);
        assertThat(report.stages()).extracting(StageOutcome::status).containsExactly(StageStatus.FAILED);
        final AppError error = Objects.requireNonNull(report.stages().getFirst().error(), "stage error");
        assertThat(error.code()).isEqualTo(ErrorCode.modelUnavailable);
        assertThat(FailureReason.in(error.details())).contains(FailureReason.MODEL_LOADING);
        server.verify(1, getRequestedFor(urlEqualTo(HEALTH_PATH)));
        server.verify(0, getRequestedFor(urlEqualTo(MODELS_PATH)));
    }

    // A health route that says ready, that is missing, or that answers only an error is no reason to wait.
    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {"200 | {\"status\":\"ok\"}", "404 | Not Found", "200 | {\"error\":\"loading is not a route\"}"})
    void verify_healthNotSayingLoading_runsConnectionAndModels(final int status, final String body) {
        server.stubFor(get(urlEqualTo(HEALTH_PATH))
                .willReturn(aResponse().withStatus(status).withBody(body)));

        final VerificationReport report = report(verifier().verify(SELECTION, VerificationPolicy.HEALTH_AND_MODELS));

        assertThat(report.stages())
                .extracting(StageOutcome::stage)
                .containsExactly(VerificationStage.CONNECTION, VerificationStage.MODELS);
        assertThat(report.isPassed()).isTrue();
    }

    // Only the model-loading wake asks the health route; every other probe leaves it alone.
    @Test
    void verify_connectionAndModels_neverAsksTheHealthRoute() {
        final VerificationReport report =
                report(verifier().verify(SELECTION, VerificationPolicy.CONNECTION_AND_MODELS));

        assertThat(report.isPassed()).isTrue();
        server.verify(0, getRequestedFor(urlEqualTo(HEALTH_PATH)));
    }

    private ProviderVerifier verifier() {
        final FakeProviderConfigs configs = new FakeProviderConfigs();
        configs.save(new ProviderConfig(
                SELECTION.providerId(),
                ProviderKind.OPENAI_COMPATIBLE,
                URI.create(server.baseUrl() + "/v1"),
                Duration.ofSeconds(2),
                Duration.ofSeconds(3)));
        final RetryPolicy policy = new RetryPolicy(CLOCK, () -> 0.5, ignored -> {});
        return Guice.createInjector(Modules.override(new LlmModule()).with(new AbstractModule() {
                    @Override
                    protected void configure() {
                        bind(ProviderConfigs.class).toInstance(configs);
                        bind(ModelSettingsRepository.class).toInstance(new FakeModelSettingsRepository());
                        bind(InferenceGate.class).toInstance(new InferenceGate());
                        bind(RetryPolicy.class).toInstance(policy);
                    }
                }))
                .getInstance(ProviderVerifier.class);
    }

    private static VerificationReport report(final Result<VerificationReport> result) {
        assertThat(result.isOk()).isTrue();
        return Objects.requireNonNull(result.data(), "verification report");
    }
}
