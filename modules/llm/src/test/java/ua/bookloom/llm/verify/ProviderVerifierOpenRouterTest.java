package ua.bookloom.llm.verify;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Objects;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
import ua.bookloom.api.llm.VerificationStage;
import ua.bookloom.api.persistence.ModelSettingsRepository;
import ua.bookloom.llm.LlmModule;
import ua.bookloom.llm.testing.FakeModelSettingsRepository;
import ua.bookloom.llm.testing.FakeProviderConfigs;

/** The Test's models stage on an OpenRouter provider, against a WireMock catalog (design D21). */
class ProviderVerifierOpenRouterTest {

    private final WireMockServer server =
            new WireMockServer(WireMockConfiguration.options().dynamicPort().bindAddress("127.0.0.1"));
    private ProviderVerifier verifier;

    @BeforeEach
    void startServer() throws IOException {
        server.start();
        final ProviderConfigs configs = new FakeProviderConfigs();
        final ProviderConfig config = new ProviderConfig(
                        "openrouter",
                        ProviderKind.OPENAI_COMPATIBLE,
                        URI.create(server.baseUrl() + "/api/v1"),
                        Duration.ofSeconds(2),
                        Duration.ofSeconds(3))
                .withEngine(ServerEngine.GENERIC, ServerEngine.OPENROUTER, null);
        assertThat(configs.save(config).isOk()).isTrue();
        try (InputStream in = Objects.requireNonNull(
                getClass().getResourceAsStream("/ua/bookloom/llm/openrouter/catalog-modalities.json"))) {
            server.stubFor(get(urlEqualTo("/api/v1/models"))
                    .willReturn(okJson(new String(in.readAllBytes(), StandardCharsets.UTF_8))));
        }
        verifier = Guice.createInjector(
                        com.google.inject.util.Modules.override(new LlmModule()).with(new AbstractModule() {
                            @Override
                            protected void configure() {
                                bind(ProviderConfigs.class).toInstance(configs);
                                bind(ModelSettingsRepository.class).toInstance(new FakeModelSettingsRepository());
                            }
                        }))
                .getInstance(ProviderVerifier.class);
    }

    @AfterEach
    void stopServer() {
        server.stop();
    }

    // IF the catalog's image-only entries were listed, THEN a person could pick a model that cannot translate.
    @Test
    void verify_openRouter_modelsStageListsTheCatalog() {
        final VerificationReport listed = report("example/text-chat");
        final VerificationReport imageOnly = report("example/image-maker");

        assertThat(listed.stages())
                .extracting(StageOutcome::stage, StageOutcome::status)
                .containsExactly(
                        tuple(VerificationStage.CONNECTION, StageStatus.PASSED),
                        tuple(VerificationStage.MODELS, StageStatus.PASSED));
        assertThat(imageOnly.stages().get(1).status()).isEqualTo(StageStatus.FAILED);
    }

    private VerificationReport report(final String modelId) {
        return Objects.requireNonNull(
                verifier.verify(new ModelSelection("openrouter", modelId), VerificationPolicy.CONNECTION_AND_MODELS)
                        .data(),
                "report");
    }
}
