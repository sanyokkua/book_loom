package ua.bookloom.llm.client.openai;

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
import java.util.Objects;
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

/** The output cap reaches an OpenAI-compatible server as max_tokens, and only when the request states one. */
class OpenAiCompatibleClientOutputCapTest {

    private static final String MODEL_ID = "google/gemma-4-e4b";
    private static final String CHAT_PATH = "/v1/chat/completions";
    private static final String REPLY = """
            {"model":"google/gemma-4-e4b","choices":[{"message":{"content":"reply"},"finish_reason":"stop"}]}
            """;

    private final WireMockServer server =
            new WireMockServer(WireMockConfiguration.options().dynamicPort().bindAddress("127.0.0.1"));

    @BeforeEach
    void startServer() {
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop();
    }

    @Test
    void chat_outputCap_postsMaxTokens() {
        stubChat(REPLY);

        sendChat(new ChatRequest(List.of(new ChatMessage(ChatRole.USER, "hello")), null, null, null, null, null, 80));

        server.verify(postRequestedFor(urlEqualTo(CHAT_PATH)).withRequestBody(equalToJson("""
                        {"model":"google/gemma-4-e4b","messages":[{"role":"user","content":"hello"}],"stream":false,"max_tokens":80}
                        """)));
    }

    @Test
    void chat_noOutputCap_omitsMaxTokens() {
        stubChat(REPLY);

        sendChat(messageOnlyRequest());

        assertThat(server.getAllServeEvents())
                .extracting(event -> event.getRequest().getBodyAsString())
                .allSatisfy(body -> assertThat(body).doesNotContain("max_tokens"));
    }

    private void stubChat(String body) {
        server.stubFor(post(urlEqualTo(CHAT_PATH)).willReturn(okJson(body)));
    }

    private ChatResponse sendChat(ChatRequest request) {
        final ProviderConfig config = new ProviderConfig(
                "lmstudio",
                ProviderKind.OPENAI_COMPATIBLE,
                URI.create(server.baseUrl() + "/v1"),
                Duration.ofSeconds(2),
                Duration.ofSeconds(2));
        final Result<ChatResponse> result = new OpenAiCompatibleClient(
                        config, new HttpExchange(new HttpClients()), new LlmModule().objectMapper(), System::nanoTime)
                .chat(MODEL_ID, request)
                .result();
        assertThat(result.isOk()).as("chat result: " + result.error()).isTrue();
        return Objects.requireNonNull(result.data(), "chat response");
    }

    private static ChatRequest messageOnlyRequest() {
        return new ChatRequest(List.of(new ChatMessage(ChatRole.USER, "hello")));
    }
}
