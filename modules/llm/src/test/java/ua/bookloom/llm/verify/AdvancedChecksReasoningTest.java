package ua.bookloom.llm.verify;

import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
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
import ua.bookloom.api.llm.ModelSelection;
import ua.bookloom.api.llm.ModelSettings;
import ua.bookloom.api.llm.OutputLengthField;
import ua.bookloom.api.llm.ProbeVerdicts;
import ua.bookloom.api.llm.ProbeVerdicts.Outcome;
import ua.bookloom.api.llm.ProviderConfig;
import ua.bookloom.api.llm.ProviderConfigs;
import ua.bookloom.api.llm.ProviderKind;
import ua.bookloom.api.llm.ProviderVerifier;
import ua.bookloom.api.llm.ServerEngine;
import ua.bookloom.api.llm.VerificationPolicy;
import ua.bookloom.api.llm.VerificationReport;
import ua.bookloom.api.persistence.ModelSettingsRepository;
import ua.bookloom.llm.LlmModule;
import ua.bookloom.llm.retry.RetryPolicy;
import ua.bookloom.llm.testing.FakeModelSettingsRepository;
import ua.bookloom.llm.testing.FakeProviderConfigs;

/** A thinking model that spends a check's small cap on reasoning is asked again with headroom, not given a verdict. */
class AdvancedChecksReasoningTest {

    private static final String PROVIDER = "ollama-v1";
    private static final String MODEL = "gemma-e4b";
    private static final String CHAT = "/v1/chat/completions";
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-30T12:00:00Z"), ZoneOffset.UTC);

    private final WireMockServer server =
            new WireMockServer(WireMockConfiguration.options().dynamicPort().bindAddress("127.0.0.1"));
    private final FakeProviderConfigs configs = new FakeProviderConfigs();
    private final ModelSettingsRepository settings = new FakeModelSettingsRepository();

    @BeforeEach
    void startServer() {
        server.start();
        final ProviderConfig base = new ProviderConfig(
                PROVIDER,
                ProviderKind.OPENAI_COMPATIBLE,
                URI.create(server.baseUrl() + "/v1"),
                Duration.ofSeconds(2),
                Duration.ofSeconds(5));
        assertThat(configs.save(base.withEngine(ServerEngine.OLLAMA, ServerEngine.OLLAMA, null))
                        .isOk())
                .isTrue();
        server.stubFor(get(urlEqualTo("/v1/models"))
                .willReturn(okJson("{\"object\":\"list\",\"data\":[{\"id\":\"" + MODEL + "\"}]}")));
        server.stubFor(post(urlEqualTo(CHAT)).atPriority(10).willReturn(okJson(reply("Fine.", "stop", ""))));
        server.stubFor(post(urlEqualTo(CHAT))
                .withRequestBody(containing("verification_probe"))
                .atPriority(5)
                .willReturn(okJson(reply("{\"status\":\"ok\"}", "stop", ""))));
    }

    @AfterEach
    void stopServer() {
        server.stop();
    }

    @Test
    void schema_lowCapSpentOnReasoning_isAskedAgainWithHeadroomAndHonoured() {
        server.stubFor(post(urlEqualTo(CHAT))
                .withRequestBody(containing("advanced_check"))
                .withRequestBody(matchingJsonPath("$.max_tokens", equalTo("64")))
                .atPriority(1)
                .willReturn(okJson(reply("", "length", ",\"reasoning\":\"Thinking Process: 1.\""))));
        server.stubFor(post(urlEqualTo(CHAT))
                .withRequestBody(containing("advanced_check"))
                .withRequestBody(matchingJsonPath("$.max_tokens", equalTo("1088")))
                .atPriority(1)
                .willReturn(okJson(reply("{\"k\":\"ZX-91\"}", "stop", ""))));

        final VerificationReport report = verifier()
                .verify(new ModelSelection(PROVIDER, MODEL), VerificationPolicy.ADVANCED)
                .data();

        assertThat(Objects.requireNonNull(report).stages()).hasSize(4);
        final ProbeVerdicts found = Objects.requireNonNull(
                        settings.find(PROVIDER, MODEL).data())
                .map(ModelSettings::probeVerdicts)
                .orElse(ProbeVerdicts.none());
        assertThat(Objects.requireNonNull(found.answerSchema()).outcome()).isEqualTo(Outcome.HONOURED);
        assertThat(found.thinkingSwitch()).isNotNull();
        assertThat(found.extraSamplers()).isNull();
        assertThat(found.capField()).isNull();
    }

    @Test
    void cap_genericServerThinkingModelSpendsSixteenTokens_isHonouredForTheFieldTried() {
        useGenericEngine();
        server.stubFor(post(urlEqualTo(CHAT))
                .withRequestBody(containing("Count from 1 to 100"))
                .atPriority(1)
                .willReturn(okJson(reply("", "length", ",\"reasoning\":\"Thinking Process: 1.\""))));

        verifier().verify(new ModelSelection(PROVIDER, MODEL), VerificationPolicy.ADVANCED);

        final ModelSettings stored = storedSettings();
        assertThat(Objects.requireNonNull(stored.probeVerdicts().capField()).outcome())
                .isEqualTo(Outcome.HONOURED);
        assertThat(stored.outputField()).isEqualTo(OutputLengthField.MAX_COMPLETION_TOKENS);
    }

    @Test
    void cap_genericServerEmptyReplyThatStoppedNormally_givesNoVerdict() {
        useGenericEngine();
        server.stubFor(post(urlEqualTo(CHAT))
                .withRequestBody(containing("Count from 1 to 100"))
                .atPriority(1)
                .willReturn(okJson(reply("", "stop", ""))));

        verifier().verify(new ModelSelection(PROVIDER, MODEL), VerificationPolicy.ADVANCED);

        assertThat(storedSettings().probeVerdicts().capField()).isNull();
    }

    private void useGenericEngine() {
        final ProviderConfig base = new ProviderConfig(
                PROVIDER,
                ProviderKind.OPENAI_COMPATIBLE,
                URI.create(server.baseUrl() + "/v1"),
                Duration.ofSeconds(2),
                Duration.ofSeconds(5));
        assertThat(configs.save(base.withEngine(ServerEngine.GENERIC, ServerEngine.GENERIC, null))
                        .isOk())
                .isTrue();
    }

    private ModelSettings storedSettings() {
        return Objects.requireNonNull(settings.find(PROVIDER, MODEL).data()).orElseThrow();
    }

    private static String reply(final String content, final String finish, final String extra) {
        return "{\"model\":\"" + MODEL + "\",\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\""
                + content.replace("\"", "\\\"") + "\"" + extra + "},\"finish_reason\":\"" + finish
                + "\"}],\"usage\":{\"prompt_tokens\":12,\"completion_tokens\":64}}";
    }

    private ProviderVerifier verifier() {
        return Guice.createInjector(Modules.override(new LlmModule()).with(new AbstractModule() {
                    @Override
                    protected void configure() {
                        bind(ProviderConfigs.class).toInstance(configs);
                        bind(ModelSettingsRepository.class).toInstance(settings);
                        bind(RetryPolicy.class).toInstance(new RetryPolicy(CLOCK, () -> 0.5, delay -> {}));
                    }
                }))
                .getInstance(ProviderVerifier.class);
    }
}
