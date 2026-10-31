package ua.bookloom.llm.verify;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.AdvancedCheck;
import ua.bookloom.api.llm.ProbeCall;
import ua.bookloom.api.llm.ProviderAuth;
import ua.bookloom.api.llm.ProviderConfig;
import ua.bookloom.api.llm.ProviderKind;
import ua.bookloom.api.llm.StageOutcome;
import ua.bookloom.api.llm.VerificationExchange;
import ua.bookloom.api.llm.VerificationListener;
import ua.bookloom.api.llm.VerificationPolicy;
import ua.bookloom.api.llm.VerificationReport;
import ua.bookloom.api.llm.VerificationStage;
import ua.bookloom.llm.LlmModule;
import ua.bookloom.llm.engine.EngineDetector;
import ua.bookloom.llm.engine.EngineFacts;
import ua.bookloom.llm.engine.EngineRegistry;
import ua.bookloom.llm.gate.InferenceGate;
import ua.bookloom.llm.http.HttpClients;
import ua.bookloom.llm.http.HttpExchange;
import ua.bookloom.llm.provider.ProviderClientFactory;
import ua.bookloom.llm.retry.RetryPolicy;
import ua.bookloom.llm.testing.FakeModelSettingsRepository;
import ua.bookloom.llm.testing.FakeProviderConfigs;

/**
 * The test card hears each model request before it is sent, masked as the report is, with the bodies laid out for
 * reading; proved at the HTTP seam for both dialects with a reply held back by the server.
 */
class ProviderVerifierAnnouncementTest {

    private static final String MODEL_ID = "test-model";
    private static final String KEY_VARIABLE = "BOOKLOOM_TEST_KEY";
    private static final String KEY_VALUE = "sk-test-123";
    private static final int REPLY_DELAY_MS = 300;
    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-10-31T12:00:00Z"), ZoneOffset.UTC);
    private static final String OLLAMA_REPLY =
            "{\"model\":\"test-model\",\"message\":{\"role\":\"assistant\",\"content\":\"{\\\"status\\\":\\\"ok\\\"}\"},"
                    + "\"done\":true,\"done_reason\":\"stop\",\"prompt_eval_count\":18,\"eval_count\":20,"
                    + "\"prompt_eval_duration\":41000000,\"eval_duration\":1000000000,\"load_duration\":2500000000}";
    private static final String OPENAI_REPLY =
            "{\"model\":\"test-model\",\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":"
                    + "\"{\\\"status\\\":\\\"ok\\\"}\"},\"finish_reason\":\"stop\"}],"
                    + "\"usage\":{\"prompt_tokens\":18,\"completion_tokens\":7}}";
    private static final String OPENAI_PLAIN_REPLY =
            "{\"model\":\"test-model\",\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"Fine.\"},"
                    + "\"finish_reason\":\"stop\"}],\"usage\":{\"prompt_tokens\":9,\"completion_tokens\":2}}";

    private final WireMockServer server =
            new WireMockServer(WireMockConfiguration.options().dynamicPort().bindAddress("127.0.0.1"));
    private final FakeProviderConfigs configs = new FakeProviderConfigs();
    private final Heard heard = new Heard();

    /** What the listener heard, and how many chat requests the server had received at each announcement. */
    private final class Heard implements VerificationListener {
        private final List<VerificationExchange> requests = new CopyOnWriteArrayList<>();
        private final List<@Nullable AdvancedCheck> checks = new CopyOnWriteArrayList<>();
        private final List<Integer> chatsServedWhenAnnounced = new CopyOnWriteArrayList<>();
        private final List<VerificationStage> finished = new CopyOnWriteArrayList<>();

        @Override
        public void requestSent(
                final VerificationStage stage,
                final @Nullable AdvancedCheck check,
                final VerificationExchange request) {
            requests.add(request);
            checks.add(check);
            chatsServedWhenAnnounced.add(chatsServed());
        }

        @Override
        public void stageFinished(final StageOutcome outcome) {
            finished.add(outcome.stage());
        }
    }

    @BeforeEach
    void start() {
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop();
    }

    // IF the request were announced only with the report, THEN the card would show nothing while the model works.
    @Test
    void verify_ollamaInferenceWithASlowReply_announcesTheRequestBeforeTheServerReceivesIt() {
        stubOllama(OLLAMA_REPLY);

        final VerificationReport report =
                report(verifier(Map.of()).verify(ollamaDraft(), MODEL_ID, VerificationPolicy.FULL, heard));

        assertThat(heard.requests).hasSize(1);
        assertThat(heard.chatsServedWhenAnnounced).containsExactly(0);
        assertThat(heard.checks).containsOnlyNulls();
        final VerificationExchange announced = heard.requests.getFirst();
        assertThat(announced.method()).isEqualTo("POST");
        assertThat(announced.url()).isEqualTo(server.baseUrl() + "/api/chat");
        assertThat(announced.requestBody()).contains("\"model\":\"test-model\"");
        assertThat(announced.httpStatus()).isZero();
        assertThat(announced.responseBody()).isEmpty();
        assertThat(report.isPassed()).isTrue();
    }

    // IF the OpenAI-compatible dialect announced after the reply, THEN the two dialects would differ in the card.
    @Test
    void verify_openAiInferenceWithASlowReply_announcesTheMaskedRequestBeforeTheServerReceivesIt() {
        stubOpenAi(OPENAI_REPLY);

        report(verifier(Map.of(KEY_VARIABLE, KEY_VALUE))
                .verify(openAiDraft(), MODEL_ID, VerificationPolicy.FULL, heard));

        assertThat(heard.chatsServedWhenAnnounced).containsExactly(0);
        final VerificationExchange announced = heard.requests.getFirst();
        assertThat(announced.url()).isEqualTo(server.baseUrl() + "/v1/chat/completions");
        assertThat(announced.requestHeaders())
                .contains("Authorization: Bearer ••••")
                .doesNotContain(KEY_VALUE);
        assertThat(announced.requestBody()).contains("max_tokens").doesNotContain(KEY_VALUE);
    }

    // IF stages were heard only at the end, THEN the card could not fill a finished stage while a later one runs.
    @Test
    void verify_fullPolicy_hearsEachStageEndInOrder() {
        stubOllama(OLLAMA_REPLY);

        report(verifier(Map.of()).verify(ollamaDraft(), MODEL_ID, VerificationPolicy.FULL, heard));

        assertThat(heard.finished)
                .containsExactly(VerificationStage.CONNECTION, VerificationStage.MODELS, VerificationStage.INFERENCE);
    }

    // IF the bodies were shown on one line, THEN a person would read a long request as one unbroken run of text.
    @Test
    void verify_jsonBodies_areLaidOutForReadingWhileTheWireBodyStaysOneLine() {
        stubOllama(OLLAMA_REPLY);

        final VerificationExchange exchange =
                inference(report(verifier(Map.of()).verify(ollamaDraft(), MODEL_ID, VerificationPolicy.FULL, heard)));

        assertThat(exchange.requestBody()).doesNotContain("\n").startsWith("{\"model\":\"test-model\"");
        assertThat(exchange.shownRequestBody()).startsWith("{\n  \"model\" : \"test-model\",\n");
        assertThat(exchange.responseBody()).isEqualTo("{\"status\":\"ok\"}");
        assertThat(exchange.shownResponseBody()).isEqualTo("{\n  \"status\" : \"ok\"\n}");
        assertThat(heard.requests.getFirst().shownRequestBody()).isEqualTo(exchange.shownRequestBody());
    }

    // IF a plain-text reply were reformatted, THEN the model's own words would be changed in the card.
    @Test
    void verify_plainTextReply_isShownUntouched() {
        server.stubFor(get(urlEqualTo("/v1/models"))
                .willReturn(okJson("{\"object\":\"list\",\"data\":[{\"id\":\"test-model\",\"object\":\"model\"}]}")));
        server.stubFor(post(urlEqualTo("/v1/chat/completions")).willReturn(okJson(OPENAI_PLAIN_REPLY)));

        final VerificationExchange exchange = inference(
                report(verifier(Map.of()).verify(openAiPlainDraft(), MODEL_ID, VerificationPolicy.FULL, heard)));

        assertThat(exchange.shownResponseBody()).isEqualTo("Fine.");
    }

    // IF Ollama's load time were dropped, THEN a slow first call would look like a slow model.
    @Test
    void verify_ollamaReplyReportsLoadDuration_exchangeCarriesTheLoadTime() {
        stubOllama(OLLAMA_REPLY);

        final VerificationExchange exchange =
                inference(report(verifier(Map.of()).verify(ollamaDraft(), MODEL_ID, VerificationPolicy.FULL, heard)));

        assertThat(exchange.loadMs()).isEqualTo(2500L);
    }

    // IF the Advanced calls were not heard one by one, THEN the card could not list each attempt as it goes out.
    @Test
    void verify_advancedPolicy_announcesEachCheckCallBeforeItIsSentAndReportsEachCallTimed() {
        stubOllama(OLLAMA_REPLY);
        server.stubFor(post(urlEqualTo("/api/chat"))
                .withRequestBody(containing("ZX-91"))
                .atPriority(1)
                .willReturn(okJson(OLLAMA_REPLY.replace("{\\\"status\\\":\\\"ok\\\"}", "{\\\"k\\\":\\\"ZX-91\\\"}"))));

        final VerificationReport report =
                report(verifier(Map.of()).verify(ollamaDraft(), MODEL_ID, VerificationPolicy.ADVANCED, heard));

        assertThat(heard.checks).containsExactly(null, AdvancedCheck.SCHEMA, AdvancedCheck.THINKING);
        assertThat(heard.chatsServedWhenAnnounced).containsExactly(0, 1, 2);
        final VerificationExchange advanced =
                Objects.requireNonNull(report.stages().get(3).exchange(), "advanced exchange");
        assertThat(advanced.calls())
                .extracting(ProbeCall::check)
                .containsExactly(AdvancedCheck.SCHEMA, AdvancedCheck.THINKING);
        assertThat(advanced.calls().getFirst().httpStatus()).isEqualTo(200);
        assertThat(advanced.calls().getFirst().elapsed()).isPositive();
        assertThat(advanced.calls().getFirst().tokensPerSecond()).isEqualTo(20.0);
        assertThat(advanced.calls().getFirst().shownResponseBody()).isEqualTo("{\n  \"k\" : \"ZX-91\"\n}");
        assertThat(heard.finished).endsWith(VerificationStage.ADVANCED);
    }

    private int chatsServed() {
        return (int) server.getAllServeEvents().stream()
                .filter(event -> event.getRequest().getUrl().contains("chat"))
                .count();
    }

    private void stubOllama(final String chatReply) {
        server.stubFor(get(urlEqualTo("/api/version")).willReturn(okJson("{\"version\":\"0.6.0\"}")));
        server.stubFor(get(urlEqualTo("/api/tags")).willReturn(okJson("{\"models\":[{\"name\":\"test-model\"}]}")));
        server.stubFor(post(urlEqualTo("/api/chat"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(chatReply)
                        .withFixedDelay(REPLY_DELAY_MS)));
    }

    private void stubOpenAi(final String chatReply) {
        server.stubFor(get(urlEqualTo("/v1/models"))
                .willReturn(okJson("{\"object\":\"list\",\"data\":[{\"id\":\"test-model\",\"object\":\"model\"}]}")));
        server.stubFor(post(urlEqualTo("/v1/chat/completions"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(chatReply)
                        .withFixedDelay(REPLY_DELAY_MS)));
    }

    private ProviderConfig ollamaDraft() {
        return new ProviderConfig(
                "draft-ollama",
                ProviderKind.OLLAMA,
                URI.create(server.baseUrl()),
                Duration.ofSeconds(2),
                Duration.ofSeconds(5));
    }

    private ProviderConfig openAiPlainDraft() {
        return new ProviderConfig(
                "draft-plain",
                ProviderKind.OPENAI_COMPATIBLE,
                URI.create(server.baseUrl() + "/v1"),
                Duration.ofSeconds(2),
                Duration.ofSeconds(5));
    }

    private ProviderConfig openAiDraft() {
        return openAiPlainDraft().withAuth(ProviderAuth.BEARER).withCredentialRef(KEY_VARIABLE);
    }

    private ProviderVerifierImpl verifier(final Map<String, String> env) {
        final var mapper = new LlmModule().objectMapper();
        final HttpExchange exchange = new HttpExchange(new HttpClients(), System::nanoTime, env::get);
        return new ProviderVerifierImpl(
                configs,
                new ProviderClientFactory(exchange, mapper, System::nanoTime),
                new InferenceGate(),
                new RetryPolicy(FIXED_CLOCK, () -> 0.5, delay -> {}),
                mapper,
                System::nanoTime,
                new EngineRegistry(configs, new EngineDetector(exchange, mapper), new EngineFacts(exchange, mapper)),
                new FakeModelSettingsRepository(),
                ProbeShapings.over(new FakeModelSettingsRepository()));
    }

    private static VerificationExchange inference(final VerificationReport report) {
        return Objects.requireNonNull(report.stages().get(2).exchange(), "inference exchange");
    }

    private static VerificationReport report(final Result<VerificationReport> result) {
        assertThat(result.isOk()).as("result: " + result.error()).isTrue();
        return Objects.requireNonNull(result.data(), "verification report");
    }
}
