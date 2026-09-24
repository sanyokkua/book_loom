package ua.bookloom.llm.provider;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
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
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.llm.ChatMessage;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.ChatRole;
import ua.bookloom.api.llm.ProviderConfig;
import ua.bookloom.api.llm.ProviderKind;
import ua.bookloom.llm.LlmModule;
import ua.bookloom.llm.client.ollama.OllamaClient;
import ua.bookloom.llm.client.openai.OpenAiCompatibleClient;
import ua.bookloom.llm.http.HttpClients;
import ua.bookloom.llm.http.HttpExchange;

/** Proves both provider dialects preserve a response's retry advice with its typed failure. */
class ProviderCallResultTest {

    private final WireMockServer server =
            new WireMockServer(WireMockConfiguration.options().dynamicPort());

    @BeforeEach
    void startServer() {
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop();
    }

    @Test
    void ollamaChat_rateLimitedReplyRetainsRetryAfter() {
        server.stubFor(post(urlEqualTo("/api/chat"))
                .willReturn(aResponse()
                        .withStatus(429)
                        .withHeader("Retry-After", "2")
                        .withBody("{}")));

        final ProviderCallResult<ChatResponse> call = ollamaClient().chat("model-a", request());

        assertThat(Objects.requireNonNull(call.result().error(), "error").code())
                .isEqualTo(ErrorCode.rateLimited);
        assertThat(call.retryAfter()).isEqualTo("2");
    }

    @Test
    void openAiChat_rateLimitedReplyRetainsRetryAfter() {
        server.stubFor(post(urlEqualTo("/v1/chat/completions"))
                .willReturn(aResponse()
                        .withStatus(429)
                        .withHeader("Retry-After", "2")
                        .withBody("{}")));

        final ProviderCallResult<ChatResponse> call = openAiClient().chat("model-a", request());

        assertThat(Objects.requireNonNull(call.result().error(), "error").code())
                .isEqualTo(ErrorCode.rateLimited);
        assertThat(call.retryAfter()).isEqualTo("2");
    }

    private OllamaClient ollamaClient() {
        return new OllamaClient(
                config(ProviderKind.OLLAMA, server.baseUrl()),
                new HttpExchange(new HttpClients()),
                new LlmModule().objectMapper());
    }

    private OpenAiCompatibleClient openAiClient() {
        return new OpenAiCompatibleClient(
                config(ProviderKind.OPENAI_COMPATIBLE, server.baseUrl() + "/v1"),
                new HttpExchange(new HttpClients()),
                new LlmModule().objectMapper());
    }

    private static ProviderConfig config(ProviderKind kind, String endpoint) {
        return new ProviderConfig("test", kind, URI.create(endpoint), Duration.ofSeconds(2), Duration.ofSeconds(3));
    }

    private static ChatRequest request() {
        return new ChatRequest(List.of(new ChatMessage(ChatRole.USER, "hello")));
    }
}
