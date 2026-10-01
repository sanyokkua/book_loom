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
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.llm.CallAttempt;
import ua.bookloom.api.llm.CallAttemptListener;
import ua.bookloom.api.llm.ChatMessage;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ChatRole;
import ua.bookloom.api.llm.ProviderConfig;
import ua.bookloom.api.llm.ProviderKind;
import ua.bookloom.api.pipeline.CallKind;
import ua.bookloom.llm.gate.InferenceGate;
import ua.bookloom.llm.http.HttpClients;
import ua.bookloom.llm.http.HttpExchange;
import ua.bookloom.llm.provider.ProviderClientFactory;
import ua.bookloom.llm.retry.RetryPolicy;

/**
 * Each request the gated model sends is reported as its own attempt with the timeout it waits against, so a stalled
 * judge call reads "attempt 2 of 2" with its own clock instead of one clock counting across both requests.
 */
class GatedChatModelAttemptsTest {

    private static final String OPENAI_REPLY = "{\"model\":\"m\",\"choices\":[{\"message\":{\"role\":\"assistant\","
            + "\"content\":\"ok\"},\"finish_reason\":\"stop\"}]}";
    private static final String OLLAMA_REPLY =
            "{\"model\":\"m\",\"message\":{\"content\":\"ok\"},\"done\":true,\"done_reason\":\"stop\"}";
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-01T12:00:00Z"), ZoneOffset.UTC);
    private static final int SLOWER_THAN_THE_TIMEOUT_MS = 1500;

    private final WireMockServer server =
            new WireMockServer(WireMockConfiguration.options().dynamicPort());
    private final List<String> heard = new ArrayList<>();
    private final CallAttemptListener listener = new CallAttemptListener() {
        @Override
        public void started(final CallAttempt attempt) {
            heard.add("started " + attempt.number() + "/" + attempt.maxAttempts() + " timeout " + attempt.timeout()
                    + " cap " + attempt.maxOutputTokens());
        }

        @Override
        public void failed(final CallAttempt attempt, final ErrorCode code) {
            heard.add("failed " + attempt.number() + " " + code);
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
    void chat_firstAttemptTimesOut_reportsBothAttemptsWithTheirTimeoutAndCap(
            final ProviderKind kind, final String basePath, final String chatPath) {
        final String reply = kind == ProviderKind.OLLAMA ? OLLAMA_REPLY : OPENAI_REPLY;
        server.stubFor(post(urlEqualTo(chatPath))
                .inScenario("stall")
                .whenScenarioStateIs(Scenario.STARTED)
                .willReturn(
                        aResponse().withFixedDelay(SLOWER_THAN_THE_TIMEOUT_MS).withBody(reply))
                .willSetStateTo("answering"));
        server.stubFor(post(urlEqualTo(chatPath))
                .inScenario("stall")
                .whenScenarioStateIs("answering")
                .willReturn(aResponse().withBody(reply)));

        model(kind, basePath).chat(request(), listener);

        assertThat(heard)
                .containsExactly(
                        "started 1/2 timeout PT1S cap 400", "failed 1 timeout", "started 2/2 timeout PT1S cap 300");
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
                "test", kind, URI.create(server.baseUrl() + basePath), Duration.ofSeconds(1), Duration.ofSeconds(1));
        final ProviderClientFactory clients = new ProviderClientFactory(
                new HttpExchange(new HttpClients()), new LlmModule().objectMapper(), System::nanoTime);
        final RetryPolicy retryPolicy = new RetryPolicy(CLOCK, () -> 0.5, ignored -> {});
        return new GatedChatModel(clients.create(config), "m", new InferenceGate(), retryPolicy);
    }
}
