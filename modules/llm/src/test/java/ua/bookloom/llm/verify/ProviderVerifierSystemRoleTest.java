package ua.bookloom.llm.verify;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import com.google.inject.Guice;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import ua.bookloom.api.llm.ModelSelection;
import ua.bookloom.api.llm.ModelSettings;
import ua.bookloom.api.llm.ProviderConfig;
import ua.bookloom.api.llm.ProviderKind;
import ua.bookloom.api.llm.ProviderVerifier;
import ua.bookloom.api.llm.RefusedSetting;
import ua.bookloom.api.llm.StageOutcome;
import ua.bookloom.api.llm.StageStatus;
import ua.bookloom.api.llm.VerificationPolicy;
import ua.bookloom.api.llm.VerificationReport;
import ua.bookloom.llm.LlmModule;
import ua.bookloom.llm.testing.FakeProviderConfigs;
import ua.bookloom.llm.testing.LlmTestStorageModule;

/**
 * Test's inference probe is laid out the way the model's template reads it (design D20): a model whose profile folds
 * the system text is sent none, and a template that refuses the system role gets the probe again folded, and the
 * refusal is remembered on the model like a run's call does — on both dialects.
 */
class ProviderVerifierSystemRoleTest {

    private static final String PROVIDER_ID = "system-role";
    private static final String PLAIN_MODEL = "plain-model";
    private static final String GEMMA3 = "gemma3:12b";

    /** Where each dialect lists models and chats, and its answer to the probe. */
    enum Dialect {
        OLLAMA(
                ProviderKind.OLLAMA,
                "",
                "/api/tags",
                "/api/chat",
                "{\"model\":\"m\",\"message\":{\"role\":\"assistant\",\"content\":\"{\\\"status\\\":\\\"ok\\\"}\"},"
                        + "\"done\":true,\"done_reason\":\"stop\"}"),
        OPENAI(
                ProviderKind.OPENAI_COMPATIBLE,
                "/v1",
                "/v1/models",
                "/v1/chat/completions",
                "{\"model\":\"m\",\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":"
                        + "\"{\\\"status\\\":\\\"ok\\\"}\"},\"finish_reason\":\"stop\"}]}");

        private final ProviderKind kind;
        private final String suffix;
        private final String modelsPath;
        private final String chatPath;
        private final String answer;

        Dialect(
                final ProviderKind kind,
                final String suffix,
                final String modelsPath,
                final String chatPath,
                final String answer) {
            this.kind = kind;
            this.suffix = suffix;
            this.modelsPath = modelsPath;
            this.chatPath = chatPath;
            this.answer = answer;
        }

        String models(final String model) {
            return kind == ProviderKind.OLLAMA
                    ? "{\"models\":[{\"name\":\"" + model + "\"}]}"
                    : "{\"object\":\"list\",\"data\":[{\"id\":\"" + model + "\",\"object\":\"model\"}]}";
        }
    }

    private final WireMockServer server =
            new WireMockServer(WireMockConfiguration.options().dynamicPort().bindAddress("127.0.0.1"));
    private final ObjectMapper json = new ObjectMapper();
    private FakeProviderConfigs configs;
    private LlmTestStorageModule storage;

    @BeforeEach
    void start() {
        server.start();
        configs = new FakeProviderConfigs();
        storage = new LlmTestStorageModule(configs);
        server.stubFor(get(urlEqualTo("/api/version")).willReturn(okJson("{\"version\":\"0.6.0\"}")));
    }

    @AfterEach
    void stop() {
        server.stop();
    }

    @ParameterizedTest
    @EnumSource(Dialect.class)
    void verify_templateRefusesTheSystemRole_resendsFoldedAndRemembers(final Dialect dialect) {
        register(dialect, PLAIN_MODEL);
        server.stubFor(post(urlEqualTo(dialect.chatPath))
                .inScenario("system role")
                .whenScenarioStateIs(Scenario.STARTED)
                .willReturn(aResponse()
                        .withStatus(400)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"error\":\"System role not supported\"}"))
                .willSetStateTo("folded"));
        server.stubFor(post(urlEqualTo(dialect.chatPath))
                .inScenario("system role")
                .whenScenarioStateIs("folded")
                .willReturn(okJson(dialect.answer)));

        final StageOutcome inference = inference(PLAIN_MODEL);

        assertThat(inference.status()).isEqualTo(StageStatus.PASSED);
        assertThat(roles(dialect)).containsExactly(List.of("system", "user"), List.of("user"));
        assertThat(stored(PLAIN_MODEL).refused())
                .extracting(RefusedSetting::name, RefusedSetting::scope)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("system_role", null));
    }

    @ParameterizedTest
    @EnumSource(Dialect.class)
    void verify_profileFoldsTheSystemText_probeCarriesNoSystemMessage(final Dialect dialect) {
        register(dialect, GEMMA3);
        server.stubFor(post(urlEqualTo(dialect.chatPath)).willReturn(okJson(dialect.answer)));

        final StageOutcome inference = inference(GEMMA3);

        assertThat(inference.status()).isEqualTo(StageStatus.PASSED);
        assertThat(roles(dialect)).containsOnly(List.of("user"));
    }

    private StageOutcome inference(final String model) {
        final ProviderVerifier verifier = Guice.createInjector(new LlmModule(Map.<String, String>of()::get), storage)
                .getInstance(ProviderVerifier.class);
        final VerificationReport report = Objects.requireNonNull(
                verifier.verify(new ModelSelection(PROVIDER_ID, model), VerificationPolicy.FULL)
                        .data(),
                "report");
        return report.stages().getLast();
    }

    private void register(final Dialect dialect, final String model) {
        server.stubFor(get(urlEqualTo(dialect.modelsPath)).willReturn(okJson(dialect.models(model))));
        final ProviderConfig config = new ProviderConfig(
                PROVIDER_ID,
                dialect.kind,
                URI.create(server.baseUrl() + dialect.suffix),
                Duration.ofSeconds(2),
                Duration.ofSeconds(3));
        assertThat(configs.save(config).isOk()).isTrue();
    }

    private List<List<String>> roles(final Dialect dialect) {
        return server.findAll(postRequestedFor(urlEqualTo(dialect.chatPath))).stream()
                .map(sent -> read(sent.getBodyAsString()))
                .map(body -> StreamSupport.stream(body.path("messages").spliterator(), false)
                        .map(message -> message.path("role").asText())
                        .toList())
                .toList();
    }

    private ModelSettings stored(final String model) {
        return Objects.requireNonNull(
                        storage.modelSettings().find(PROVIDER_ID, model).data(), "stored settings")
                .orElseThrow();
    }

    private JsonNode read(final String body) {
        try {
            return json.readTree(body);
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }
}
