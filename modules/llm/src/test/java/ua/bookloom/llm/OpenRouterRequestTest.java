package ua.bookloom.llm;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import com.google.inject.Guice;
import com.google.inject.Injector;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import ua.bookloom.api.llm.ChatMessage;
import ua.bookloom.api.llm.ChatModel;
import ua.bookloom.api.llm.ChatModelFactory;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ChatRole;
import ua.bookloom.api.llm.ModelCapabilities;
import ua.bookloom.api.llm.ModelSelection;
import ua.bookloom.api.llm.ProviderAuth;
import ua.bookloom.api.llm.ProviderConfig;
import ua.bookloom.api.llm.ProviderKind;
import ua.bookloom.api.llm.ReasoningLevel;
import ua.bookloom.api.llm.ServerEngine;
import ua.bookloom.api.pipeline.CallKind;
import ua.bookloom.llm.testing.FakeProviderConfigs;
import ua.bookloom.llm.testing.LlmTestStorageModule;

/**
 * Proves on the wire what an OpenRouter call carries (D21): the parameter enforcement and compression switch on every
 * call, only the parameters the endpoint lists, the reasoning object chosen from the catalog, the endpoint's limits,
 * and none of the routing or data-policy controls. The catalog and the endpoint are read first, as a person's action
 * (the Models tab's report) reads them, and the chat call uses what was read.
 */
class OpenRouterRequestTest {

    private static final String PROVIDER_ID = "openrouter-under-test";
    private static final String API = "/api/v1";
    private static final String CHAT_PATH = API + "/chat/completions";
    private static final String OFF_BY_EFFORT = "example/off-by-effort";
    private static final String OFF_BY_SWITCH = "example/off-by-switch";
    private static final String ALWAYS_REASONS = "example/always-reasons";
    private static final String PLAIN = "example/plain";
    private static final int SMALL_CAP = 512;
    private static final int LARGE_CAP = 6000;
    private static final int ENDPOINT_MAX_COMPLETION = 4096;
    private static final int ENDPOINT_WINDOW = 32768;
    private static final int SEED = 7;
    private static final String COSTED_REPLY = "{\"id\":\"gen-1\",\"model\":\"example/model-a\","
            + "\"provider\":\"ExampleHost\",\"choices\":[{\"message\":{\"role\":\"assistant\","
            + "\"content\":\"REPLY-TEXT-MARK\"},\"finish_reason\":\"stop\"}],"
            + "\"usage\":{\"prompt_tokens\":10,\"completion_tokens\":5,\"cost\":0.00042}}";
    private static final String REPLY =
            "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"ok\"},\"finish_reason\":\"stop\"}]}";

    private final WireMockServer server =
            new WireMockServer(WireMockConfiguration.options().dynamicPort().bindAddress("127.0.0.1"));
    private final ObjectMapper json = new ObjectMapper();
    private Injector injector;

    @BeforeEach
    void startServer() {
        server.start();
        final FakeProviderConfigs configs = new FakeProviderConfigs();
        assertThat(configs.save(openRouterProvider()).isOk()).isTrue();
        server.stubFor(get(urlEqualTo(API + "/models")).willReturn(okJson(fixture("models.json"))));
        List.of(OFF_BY_EFFORT, OFF_BY_SWITCH, ALWAYS_REASONS, PLAIN)
                .forEach(id -> server.stubFor(get(urlEqualTo(API + "/models/" + id + "/endpoints"))
                        .willReturn(okJson(fixture("endpoints.json")))));
        server.stubFor(post(urlEqualTo(CHAT_PATH)).willReturn(okJson(REPLY)));
        injector = Guice.createInjector(new LlmModule(name -> null), new LlmTestStorageModule(configs));
    }

    @AfterEach
    void stopServer() {
        server.stop();
    }

    @Test
    void chat_openRouter_sendsRequireParametersAndCompressionOff() throws IOException {
        final JsonNode body = chat(PLAIN, request(SMALL_CAP, ReasoningLevel.OFF));

        assertThat(body.path("provider")).isEqualTo(json.readTree("{\"require_parameters\":true}"));
        assertThat(body.path("plugins"))
                .isEqualTo(json.readTree("[{\"id\":\"context-compression\",\"enabled\":false}]"));
        assertThat(body.path("stream").asBoolean(true)).as("stream").isFalse();
    }

    @Test
    void chat_unsupportedParameter_isNotSent() throws IOException {
        final JsonNode body = chat(PLAIN, request(SMALL_CAP, ReasoningLevel.OFF).withSeed(SEED));

        assertThat(body.has("seed"))
                .as("seed sent though the endpoint does not list it")
                .isFalse();
        assertThat(body.path("temperature").asDouble()).as("listed temperature").isEqualTo(0.2);
    }

    @Test
    void chat_reasoningNoneListed_sendsEffortNone() throws IOException {
        final JsonNode body = chat(OFF_BY_EFFORT, request(SMALL_CAP, ReasoningLevel.OFF));

        assertThat(body.path("reasoning")).isEqualTo(json.readTree("{\"effort\":\"none\"}"));
        assertThat(body.has("reasoning_effort")).as("reasoning_effort").isFalse();
    }

    @Test
    void chat_reasoningObjectWithoutEfforts_sendsEnabledFalse() throws IOException {
        final JsonNode body = chat(OFF_BY_SWITCH, request(SMALL_CAP, ReasoningLevel.OFF));

        assertThat(body.path("reasoning")).isEqualTo(json.readTree("{\"enabled\":false}"));
    }

    @Test
    void chat_reasoningMandatory_sendsLowestEffort() throws IOException {
        final JsonNode body = chat(ALWAYS_REASONS, request(SMALL_CAP, ReasoningLevel.OFF));

        assertThat(body.path("reasoning")).isEqualTo(json.readTree("{\"effort\":\"low\"}"));
    }

    @Test
    void chat_reasoningAbsent_sendsNothing() throws IOException {
        final JsonNode body = chat(PLAIN, request(SMALL_CAP, ReasoningLevel.OFF));

        assertThat(body.has("reasoning")).as("reasoning sent").isFalse();
        assertThat(body.has("reasoning_effort")).as("reasoning_effort sent").isFalse();
    }

    @Test
    void chat_capAboveEndpointLimit_isLoweredTo4096() throws IOException {
        final JsonNode body = chat(PLAIN, request(LARGE_CAP, ReasoningLevel.OFF));

        assertThat(body.path("max_tokens").asInt()).isEqualTo(ENDPOINT_MAX_COMPLETION);
        assertThat(body.has("max_completion_tokens"))
                .as("max_completion_tokens")
                .isFalse();
    }

    @Test
    void facts_window_isTheEndpointContextLengthNotTheModels() {
        final Integer window = injector.getInstance(ModelCapabilities.class)
                .contextLength(PROVIDER_ID, PLAIN)
                .map(length -> length.tokens())
                .data();

        assertThat(window).isEqualTo(ENDPOINT_WINDOW);
    }

    // The call line names who answered and what it cost, as slugs and numbers; the prompt and the reply text stay out.
    @Test
    void chat_debugLine_carriesSlugsAndCostButNoText() throws IOException {
        server.stubFor(post(urlEqualTo(CHAT_PATH)).willReturn(okJson(COSTED_REPLY)));
        final Logger bookloom = (Logger) LoggerFactory.getLogger("ua.bookloom");
        final Level before = bookloom.getLevel();
        final ListAppender<ILoggingEvent> logged = new ListAppender<>();
        bookloom.setLevel(Level.DEBUG);
        logged.start();
        bookloom.addAppender(logged);
        try {
            chat(PLAIN, draftRequest());
        } finally {
            bookloom.detachAppender(logged);
            bookloom.setLevel(before);
        }

        final List<String> lines = logged.list.stream()
                .filter(event -> event.getLevel() == Level.DEBUG)
                .map(ILoggingEvent::getFormattedMessage)
                .filter(message -> message.startsWith("Provider chat call "))
                .toList();
        assertThat(lines)
                .singleElement()
                .asString()
                .contains("servedProvider=ExampleHost")
                .contains("servedModel=example/model-a")
                .contains("cost=0.00042")
                .doesNotContain("REPLY-TEXT-MARK");
    }

    @ParameterizedTest
    @ValueSource(strings = {"data_collection", "quantizations", "order", "allow_fallbacks", "session_id"})
    void chat_openRouter_neverSendsRoutingOrDataPolicyFields(final String field) throws IOException {
        final JsonNode body = chat(OFF_BY_EFFORT, request(SMALL_CAP, ReasoningLevel.OFF));

        assertThat(body.findValue(field)).as(field).isNull();
    }

    private JsonNode chat(final String modelId, final ChatRequest request) throws IOException {
        final ModelSelection selection = new ModelSelection(PROVIDER_ID, modelId);
        assertThat(injector.getInstance(ModelCapabilities.class)
                        .report(selection)
                        .isOk())
                .as("facts read")
                .isTrue();
        final ChatModel model = Objects.requireNonNull(
                injector.getInstance(ChatModelFactory.class).create(selection).data(), "chat model");
        assertThat(model.chat(request).isOk()).as("chat answered").isTrue();
        final List<LoggedRequest> sent = server.findAll(postRequestedFor(urlEqualTo(CHAT_PATH)));
        assertThat(sent).hasSize(1);
        return json.readTree(sent.getFirst().getBodyAsString());
    }

    private ProviderConfig openRouterProvider() {
        return new ProviderConfig(
                PROVIDER_ID,
                ProviderKind.OPENAI_COMPATIBLE,
                URI.create(server.baseUrl() + API),
                Duration.ofSeconds(2),
                Duration.ofSeconds(5),
                null,
                ProviderAuth.NONE,
                null,
                null,
                null,
                null,
                Map.of(),
                List.of(),
                ServerEngine.GENERIC,
                ServerEngine.OPENROUTER,
                null,
                false);
    }

    private static ChatRequest request(final int cap, final ReasoningLevel level) {
        return new ChatRequest(
                List.of(new ChatMessage(ChatRole.USER, "Translate: a red door.")),
                0.2,
                null,
                level,
                null,
                null,
                cap,
                null,
                null);
    }

    private static ChatRequest draftRequest() {
        return new ChatRequest(
                List.of(new ChatMessage(ChatRole.USER, "Translate: a red door.")),
                0.2,
                null,
                ReasoningLevel.OFF,
                null,
                null,
                SMALL_CAP,
                null,
                CallKind.DRAFT);
    }

    private static String fixture(final String name) {
        try (InputStream in = Objects.requireNonNull(
                OpenRouterRequestTest.class.getResourceAsStream("/ua/bookloom/llm/openrouter/" + name), name)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }
}
