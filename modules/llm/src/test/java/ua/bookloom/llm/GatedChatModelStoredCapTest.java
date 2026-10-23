package ua.bookloom.llm;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ua.bookloom.api.llm.ChatMessage;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ChatRole;
import ua.bookloom.api.llm.ModelSettings;
import ua.bookloom.api.llm.OutputLengthField;
import ua.bookloom.api.llm.ProviderConfig;
import ua.bookloom.api.llm.ProviderKind;
import ua.bookloom.api.llm.TemperatureChoice;
import ua.bookloom.llm.gate.InferenceGate;
import ua.bookloom.llm.http.HttpClients;
import ua.bookloom.llm.http.HttpExchange;
import ua.bookloom.llm.provider.ModelSettingsCache;
import ua.bookloom.llm.provider.ProviderClientFactory;
import ua.bookloom.llm.provider.RefusalMemory;
import ua.bookloom.llm.retry.RetryPolicy;
import ua.bookloom.llm.testing.FakeModelSettingsRepository;

/** "Limit answer length" on the Models tab caps a call too when the call states no cap of its own. */
class GatedChatModelStoredCapTest {

    private static final String PROVIDER = "office";
    private static final String MODEL = "gemma4:e4b";
    private static final String OPENAI_REPLY = "{\"model\":\"m\",\"choices\":[{\"message\":{\"role\":\"assistant\","
            + "\"content\":\"ok\"},\"finish_reason\":\"stop\"}]}";
    private static final String OLLAMA_REPLY =
            "{\"model\":\"m\",\"message\":{\"content\":\"ok\"},\"done\":true,\"done_reason\":\"stop\"}";
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
    @CsvSource({
        "OLLAMA, '', /api/chat, MAX_TOKENS, '\"num_predict\":128'",
        "OPENAI_COMPATIBLE, /v1, /v1/chat/completions, MAX_TOKENS, '\"max_tokens\":128'",
        "OPENAI_COMPATIBLE, /v1, /v1/chat/completions, MAX_COMPLETION_TOKENS, '\"max_completion_tokens\":128'",
    })
    void chat_storedLimitAndRequestWithoutCap_bodyCarriesTheLimitInTheChosenField(
            final ProviderKind kind,
            final String basePath,
            final String chatPath,
            final OutputLengthField field,
            final String expected) {
        final String reply = kind == ProviderKind.OLLAMA ? OLLAMA_REPLY : OPENAI_REPLY;
        server.stubFor(post(urlEqualTo(chatPath))
                .willReturn(aResponse().withStatus(200).withBody(reply)));
        repository.save(new ModelSettings(
                PROVIDER, MODEL, TemperatureChoice.CHOOSE, null, null, 128, field, List.of(), null, null));

        model(kind, basePath).chat(uncapped());

        assertThat(server.getAllServeEvents())
                .singleElement()
                .satisfies(event ->
                        assertThat(event.getRequest().getBodyAsString()).contains(expected));
    }

    private static ChatRequest uncapped() {
        return new ChatRequest(
                List.of(new ChatMessage(ChatRole.USER, "Say ok.")),
                0.2,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
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
