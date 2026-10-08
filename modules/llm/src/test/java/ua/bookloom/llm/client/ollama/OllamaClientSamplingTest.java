package ua.bookloom.llm.client.ollama;

import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import org.jspecify.annotations.Nullable;
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
import ua.bookloom.api.llm.SamplingParams;
import ua.bookloom.llm.LlmModule;
import ua.bookloom.llm.http.HttpClients;
import ua.bookloom.llm.http.HttpExchange;

/** The sampling controls reach Ollama as native options top_p, top_k, min_p and repeat_penalty, only when set. */
class OllamaClientSamplingTest {

    private static final String CHAT_PATH = "/api/chat";
    private static final String REPLY = """
            {"model":"m","message":{"content":"reply"},"done_reason":"stop"}
            """;

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

    @Test
    void chat_sampling_postsNativeOptions() {
        send(requestWith(new SamplingParams(0.9, 40, 0.05, 1.0)));

        server.verify(postRequestedFor(urlEqualTo(CHAT_PATH)).withRequestBody(equalToJson("""
                        {"model":"m","messages":[{"role":"user","content":"hello"}],"stream":true,
                         "options":{"top_p":0.9,"top_k":40,"min_p":0.05,"repeat_penalty":1.0}}
                        """)));
    }

    @Test
    void chat_noSampling_omitsEveryOption() {
        send(requestWith(null));

        assertThat(server.getAllServeEvents().getFirst().getRequest().getBodyAsString())
                .doesNotContain("top_p", "top_k", "min_p", "repeat_penalty");
    }

    private static ChatRequest requestWith(@Nullable SamplingParams sampling) {
        return new ChatRequest(
                List.of(new ChatMessage(ChatRole.USER, "hello")),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                sampling);
    }

    private void send(ChatRequest request) {
        final ProviderConfig config = new ProviderConfig(
                "ollama",
                ProviderKind.OLLAMA,
                URI.create(server.baseUrl()),
                Duration.ofSeconds(2),
                Duration.ofSeconds(2));
        final Result<ChatResponse> result = new OllamaClient(
                        config, new HttpExchange(new HttpClients()), new LlmModule().objectMapper())
                .chat("m", request)
                .result();
        assertThat(result.isOk()).as("chat result: " + result.error()).isTrue();
    }
}
