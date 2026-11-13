package ua.bookloom.llm.client.ollama;

import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ChatMessage;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.ChatRole;
import ua.bookloom.api.llm.ProviderConfig;
import ua.bookloom.api.llm.ProviderKind;
import ua.bookloom.llm.LlmModule;
import ua.bookloom.llm.http.HttpClients;
import ua.bookloom.llm.http.HttpExchange;

/** The fields of an Ollama-native body that go with the model's window, at the real HTTP boundary (design D20). */
class OllamaClientWindowTest {

    private static final String MODEL_ID = "gemma4:e4b-mlx";
    private static final String CHAT_PATH = "/api/chat";
    private static final String REPLY = """
            {"model":"gemma4:e4b-mlx","message":{"content":"reply"},"done_reason":"stop"}
            """;
    private static final ObjectMapper JSON = new ObjectMapper();

    private final WireMockServer server =
            new WireMockServer(WireMockConfiguration.options().dynamicPort().bindAddress("127.0.0.1"));

    @BeforeEach
    void startServer() {
        server.start();
        server.stubFor(post(urlEqualTo(CHAT_PATH)).willReturn(okJson(REPLY)));
    }

    @AfterEach
    void stopServer() {
        server.stop();
    }

    // Ollama unloads an idle model after a few minutes, so a run's calls hold it; a test or a setup call leaves the
    // person's own Ollama settings in force.
    @Test
    void chat_inRun_sendsKeepAlive30mAndInteractiveSendsNone() throws IOException {
        send(hello().keptLoaded());
        send(hello());

        final List<String> bodies = server.getAllServeEvents().stream()
                .map(event -> event.getRequest().getBodyAsString())
                .toList()
                .reversed();
        assertThat(JSON.readTree(bodies.get(0)).path("keep_alive").asText()).isEqualTo("30m");
        assertThat(JSON.readTree(bodies.get(1)).has("keep_alive")).isFalse();
    }

    // A prompt longer than the window is refused instead of silently cut from the front.
    @Test
    void chat_ollamaNative_sendsTruncateFalse() {
        send(hello().sizedTo(8192, 4096));

        server.verify(postRequestedFor(urlEqualTo(CHAT_PATH)).withRequestBody(equalToJson("""
                        {"model":"gemma4:e4b-mlx","messages":[{"role":"user","content":"hello"}],"stream":true,
                         "options":{"num_ctx":8192},"truncate":false}
                        """)));
    }

    // With no window there is nothing to protect, so no truncate field is sent either.
    @Test
    void chat_noWindow_sendsNoTruncate() throws IOException {
        send(hello());

        final String body = server.getAllServeEvents().getFirst().getRequest().getBodyAsString();
        assertThat(JSON.readTree(body).has("truncate")).isFalse();
    }

    private static ChatRequest hello() {
        return new ChatRequest(List.of(new ChatMessage(ChatRole.USER, "hello"))).withoutReasoning();
    }

    private void send(final ChatRequest request) {
        final ProviderConfig config = new ProviderConfig(
                "ollama",
                ProviderKind.OLLAMA,
                URI.create(server.baseUrl()),
                Duration.ofSeconds(2),
                Duration.ofSeconds(2));
        final Result<ChatResponse> result = new OllamaClient(
                        config, new HttpExchange(new HttpClients()), new LlmModule().objectMapper())
                .chat(MODEL_ID, request)
                .result();
        assertThat(result.isOk()).as("chat result: " + result.error()).isTrue();
    }
}
