package ua.bookloom.llm.client;

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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.llm.ChatMessage;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ChatRole;
import ua.bookloom.api.llm.ProviderConfig;
import ua.bookloom.api.llm.ProviderKind;
import ua.bookloom.llm.LlmModule;
import ua.bookloom.llm.client.ollama.OllamaClient;
import ua.bookloom.llm.client.openai.OpenAiCompatibleClient;
import ua.bookloom.llm.http.HttpClients;
import ua.bookloom.llm.http.HttpExchange;

/**
 * A server that answers a chat with HTTP 5xx, or with a body that is not the dialect's reply, did answer: in both
 * dialects the failure is {@code upstream} with words that say so, so nothing downstream words it as "cannot reach",
 * and it is never an unexpected {@code internal} fault.
 */
class ChatReplyFailureTest {

    private final WireMockServer server =
            new WireMockServer(WireMockConfiguration.options().dynamicPort().bindAddress("127.0.0.1"));

    @BeforeEach
    void start() {
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop();
    }

    @ParameterizedTest
    @CsvSource({"OLLAMA,/api/chat,500", "OLLAMA,/api/chat,503", "OPENAI_COMPATIBLE,/v1/chat/completions,500"})
    void chat_serverError_isUpstreamNamingTheStatus(final ProviderKind kind, final String path, final int status) {
        server.stubFor(
                post(urlEqualTo(path)).willReturn(aResponse().withStatus(status).withBody("")));

        final AppError error = Objects.requireNonNull(chat(kind).error(), "error");

        assertThat(error.code()).isEqualTo(ErrorCode.upstream);
        assertThat(error.message()).isEqualTo("The AI program had an error (HTTP " + status + ").");
    }

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                "OLLAMA|/api/chat|''",
                "OLLAMA|/api/chat|not-json",
                "OLLAMA|/api/chat|null",
                "OLLAMA|/api/chat|{}",
                "OLLAMA|/api/chat|{\"message\":{}}",
                "OLLAMA|/api/chat|{\"message\":{\"content\":null}}",
                "OPENAI_COMPATIBLE|/v1/chat/completions|''",
                "OPENAI_COMPATIBLE|/v1/chat/completions|not-json",
                "OPENAI_COMPATIBLE|/v1/chat/completions|<html>proxy error</html>",
                "OPENAI_COMPATIBLE|/v1/chat/completions|null",
                "OPENAI_COMPATIBLE|/v1/chat/completions|{}",
                "OPENAI_COMPATIBLE|/v1/chat/completions|{\"choices\":[]}",
                "OPENAI_COMPATIBLE|/v1/chat/completions|{\"choices\":[{\"message\":{}}]}"
            })
    void chat_unreadableReply_isUpstreamSayingTheReplyCouldNotBeRead(
            final ProviderKind kind, final String path, final String body) {
        server.stubFor(
                post(urlEqualTo(path)).willReturn(aResponse().withStatus(200).withBody(body)));

        final AppError error = Objects.requireNonNull(chat(kind).error(), "error");

        assertThat(error.code()).isEqualTo(ErrorCode.upstream);
        assertThat(error.message()).isEqualTo("The AI program answered, but its reply could not be read.");
    }

    private ua.bookloom.api.Result<?> chat(final ProviderKind kind) {
        final String base = server.baseUrl() + (kind == ProviderKind.OLLAMA ? "" : "/v1");
        final ProviderConfig config =
                new ProviderConfig("p", kind, URI.create(base), Duration.ofSeconds(2), Duration.ofSeconds(2));
        final HttpExchange exchange = new HttpExchange(new HttpClients());
        final ChatRequest request = new ChatRequest(List.of(new ChatMessage(ChatRole.USER, "hello")));
        return kind == ProviderKind.OLLAMA
                ? new OllamaClient(config, exchange, new LlmModule().objectMapper())
                        .chat("model-a", request)
                        .result()
                : new OpenAiCompatibleClient(config, exchange, new LlmModule().objectMapper(), System::nanoTime)
                        .chat("model-a", request)
                        .result();
    }
}
