package ua.bookloom.llm.verify;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
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
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.llm.ChatMessage;
import ua.bookloom.api.llm.ChatModelFactory;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ChatRole;
import ua.bookloom.api.llm.ModelSelection;
import ua.bookloom.api.llm.ModelSettings;
import ua.bookloom.api.llm.ProbeVerdicts;
import ua.bookloom.api.llm.ProviderConfig;
import ua.bookloom.api.llm.ProviderConfigs;
import ua.bookloom.api.llm.ProviderKind;
import ua.bookloom.api.llm.ProviderVerifier;
import ua.bookloom.api.llm.ReasoningLevel;
import ua.bookloom.api.llm.ServerEngine;
import ua.bookloom.api.llm.VerificationPolicy;
import ua.bookloom.api.persistence.ModelSettingsRepository;
import ua.bookloom.api.pipeline.CallKind;
import ua.bookloom.llm.LlmModule;
import ua.bookloom.llm.testing.FakeModelSettingsRepository;
import ua.bookloom.llm.testing.FakeProviderConfigs;

/** The inference check finds the reasoning value a hosted server accepts, and later calls send it (NVIDIA's Mistral). */
class ProviderVerifierFindsReasoningValueTest {

    private static final String CHAT = "/v1/chat/completions";
    private static final String MODEL = "vendor/chat-model";
    private static final String OK_REPLY = "{\"choices\":[{\"index\":0,\"finish_reason\":\"stop\",\"message\":"
            + "{\"role\":\"assistant\",\"content\":\"{\\\"status\\\":\\\"ok\\\"}\"}}]}";

    private final WireMockServer server =
            new WireMockServer(WireMockConfiguration.options().dynamicPort().bindAddress("127.0.0.1"));
    private final FakeModelSettingsRepository settings = new FakeModelSettingsRepository();
    private Injector injector;

    @BeforeEach
    void startServer() {
        server.start();
        final ProviderConfigs configs = new FakeProviderConfigs();
        assertThat(configs.save(new ProviderConfig(
                                        "hosted",
                                        ProviderKind.OPENAI_COMPATIBLE,
                                        URI.create(server.baseUrl() + "/v1"),
                                        Duration.ofSeconds(2),
                                        Duration.ofSeconds(3))
                                .withEngine(ServerEngine.GENERIC, ServerEngine.GENERIC, null))
                        .isOk())
                .isTrue();
        server.stubFor(get(urlEqualTo("/v1/models"))
                .willReturn(
                        okJson("{\"object\":\"list\",\"data\":[{\"id\":\"" + MODEL + "\",\"object\":\"model\"}]}")));
        injector = Guice.createInjector(Modules.override(new LlmModule()).with(new AbstractModule() {
            @Override
            protected void configure() {
                bind(ProviderConfigs.class).toInstance(configs);
                bind(ModelSettingsRepository.class).toInstance(settings);
            }
        }));
    }

    @AfterEach
    void stopServer() {
        server.stop();
    }

    // IF the check dropped the reasoning control when "none" was refused, THEN a model that accepts "low" would think
    // at its default effort on every run call; the lowest accepted value must be found, kept and sent later.
    @Test
    void verify_noneRefusedLowAccepted_lowIsKeptAndSentByLaterCalls() {
        stubChat(400, "reasoning_effort\":\"none", "{\"message\":\"Invalid request\",\"code\":400}");
        server.stubFor(post(urlEqualTo(CHAT)).atPriority(5).willReturn(okJson(OK_REPLY)));

        injector.getInstance(ProviderVerifier.class)
                .verify(new ModelSelection("hosted", MODEL), VerificationPolicy.FULL);

        final ProbeVerdicts.ThinkingVerdict found = storedThinking();
        assertThat(found.switchName()).isEqualTo("reasoning_effort:low");
        assertThat(found.verdict().outcome()).isEqualTo(ProbeVerdicts.Outcome.HONOURED);

        server.resetRequests();
        Objects.requireNonNull(injector.getInstance(ChatModelFactory.class)
                        .create(new ModelSelection("hosted", MODEL))
                        .data())
                .chat(draft());

        server.verify(postRequestedFor(urlEqualTo(CHAT)).withRequestBody(containing("\"reasoning_effort\":\"low\"")));
    }

    // IF an accepted "none" with a valid answer inside the probe's small cap were not kept, THEN the model would run
    // with no reasoning control at all although the server takes one.
    @Test
    void verify_noneAccepted_noneIsKeptAndSentByLaterCalls() {
        server.stubFor(post(urlEqualTo(CHAT)).willReturn(okJson(OK_REPLY)));

        injector.getInstance(ProviderVerifier.class)
                .verify(new ModelSelection("hosted", MODEL), VerificationPolicy.FULL);

        final ProbeVerdicts.ThinkingVerdict found = storedThinking();
        assertThat(found.switchName()).isEqualTo("reasoning_effort");

        server.resetRequests();
        Objects.requireNonNull(injector.getInstance(ChatModelFactory.class)
                        .create(new ModelSelection("hosted", MODEL))
                        .data())
                .chat(draft());

        server.verify(postRequestedFor(urlEqualTo(CHAT)).withRequestBody(containing("\"reasoning_effort\":\"none\"")));
    }

    private ProbeVerdicts.ThinkingVerdict storedThinking() {
        final Optional<ModelSettings> stored =
                Objects.requireNonNull(settings.find("hosted", MODEL).data(), "result");
        return Objects.requireNonNull(stored.orElseThrow().probeVerdicts().thinkingSwitch(), "thinking verdict");
    }

    private void stubChat(final int status, final String bodyHas, final String reply) {
        server.stubFor(post(urlEqualTo(CHAT))
                .atPriority(1)
                .withRequestBody(containing(bodyHas))
                .willReturn(aResponse()
                        .withStatus(status)
                        .withHeader("Content-Type", "application/json")
                        .withBody(reply)));
    }

    private static ChatRequest draft() {
        return new ChatRequest(
                List.of(new ChatMessage(ChatRole.USER, "Translate: a red door.")),
                0.2,
                null,
                ReasoningLevel.OFF,
                null,
                null,
                200,
                null,
                CallKind.DRAFT);
    }
}
