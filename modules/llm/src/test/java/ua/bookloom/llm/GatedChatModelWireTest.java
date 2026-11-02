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
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.CallAttempt;
import ua.bookloom.api.llm.CallAttemptListener;
import ua.bookloom.api.llm.ChatMessage;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.ChatRole;
import ua.bookloom.api.llm.ProviderConfig;
import ua.bookloom.api.llm.ProviderKind;
import ua.bookloom.api.pipeline.CallKind;
import ua.bookloom.llm.gate.InferenceGate;
import ua.bookloom.llm.http.HttpClients;
import ua.bookloom.llm.http.HttpExchange;
import ua.bookloom.llm.provider.ProviderClientFactory;
import ua.bookloom.llm.retry.RetryPolicy;

/** A call shows what went on the wire and what came back, for both dialects, with no header or credential. */
class GatedChatModelWireTest {

    private static final String OPENAI_REPLY = "{\"model\":\"m\",\"choices\":[{\"message\":{\"role\":\"assistant\","
            + "\"content\":\"ok\"},\"finish_reason\":\"stop\"}]}";
    private static final String OLLAMA_REPLY =
            "{\"model\":\"m\",\"message\":{\"content\":\"ok\"},\"done\":true,\"done_reason\":\"stop\"}";

    private final WireMockServer server =
            new WireMockServer(WireMockConfiguration.options().dynamicPort().bindAddress("127.0.0.1"));
    private final List<CallAttempt> heard = new ArrayList<>();
    private final CallAttemptListener listener = new CallAttemptListener() {
        @Override
        public void started(final CallAttempt attempt) {
            heard.add(attempt);
        }

        @Override
        public void failed(final CallAttempt attempt, final ErrorCode code) {
            // Only the start carries the body.
        }
    };

    @BeforeEach
    void startServer() {
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop();
    }

    @ParameterizedTest
    @CsvSource({"OPENAI_COMPATIBLE, /v1, /v1/chat/completions", "OLLAMA, '', /api/chat"})
    void chat_answered_attemptCarriesTheWireBodyAndTheReplyKeepsTheBodyAsReceived(
            final ProviderKind kind, final String basePath, final String chatPath) {
        final String reply = kind == ProviderKind.OLLAMA ? OLLAMA_REPLY : OPENAI_REPLY;
        server.stubFor(post(urlEqualTo(chatPath)).willReturn(aResponse().withBody(reply)));

        final Result<ChatResponse> result = model(kind, basePath).chat(request(), listener);

        assertThat(heard).hasSize(1);
        assertThat(heard.getFirst().requestBody())
                .contains("\"model\":\"m\"")
                .contains("Translate this.")
                .doesNotContain("Authorization");
        assertThat(result.data()).isNotNull();
        assertThat(result.data().rawBody()).contains("\"content\":\"ok\"");
    }

    private static ChatRequest request() {
        return new ChatRequest(
                List.of(new ChatMessage(ChatRole.USER, "Translate this.")),
                null,
                null,
                null,
                null,
                null,
                400,
                null,
                CallKind.DRAFT);
    }

    private GatedChatModel model(final ProviderKind kind, final String basePath) {
        final ProviderConfig config = new ProviderConfig(
                "test", kind, URI.create(server.baseUrl() + basePath), Duration.ofSeconds(5), Duration.ofSeconds(5));
        final ProviderClientFactory clients = new ProviderClientFactory(
                new HttpExchange(new HttpClients()), new LlmModule().objectMapper(), System::nanoTime);
        final RetryPolicy retryPolicy = new RetryPolicy(
                Clock.fixed(Instant.parse("2026-10-01T12:00:00Z"), ZoneOffset.UTC), () -> 0.5, ignored -> {});
        return new GatedChatModel(clients.create(config), "m", new InferenceGate(), retryPolicy);
    }
}
