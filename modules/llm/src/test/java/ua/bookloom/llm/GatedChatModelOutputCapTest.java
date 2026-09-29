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

/** A capability downgrade retried by the gated model still sends the output cap the request carried. */
class GatedChatModelOutputCapTest {

    private static final String OLLAMA_CHAT_PATH = "/api/chat";
    private static final String OLLAMA_REPLY =
            "{\"model\":\"test-model\",\"message\":{\"role\":\"assistant\",\"content\":\"translated\"},\"done_reason\":\"stop\"}";
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-22T12:00:00Z"), ZoneOffset.UTC);

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

    // The retry after a rejected reasoning control keeps the cap.
    @Test
    void chat_ollamaThinkRejectedWithOutputCap_retriedRequestStillPostsNumPredict() {
        server.stubFor(post(urlEqualTo(OLLAMA_CHAT_PATH))
                .inScenario("think capability with cap")
                .whenScenarioStateIs(Scenario.STARTED)
                .willReturn(aResponse().withStatus(400).withBody("{\"error\":\"unknown field think\"}"))
                .willSetStateTo("think removed"));
        server.stubFor(post(urlEqualTo(OLLAMA_CHAT_PATH))
                .inScenario("think capability with cap")
                .whenScenarioStateIs("think removed")
                .willReturn(aResponse().withStatus(200).withBody(OLLAMA_REPLY)));
        final ChatRequest request = new ChatRequest(
                List.of(new ChatMessage(ChatRole.USER, "Translate this.")), null, null, false, null, null, 80);

        final Result<ChatResponse> result = ollamaModel().chat(request);

        assertThat(result.isOk()).isTrue();
        assertThat(server.getAllServeEvents())
                .extracting(event -> event.getRequest().getBodyAsString())
                .allSatisfy(body -> assertThat(body).contains("\"num_predict\":80"))
                .anySatisfy(body -> assertThat(body).contains("\"think\":false"))
                .anySatisfy(body -> assertThat(body).doesNotContain("\"think\""));
    }

    @Test
    void chat_structuredOutputRejectedWithOutputCap_retriedRequestStillPostsNumPredict() {
        server.stubFor(post(urlEqualTo(OLLAMA_CHAT_PATH))
                .inScenario("format capability with cap")
                .whenScenarioStateIs(Scenario.STARTED)
                .willReturn(aResponse().withStatus(400).withBody("{\"error\":\"unknown field format\"}"))
                .willSetStateTo("format removed"));
        server.stubFor(post(urlEqualTo(OLLAMA_CHAT_PATH))
                .inScenario("format capability with cap")
                .whenScenarioStateIs("format removed")
                .willReturn(aResponse().withStatus(200).withBody(OLLAMA_REPLY)));
        final ChatRequest request = new ChatRequest(
                List.of(new ChatMessage(ChatRole.USER, "Translate this.")),
                null,
                new ResponseFormat("draft", "{\"type\":\"object\"}"),
                null,
                null,
                null,
                80);

        final Result<ChatResponse> result = ollamaModel().chat(request);

        assertThat(result.isOk()).isTrue();
        assertThat(server.getAllServeEvents())
                .extracting(event -> event.getRequest().getBodyAsString())
                .allSatisfy(body -> assertThat(body).contains("\"num_predict\":80"))
                .anySatisfy(body -> assertThat(body).contains("\"format\""))
                .anySatisfy(body -> assertThat(body).doesNotContain("\"format\""));
    }

    private GatedChatModel ollamaModel() {
        final ProviderConfig config = new ProviderConfig(
                "ollama",
                ProviderKind.OLLAMA,
                URI.create(server.baseUrl()),
                Duration.ofSeconds(2),
                Duration.ofSeconds(3));
        final ProviderClientFactory clients = new ProviderClientFactory(
                new HttpExchange(new HttpClients()), new LlmModule().objectMapper(), System::nanoTime);
        final RetryPolicy retryPolicy = new RetryPolicy(CLOCK, () -> 0.5, ignored -> {});
        return new GatedChatModel(clients.create(config), "test-model", new InferenceGate(), retryPolicy);
    }
}
