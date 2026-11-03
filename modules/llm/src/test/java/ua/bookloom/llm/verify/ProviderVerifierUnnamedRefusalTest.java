package ua.bookloom.llm.verify;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.notContaining;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import java.io.IOException;
import java.net.URI;
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

/** The inference check on a server that refuses a field with a body that does not say which one (NVIDIA's hosted API). */
class ProviderVerifierUnnamedRefusalTest {

    private final WireMockServer server =
            new WireMockServer(WireMockConfiguration.options().dynamicPort().bindAddress("127.0.0.1"));
    private ProviderVerifier verifier;

    @BeforeEach
    void startServer() throws IOException {
        server.start();
        final ProviderConfigs configs = new FakeProviderConfigs();
        final ProviderConfig config = new ProviderConfig(
                        "hosted",
                        ProviderKind.OPENAI_COMPATIBLE,
                        URI.create(server.baseUrl() + "/v1"),
                        Duration.ofSeconds(2),
                        Duration.ofSeconds(3))
                .withEngine(ServerEngine.GENERIC, ServerEngine.GENERIC, null);
        assertThat(configs.save(config).isOk()).isTrue();
        stubChat();
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

    private void stubChat() {
        server.stubFor(get(urlEqualTo("/v1/models"))
                .willReturn(okJson(
                        "{\"object\":\"list\",\"data\":[{\"id\":\"vendor/chat-model\",\"object\":\"model\"}]}")));
        server.stubFor(post(urlEqualTo("/v1/chat/completions"))
                .withRequestBody(containing("reasoning_effort"))
                .willReturn(aResponse()
                        .withStatus(400)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"message\":\"Invalid request\",\"type\":\"Bad Request\",\"code\":400}")));
        server.stubFor(post(urlEqualTo("/v1/chat/completions"))
                .withRequestBody(notContaining("reasoning_effort"))
                .willReturn(okJson("{\"choices\":[{\"index\":0,\"finish_reason\":\"stop\",\"message\":"
                        + "{\"role\":\"assistant\",\"content\":\"{\\\"status\\\":\\\"ok\\\"}\"}}]}")));
    }

    @AfterEach
    void stopServer() {
        server.stop();
    }

    // IF a refusal that names no field ended the inference check, THEN a hosted server that cannot switch reasoning
    // off could never be tested; the check must retry without the reasoning control and pass.
    @Test
    void verify_refusalNamingNoField_inferencePassesWithoutTheReasoningControl() {
        final VerificationReport report = Objects.requireNonNull(
                verifier.verify(new ModelSelection("hosted", "vendor/chat-model"), VerificationPolicy.FULL)
                        .data(),
                "report");

        assertThat(report.stages())
                .extracting(StageOutcome::stage, StageOutcome::status)
                .contains(tuple(VerificationStage.INFERENCE, StageStatus.PASSED));
    }
}
