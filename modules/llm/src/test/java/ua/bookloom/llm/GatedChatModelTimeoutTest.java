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
import ua.bookloom.api.llm.ResponseFormat;
import ua.bookloom.llm.gate.InferenceGate;
import ua.bookloom.llm.http.HttpClients;
import ua.bookloom.llm.http.HttpExchange;
import ua.bookloom.llm.provider.ProviderClientFactory;
import ua.bookloom.llm.retry.RetryPolicy;

/** Proves a capability-downgraded retry keeps the expected-output-scaled timeout of the original request. */
class GatedChatModelTimeoutTest {

    private static final String CHAT_PATH = "/v1/chat/completions";
    private static final String MODEL_ID = "test-model";
    private static final String REPLY =
            "{\"model\":\"test-model\",\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"translated\"},\"finish_reason\":\"stop\"}]}";
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-22T12:00:00Z"), ZoneOffset.UTC);

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

    // A 1 s configured timeout would fail a 1.5 s-delayed retry; the downgraded request keeps its expected output,
    // so the retry still scales to 200 s and gets its reply.
    @Test
    void chat_structuredOutputRejectedWithExpectedOutput_retriedRequestKeepsScaledTimeout() {
        server.stubFor(post(urlEqualTo(CHAT_PATH))
                .inScenario("format capability with timeout")
                .whenScenarioStateIs(Scenario.STARTED)
                .willReturn(aResponse().withStatus(400).withBody("{\"error\":\"response_format is unsupported\"}"))
                .willSetStateTo("format removed"));
        server.stubFor(post(urlEqualTo(CHAT_PATH))
                .inScenario("format capability with timeout")
                .whenScenarioStateIs("format removed")
                .willReturn(aResponse().withFixedDelay(1500).withBody(REPLY)));
        final ChatRequest request = new ChatRequest(
                List.of(new ChatMessage(ChatRole.USER, "Translate this.")),
                0.2,
                new ResponseFormat("draft", "{\"type\":\"object\"}"),
                null,
                null,
                400);

        final Result<ChatResponse> result = model(Duration.ofSeconds(1)).chat(request);

        assertThat(result.isOk()).isTrue();
    }

    private GatedChatModel model(Duration requestTimeout) {
        final ProviderConfig config = new ProviderConfig(
                "test",
                ProviderKind.OPENAI_COMPATIBLE,
                URI.create(server.baseUrl() + "/v1"),
                Duration.ofSeconds(2),
                requestTimeout);
        final ProviderClientFactory clients = new ProviderClientFactory(
                new HttpExchange(new HttpClients()), new LlmModule().objectMapper(), System::nanoTime);
        final RetryPolicy retryPolicy = new RetryPolicy(CLOCK, () -> 0.5, ignored -> {});
        return new GatedChatModel(clients.create(config), MODEL_ID, new InferenceGate(), retryPolicy);
    }
}
