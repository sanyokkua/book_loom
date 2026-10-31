package ua.bookloom.llm.client.openai;

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
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import ua.bookloom.api.llm.ChatMessage;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ChatRole;
import ua.bookloom.api.llm.ProbeVerdicts;
import ua.bookloom.api.llm.ProviderConfig;
import ua.bookloom.api.llm.ProviderKind;
import ua.bookloom.api.llm.ReasoningLevel;
import ua.bookloom.api.llm.ServerEngine;
import ua.bookloom.llm.LlmModule;
import ua.bookloom.llm.engine.EngineFacts;
import ua.bookloom.llm.http.HttpClients;
import ua.bookloom.llm.http.HttpExchange;

/**
 * An OpenAI-compatible server fixes its window when the model loads, so a call is planned for that window and its
 * body names no size, on every engine (design D20). LM Studio's own route is covered by the pipeline's window test.
 */
class OpenAiNoContextSizeTest {

    private static final String CHAT_PATH = "/v1/chat/completions";
    private static final String MODEL_ID = "mara-7b";
    private static final String REPLY = """
            {"model":"mara-7b","choices":[{"message":{"content":"reply"},"finish_reason":"stop"}]}
            """;
    private static final String PROPS = """
            {"build_info":"b11669-620d2225c","role":"model","chat_template":"{{ messages }}"}
            """;

    private final WireMockServer server =
            new WireMockServer(WireMockConfiguration.options().dynamicPort().bindAddress("127.0.0.1"));
    private final ObjectMapper json = new ObjectMapper();

    @BeforeEach
    void startServer() {
        server.start();
        server.stubFor(post(urlEqualTo(CHAT_PATH)).willReturn(okJson(REPLY)));
        server.stubFor(get(urlEqualTo("/props")).willReturn(okJson(PROPS)));
    }

    @AfterEach
    void stopServer() {
        server.stop();
    }

    @ParameterizedTest
    @EnumSource(
            value = ServerEngine.class,
            names = {"LM_STUDIO"},
            mode = EnumSource.Mode.EXCLUDE)
    void serializeRequest_anyOpenAiEngine_carriesNoContextSize(final ServerEngine engine) throws IOException {
        final ChatRequest sized = new ChatRequest(
                        List.of(new ChatMessage(ChatRole.USER, "Translate: a red door.")),
                        0.2,
                        null,
                        ReasoningLevel.OFF)
                .sizedTo(8192, 400);

        client(engine).chat(MODEL_ID, sized);

        final List<LoggedRequest> sent = server.findAll(postRequestedFor(urlEqualTo(CHAT_PATH)));
        assertThat(sent).hasSize(1);
        final JsonNode body = json.readTree(sent.getFirst().getBodyAsString());
        assertThat(body.has("num_ctx")).isFalse();
        assertThat(body.has("n_ctx")).isFalse();
        assertThat(body.has("context_length")).isFalse();
        assertThat(body.has("options")).isFalse();
    }

    private OpenAiCompatibleClient client(final ServerEngine engine) {
        final ProviderConfig config = new ProviderConfig(
                        "engine-under-test",
                        ProviderKind.OPENAI_COMPATIBLE,
                        URI.create(server.baseUrl() + "/v1"),
                        Duration.ofSeconds(2),
                        Duration.ofSeconds(5))
                .withEngine(engine);
        final HttpExchange exchange = new HttpExchange(new HttpClients());
        final ObjectMapper mapper = new LlmModule().objectMapper();
        return new OpenAiCompatibleClient(
                config,
                exchange,
                mapper,
                System::nanoTime,
                new EngineFacts(exchange, mapper),
                model -> ProbeVerdicts.none());
    }
}
