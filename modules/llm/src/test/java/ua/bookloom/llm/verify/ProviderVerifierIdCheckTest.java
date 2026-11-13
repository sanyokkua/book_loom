package ua.bookloom.llm.verify;

import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
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
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.llm.ProviderConfig;
import ua.bookloom.api.llm.ProviderConfigs;
import ua.bookloom.api.llm.ProviderKind;
import ua.bookloom.api.llm.ProviderVerifier;
import ua.bookloom.api.llm.ServerEngine;
import ua.bookloom.api.llm.StageOutcome;
import ua.bookloom.api.llm.StageStatus;
import ua.bookloom.api.llm.VerificationExchange;
import ua.bookloom.api.llm.VerificationPolicy;
import ua.bookloom.api.llm.VerificationReport;
import ua.bookloom.api.persistence.ModelSettingsRepository;
import ua.bookloom.llm.LlmModule;
import ua.bookloom.llm.retry.RetryPolicy;
import ua.bookloom.llm.testing.FakeModelSettingsRepository;
import ua.bookloom.llm.testing.FakeProviderConfigs;

/** The Test's models stage checks an LM Studio id with the same check a chat call makes; inference names its route. */
class ProviderVerifierIdCheckTest {

    private static final String MODEL_ID = "google/gemma-4-e4b";
    private static final String NATIVE_CHAT = "/api/v0/chat/completions";
    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-10-29T12:00:00Z"), ZoneOffset.UTC);

    private final WireMockServer server =
            new WireMockServer(WireMockConfiguration.options().dynamicPort().bindAddress("127.0.0.1"));
    private ProviderVerifier verifier;

    @BeforeEach
    void startServer() {
        server.start();
        verifier = verifier(new FakeProviderConfigs());
        // LM Studio answers 200 with an error body to every path it does not serve.
        server.stubFor(get(anyUrl())
                .atPriority(10)
                .willReturn(okJson("{\"error\":\"Unexpected endpoint or method. (GET /x)\"}")));
        server.stubFor(get(urlEqualTo("/api/v0/models"))
                .atPriority(1)
                .willReturn(okJson("{\"object\":\"list\",\"data\":[{\"id\":\"google/gemma-4-e4b\",\"type\":\"vlm\","
                        + "\"compatibility_type\":\"mlx\",\"state\":\"loaded\"},{\"id\":\"lapa-v0.1.2-instruct\","
                        + "\"type\":\"llm\",\"compatibility_type\":\"gguf\",\"state\":\"not-loaded\"}]}")));
        server.stubFor(get(urlEqualTo("/v1/models"))
                .atPriority(1)
                .willReturn(
                        okJson("{\"data\":[{\"id\":\"google/gemma-4-e4b\",\"owned_by\":\"organization_owner\"}]}")));
        server.stubFor(post(urlEqualTo(NATIVE_CHAT))
                .willReturn(okJson("{\"model\":\"google/gemma-4-e4b\",\"choices\":[{\"message\":{\"content\":"
                        + "\"{\\\"status\\\":\\\"ok\\\"}\"},\"finish_reason\":\"stop\"}],"
                        + "\"usage\":{\"prompt_tokens\":20,\"completion_tokens\":6},"
                        + "\"stats\":{\"time_to_first_token\":0.21,\"generation_time\":0.1}}")));
    }

    @AfterEach
    void stopServer() {
        server.stop();
    }

    @Test
    void verify_lmStudioIdNotListed_failsModelsWithModelNotFoundAndSendsNoChat() {
        final VerificationReport report = report("google/gemma-4-e4b-typo");

        final StageOutcome models = report.stages().get(1);
        assertThat(models.status()).isEqualTo(StageStatus.FAILED);
        assertThat(Objects.requireNonNull(models.error(), "error").code()).isEqualTo(ErrorCode.modelNotFound);
        assertThat(Objects.requireNonNull(models.error(), "error").message()).contains("google/gemma-4-e4b-typo");
        server.verify(0, postRequestedFor(anyUrl()));
    }

    @Test
    void verify_lmStudioVariant_passesModelsAndNotesTheRemoval() {
        final StageOutcome models = report(MODEL_ID + "@q4_k_m").stages().get(1);

        assertThat(models.status()).isEqualTo(StageStatus.PASSED);
        assertThat(models.count()).isEqualTo(2);
        assertThat(models.note()).isEqualTo("The variant @q4_k_m was removed; google/gemma-4-e4b is used.");
    }

    @Test
    void verify_lmStudioInference_exchangeNamesTheRouteUsedAndTheFirstTokenTime() {
        final StageOutcome inference = report(MODEL_ID).stages().get(2);

        final VerificationExchange exchange = Objects.requireNonNull(inference.exchange(), "exchange");
        assertThat(inference.status()).isEqualTo(StageStatus.PASSED);
        assertThat(exchange.url()).isEqualTo(server.baseUrl() + NATIVE_CHAT);
        assertThat(exchange.firstTokenMs()).isEqualTo(210L);
    }

    // The first Test of a description with no engine yet: the models stage must already use the engine it detected.
    @Test
    void verify_firstTestGenericDraftVariant_passesModelsAndNotesTheRemoval() {
        final VerificationReport report = firstReport(MODEL_ID + "@q4_k_m");

        final StageOutcome models = report.stages().get(1);
        assertThat(Objects.requireNonNull(report.engine(), "engine").engine()).isEqualTo(ServerEngine.LM_STUDIO);
        assertThat(models.status()).isEqualTo(StageStatus.PASSED);
        assertThat(models.note()).isEqualTo("The variant @q4_k_m was removed; google/gemma-4-e4b is used.");
        assertThat(report.stages().get(2).status()).isEqualTo(StageStatus.PASSED);
    }

    @Test
    void verify_firstTestGenericDraftIdNotListed_failsModelsNamingTheMissingId() {
        final StageOutcome models =
                firstReport("google/gemma-4-e4b-typo").stages().get(1);

        assertThat(models.status()).isEqualTo(StageStatus.FAILED);
        assertThat(Objects.requireNonNull(models.error(), "error").code()).isEqualTo(ErrorCode.modelNotFound);
        assertThat(Objects.requireNonNull(models.error(), "error").message()).contains("google/gemma-4-e4b-typo");
        server.verify(0, postRequestedFor(anyUrl()));
    }

    private VerificationReport report(final String modelId) {
        return run(draft().withEngine(ServerEngine.LM_STUDIO), modelId);
    }

    private VerificationReport firstReport(final String modelId) {
        return run(draft(), modelId);
    }

    private ProviderConfig draft() {
        return new ProviderConfig(
                "lmstudio",
                ProviderKind.OPENAI_COMPATIBLE,
                URI.create(server.baseUrl() + "/v1"),
                Duration.ofSeconds(2),
                Duration.ofSeconds(3));
    }

    private VerificationReport run(final ProviderConfig draft, final String modelId) {
        return Objects.requireNonNull(
                verifier.verify(draft, modelId, VerificationPolicy.FULL).data(), "report");
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
}
