package ua.bookloom.llm.verify;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ProviderAuth;
import ua.bookloom.api.llm.ProviderConfig;
import ua.bookloom.api.llm.ProviderKind;
import ua.bookloom.api.llm.StageOutcome;
import ua.bookloom.api.llm.StageStatus;
import ua.bookloom.api.llm.VerificationExchange;
import ua.bookloom.api.llm.VerificationPolicy;
import ua.bookloom.api.llm.VerificationReport;
import ua.bookloom.api.llm.VerificationStage;
import ua.bookloom.llm.LlmModule;
import ua.bookloom.llm.gate.InferenceGate;
import ua.bookloom.llm.http.HttpClients;
import ua.bookloom.llm.http.HttpExchange;
import ua.bookloom.llm.provider.ProviderClientFactory;
import ua.bookloom.llm.retry.RetryPolicy;
import ua.bookloom.llm.testing.FakeProviderConfigs;

/** Verifying an unsaved provider description: signed in, never registered, nothing leaked. */
class ProviderVerifierImplDraftTest {

    private static final String KEY_VARIABLE = "BOOKLOOM_TEST_KEY";
    private static final String KEY_VALUE = "sk-test-123";
    private static final String MODEL_ID = "test-model";
    private static final String BEARER = "Bearer " + KEY_VALUE;
    private static final String OPENAI_MODEL_REPLY =
            "{\"object\":\"list\",\"data\":[{\"id\":\"test-model\",\"object\":\"model\"}]}";
    private static final String OPENAI_CHAT_REPLY =
            "{\"model\":\"test-model\",\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"{\\\"status\\\":\\\"ok\\\"}\"},\"finish_reason\":\"stop\"}]}";
    private static final String OLLAMA_CHAT_REPLY =
            "{\"model\":\"test-model\",\"message\":{\"role\":\"assistant\",\"content\":\"{\\\"status\\\":\\\"ok\\\"}\"},\"done\":true,\"done_reason\":\"stop\"}";
    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-09-22T12:00:00Z"), ZoneOffset.UTC);

    private final WireMockServer server =
            new WireMockServer(WireMockConfiguration.options().dynamicPort().bindAddress("127.0.0.1"));
    private FakeProviderConfigs configs;
    private @Nullable ProviderClientFactory factory;

    @BeforeEach
    void start() {
        server.start();
        configs = new FakeProviderConfigs();
    }

    @AfterEach
    void stop() {
        server.stop();
    }

    // IF an unsaved bearer-signed provider is verified, THEN every stage sends the bearer header and nothing is
    // registered.
    @Test
    void verifyDraft_unsavedOpenAiBearer_passesWithBearerAndIsNotRegistered() {
        stubOpenAiSigned();

        final VerificationReport report = report(verifier(Map.of(KEY_VARIABLE, KEY_VALUE))
                .verify(openAiDraft("draft-remote"), MODEL_ID, VerificationPolicy.PREFLIGHT));

        assertThat(report.isPassed()).isTrue();
        assertThat(configs.find("draft-remote")).isEmpty();
    }

    // IF the draft has the id of a saved provider, THEN the saved config and its cached client are untouched.
    @Test
    void verifyDraft_sameIdAsSavedProvider_leavesSavedConfigAndClientUntouched() {
        stubOpenAiSigned();
        final ProviderConfig saved = openAiDraft("remote").withBaseUrl(URI.create("http://localhost:1234/v1"));
        assertThat(configs.save(saved).isOk()).isTrue();
        final ProviderVerifierImpl verifier = verifier(Map.of(KEY_VARIABLE, KEY_VALUE));
        final var cachedBefore = Objects.requireNonNull(factory, "factory").create(saved);

        final VerificationReport report =
                report(verifier.verify(openAiDraft("remote"), MODEL_ID, VerificationPolicy.PREFLIGHT));

        assertThat(report.isPassed()).isTrue();
        assertThat(configs.find("remote")).contains(saved);
        assertThat(Objects.requireNonNull(factory, "factory").create(saved)).isSameAs(cachedBefore);
    }

    // IF the draft's key variable is unset, THEN the connection stage fails with missingCredential and nothing is sent.
    @Test
    void verifyDraft_unsetKey_failsConnectionWithMissingCredentialAndSendsNothing() {
        final VerificationReport report =
                report(verifier(Map.of()).verify(openAiDraft("draft-remote"), MODEL_ID, VerificationPolicy.PREFLIGHT));

        final StageOutcome connection = report.stages().get(0);
        assertThat(connection.stage()).isEqualTo(VerificationStage.CONNECTION);
        assertThat(connection.status()).isEqualTo(StageStatus.FAILED);
        assertThat(Objects.requireNonNull(connection.error(), "error").code()).isEqualTo(ErrorCode.missingCredential);
        assertThat(server.getAllServeEvents()).isEmpty();
    }

    // IF an Ollama draft overrides the models path, THEN the models stage lists from the override.
    @Test
    void verifyDraft_ollamaModelsPathOverride_listsFromTheOverride() {
        server.stubFor(get(urlEqualTo("/api/version")).willReturn(okJson("{\"version\":\"0.6.0\"}")));
        server.stubFor(get(urlEqualTo("/catalog/tags")).willReturn(okJson("{\"models\":[{\"name\":\"test-model\"}]}")));
        server.stubFor(post(urlEqualTo("/api/chat")).willReturn(okJson(OLLAMA_CHAT_REPLY)));
        final ProviderConfig draft = new ProviderConfig(
                        "draft-ollama",
                        ProviderKind.OLLAMA,
                        URI.create(server.baseUrl()),
                        Duration.ofSeconds(2),
                        Duration.ofSeconds(3))
                .withModelsPath("/catalog/tags");

        final VerificationReport report =
                report(verifier(Map.of()).verify(draft, MODEL_ID, VerificationPolicy.PREFLIGHT));

        assertThat(report.isPassed()).isTrue();
        server.verify(1, getRequestedFor(urlEqualTo("/catalog/tags")));
        server.verify(0, getRequestedFor(urlEqualTo("/api/tags")));
    }

    // IF the rules refuse the draft, THEN verification is a validation error and no request is sent.
    @Test
    void verifyDraft_invalidConfig_isValidationErrorWithoutRequest() {
        final ProviderConfig invalid = new ProviderConfig(
                        "draft-ollama",
                        ProviderKind.OLLAMA,
                        URI.create(server.baseUrl()),
                        Duration.ofSeconds(2),
                        Duration.ofSeconds(3))
                .withChatPath("/custom/chat");

        final Result<VerificationReport> result =
                verifier(Map.of()).verify(invalid, MODEL_ID, VerificationPolicy.PREFLIGHT);

        assertThat(Objects.requireNonNull(result.error(), "error").code()).isEqualTo(ErrorCode.validation);
        assertThat(server.getAllServeEvents()).isEmpty();
    }

    // IF the inference stage ran with a key, THEN its exchange shows the masked header and no key appears anywhere.
    @Test
    void verifyDraft_inferenceExchange_carriesMaskedHeadersAndNoKey() {
        stubOpenAiSigned();

        final VerificationReport report = report(verifier(Map.of(KEY_VARIABLE, KEY_VALUE))
                .verify(openAiDraft("draft-remote"), MODEL_ID, VerificationPolicy.PREFLIGHT));

        final VerificationExchange exchange =
                Objects.requireNonNull(report.stages().get(2).exchange(), "inference exchange");
        assertThat(exchange.requestHeaders()).contains("Authorization: Bearer ••••");
        assertThat(exchange.toString()).doesNotContain(KEY_VALUE);
        assertThat(report.toString()).doesNotContain(KEY_VALUE);
    }

    // IF the structured probe is rejected and the plain probe answers, THEN inference passes with "not confirmed".
    @Test
    void verifyDraft_structuredRejectedPlainOk_passesNotConfirmed() {
        server.stubFor(get(urlEqualTo("/v1/models")).willReturn(okJson(OPENAI_MODEL_REPLY)));
        server.stubFor(post(urlEqualTo("/v1/chat/completions"))
                .inScenario("probe")
                .whenScenarioStateIs(Scenario.STARTED)
                .willReturn(aResponse().withStatus(400).withBody("{\"error\":\"response_format unsupported\"}"))
                .willSetStateTo("plain"));
        server.stubFor(post(urlEqualTo("/v1/chat/completions"))
                .inScenario("probe")
                .whenScenarioStateIs("plain")
                .willReturn(okJson(OPENAI_CHAT_REPLY)));
        final ProviderConfig draft = new ProviderConfig(
                "draft-remote",
                ProviderKind.OPENAI_COMPATIBLE,
                URI.create(server.baseUrl() + "/v1"),
                Duration.ofSeconds(2),
                Duration.ofSeconds(3));

        final VerificationReport report =
                report(verifier(Map.of()).verify(draft, MODEL_ID, VerificationPolicy.PREFLIGHT));

        assertThat(report.stages().get(2).status()).isEqualTo(StageStatus.PASSED);
        assertThat(report.stages().get(2).note()).isEqualTo("structured output: not confirmed");
    }

    private void stubOpenAiSigned() {
        server.stubFor(get(urlEqualTo("/v1/models"))
                .withHeader("Authorization", equalTo(BEARER))
                .willReturn(okJson(OPENAI_MODEL_REPLY)));
        server.stubFor(post(urlEqualTo("/v1/chat/completions"))
                .withHeader("Authorization", equalTo(BEARER))
                .willReturn(okJson(OPENAI_CHAT_REPLY)));
    }

    private ProviderConfig openAiDraft(String id) {
        return new ProviderConfig(
                id,
                ProviderKind.OPENAI_COMPATIBLE,
                URI.create(server.baseUrl() + "/v1"),
                Duration.ofSeconds(2),
                Duration.ofSeconds(3),
                null,
                ProviderAuth.BEARER,
                null,
                KEY_VARIABLE,
                null,
                null,
                Map.of(),
                List.of());
    }

    private ProviderVerifierImpl verifier(Map<String, String> env) {
        final var mapper = new LlmModule().objectMapper();
        factory = new ProviderClientFactory(
                new HttpExchange(new HttpClients(), System::nanoTime, env::get), mapper, System::nanoTime);
        return new ProviderVerifierImpl(
                configs,
                factory,
                new InferenceGate(),
                new RetryPolicy(FIXED_CLOCK, () -> 0.5, delay -> {}),
                mapper,
                System::nanoTime);
    }

    private static VerificationReport report(Result<VerificationReport> result) {
        assertThat(result.isOk()).as("result: " + result.error()).isTrue();
        return Objects.requireNonNull(result.data(), "verification report");
    }
}
