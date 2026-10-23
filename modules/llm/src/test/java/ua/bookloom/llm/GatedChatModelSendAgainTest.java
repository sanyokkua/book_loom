package ua.bookloom.llm;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ChatMessage;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.ChatRole;
import ua.bookloom.api.llm.ModelSettings;
import ua.bookloom.api.llm.ProviderConfig;
import ua.bookloom.api.llm.ProviderKind;
import ua.bookloom.api.llm.ReasoningLevel;
import ua.bookloom.api.llm.SamplingParams;
import ua.bookloom.api.pipeline.CallKind;
import ua.bookloom.llm.gate.InferenceGate;
import ua.bookloom.llm.http.HttpClients;
import ua.bookloom.llm.http.HttpExchange;
import ua.bookloom.llm.provider.ModelSettingsCache;
import ua.bookloom.llm.provider.ProviderClientFactory;
import ua.bookloom.llm.provider.RefusalMemory;
import ua.bookloom.llm.retry.RetryPolicy;
import ua.bookloom.llm.testing.FakeModelSettingsRepository;

/** "Send again" on the Models tab forgets a refusal, and the very next request carries the setting once more. */
class GatedChatModelSendAgainTest {

    private static final String PROVIDER = "office";
    private static final String MODEL = "gpt-oss:20b";
    private static final String OPENAI_REPLY = "{\"model\":\"m\",\"choices\":[{\"message\":{\"role\":\"assistant\","
            + "\"content\":\"ok\"},\"finish_reason\":\"stop\"}]}";
    private static final String OLLAMA_REPLY =
            "{\"model\":\"m\",\"message\":{\"content\":\"ok\"},\"done\":true,\"done_reason\":\"stop\"}";
    private static final String TEMPERATURE_REFUSAL =
            "{\"error\":\"Unsupported parameter: 'temperature' is not supported with this model\"}";
    private static final String CSV = "OPENAI_COMPATIBLE, /v1, /v1/chat/completions";
    private static final String CSV_OLLAMA = "OLLAMA, '', /api/chat";
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-20T12:00:00Z"), ZoneOffset.UTC);

    private final WireMockServer server =
            new WireMockServer(WireMockConfiguration.options().dynamicPort().bindAddress("127.0.0.1"));
    private final FakeModelSettingsRepository repository = new FakeModelSettingsRepository();

    @BeforeEach
    void start() {
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop();
    }

    @ParameterizedTest
    @CsvSource({CSV, CSV_OLLAMA})
    void chat_refusalRemovedBySendAgain_nextRequestCarriesTemperature(
            final ProviderKind kind, final String basePath, final String chatPath) {
        stub(kind, chatPath);
        final GatedChatModel model = model(kind, basePath);
        model.chat(request());

        final Result<ModelSettings> sentAgain =
                repository.update(PROVIDER, MODEL, s -> s.withoutRefusal("temperature"));
        final Result<ChatResponse> next = model.chat(request());

        assertThat(sentAgain.isOk()).isTrue();
        assertThat(next.isOk()).isTrue();
        final List<String> bodies = bodies(chatPath);
        assertThat(bodies).hasSize(3);
        assertThat(bodies.get(0)).contains("\"temperature\":0.2");
        assertThat(bodies.get(1)).doesNotContain("temperature");
        assertThat(bodies.get(2)).contains("\"temperature\":0.2");
    }

    // First request is refused, then every request is answered.
    private void stub(final ProviderKind kind, final String chatPath) {
        final String okBody = kind == ProviderKind.OLLAMA ? OLLAMA_REPLY : OPENAI_REPLY;
        server.stubFor(post(urlEqualTo(chatPath))
                .inScenario("replies")
                .whenScenarioStateIs(Scenario.STARTED)
                .willReturn(aResponse().withStatus(400).withBody(TEMPERATURE_REFUSAL))
                .willSetStateTo("answered"));
        server.stubFor(post(urlEqualTo(chatPath))
                .inScenario("replies")
                .whenScenarioStateIs("answered")
                .willReturn(aResponse().withStatus(200).withBody(okBody)));
    }

    private List<String> bodies(final String chatPath) {
        final List<String> all = new ArrayList<>(server.getAllServeEvents().stream()
                .filter(event -> event.getRequest().getUrl().equals(chatPath))
                .map(event -> event.getRequest().getBodyAsString())
                .toList());
        Collections.reverse(all);
        return all;
    }

    private static ChatRequest request() {
        return new ChatRequest(
                List.of(new ChatMessage(ChatRole.USER, "Translate this.")),
                0.2,
                null,
                ReasoningLevel.OFF,
                8192,
                null,
                400,
                null,
                CallKind.DRAFT,
                new SamplingParams(null, null, null, null),
                null);
    }

    private GatedChatModel model(final ProviderKind kind, final String basePath) {
        final ProviderConfig config = new ProviderConfig(
                "test", kind, URI.create(server.baseUrl() + basePath), Duration.ofSeconds(5), Duration.ofSeconds(5));
        final ProviderClientFactory clients = new ProviderClientFactory(
                new HttpExchange(new HttpClients()), new LlmModule().objectMapper(), System::nanoTime);
        final ModelSettingsCache cache = new ModelSettingsCache(repository);
        return new GatedChatModel(
                clients.create(config),
                PROVIDER,
                MODEL,
                new InferenceGate(),
                new RetryPolicy(CLOCK, () -> 0.5, ignored -> {}),
                cache::current,
                new RefusalMemory(cache));
    }
}
