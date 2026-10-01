package ua.bookloom.llm;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ChatMessage;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.ChatRole;
import ua.bookloom.api.llm.ProviderConfig;
import ua.bookloom.api.llm.ProviderKind;
import ua.bookloom.llm.gate.InferenceGate;
import ua.bookloom.llm.http.HttpClients;
import ua.bookloom.llm.http.HttpExchange;
import ua.bookloom.llm.provider.ProviderClientFactory;
import ua.bookloom.llm.retry.RetryPolicy;

/**
 * A timed-out call is retried once, and never as the identical request: the retry samples with its own seed and a
 * lower output cap, so a reply that looped until the timeout is not simply replayed.
 */
class GatedChatModelTimeoutRetryTest {

    private static final String CHAT_PATH = "/v1/chat/completions";
    private static final String MODEL_ID = "test-model";
    private static final String REPLY = "{\"model\":\"test-model\",\"choices\":[{\"message\":{\"role\":\"assistant\","
            + "\"content\":\"translated\"},\"finish_reason\":\"stop\"}]}";
    private static final String OLLAMA_CHAT_PATH = "/api/chat";
    private static final String OLLAMA_REPLY = "{\"model\":\"test-model\",\"message\":{\"content\":\"translated\"},"
            + "\"done\":true,\"done_reason\":\"stop\"}";
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-01T12:00:00Z"), ZoneOffset.UTC);
    private static final int SLOWER_THAN_THE_TIMEOUT_MS = 1500;

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
    void chat_firstAttemptTimesOut_retriesOnceWithASeedAndALowerCap() {
        server.stubFor(post(urlEqualTo(CHAT_PATH))
                .inScenario("timeout then answer")
                .whenScenarioStateIs(Scenario.STARTED)
                .willReturn(
                        aResponse().withFixedDelay(SLOWER_THAN_THE_TIMEOUT_MS).withBody(REPLY))
                .willSetStateTo("answering"));
        server.stubFor(post(urlEqualTo(CHAT_PATH))
                .inScenario("timeout then answer")
                .whenScenarioStateIs("answering")
                .willReturn(aResponse().withBody(REPLY)));

        final Result<ChatResponse> result = model().chat(cappedRequest());

        assertThat(result.data()).extracting(ChatResponse::content).isEqualTo("translated");
        final List<String> bodies = bodies();
        assertThat(bodies).hasSize(2);
        assertThat(bodies.get(0)).contains("\"max_tokens\":400").doesNotContain("\"seed\"");
        assertThat(bodies.get(1)).contains("\"max_tokens\":300", "\"seed\":2");
    }

    @Test
    void chat_everyAttemptTimesOut_stopsAfterTwoRequestsWithTimeout() {
        server.stubFor(post(urlEqualTo(CHAT_PATH))
                .willReturn(
                        aResponse().withFixedDelay(SLOWER_THAN_THE_TIMEOUT_MS).withBody(REPLY)));

        final Result<ChatResponse> result = model().chat(cappedRequest());

        assertThat(Objects.requireNonNull(result.error(), "error").code()).isEqualTo(ErrorCode.timeout);
        server.verify(2, postRequestedFor(urlEqualTo(CHAT_PATH)));
    }

    @Test
    void chat_uncappedRequestTimesOut_retryAddsASeedAndStaysUncapped() {
        server.stubFor(post(urlEqualTo(CHAT_PATH))
                .inScenario("uncapped timeout then answer")
                .whenScenarioStateIs(Scenario.STARTED)
                .willReturn(
                        aResponse().withFixedDelay(SLOWER_THAN_THE_TIMEOUT_MS).withBody(REPLY))
                .willSetStateTo("answering"));
        server.stubFor(post(urlEqualTo(CHAT_PATH))
                .inScenario("uncapped timeout then answer")
                .whenScenarioStateIs("answering")
                .willReturn(aResponse().withBody(REPLY)));

        final Result<ChatResponse> result =
                model().chat(new ChatRequest(List.of(new ChatMessage(ChatRole.USER, "Translate this."))));

        assertThat(result.isOk()).isTrue();
        assertThat(bodies().get(1)).contains("\"seed\":2").doesNotContain("max_tokens");
    }

    // The Ollama-native reply streams; a stream that sends nothing within its idle gap times out the same way.
    @Test
    void chat_ollamaStreamSilentPastItsIdleGap_retriesOnceWithASeedAndALowerCap() {
        server.stubFor(post(urlEqualTo(OLLAMA_CHAT_PATH))
                .inScenario("ollama silent then answer")
                .whenScenarioStateIs(Scenario.STARTED)
                .willReturn(
                        aResponse().withFixedDelay(SLOWER_THAN_THE_TIMEOUT_MS).withBody(OLLAMA_REPLY))
                .willSetStateTo("answering"));
        server.stubFor(post(urlEqualTo(OLLAMA_CHAT_PATH))
                .inScenario("ollama silent then answer")
                .whenScenarioStateIs("answering")
                .willReturn(aResponse().withBody(OLLAMA_REPLY)));

        final Result<ChatResponse> result = model(ProviderKind.OLLAMA, "").chat(cappedRequest());

        assertThat(result.data()).extracting(ChatResponse::content).isEqualTo("translated");
        final List<String> bodies = bodies(OLLAMA_CHAT_PATH);
        assertThat(bodies).hasSize(2);
        assertThat(bodies.get(0)).contains("\"num_predict\":400").doesNotContain("\"seed\"");
        assertThat(bodies.get(1)).contains("\"num_predict\":300", "\"seed\":2");
    }

    private List<String> bodies() {
        return bodies(CHAT_PATH);
    }

    private List<String> bodies(String path) {
        return server.findAll(postRequestedFor(urlEqualTo(path))).stream()
                .map(LoggedRequest::getBodyAsString)
                .toList();
    }

    private static ChatRequest cappedRequest() {
        return new ChatRequest(
                List.of(new ChatMessage(ChatRole.USER, "Translate this.")), null, null, null, null, null, 400);
    }

    private GatedChatModel model() {
        return model(ProviderKind.OPENAI_COMPATIBLE, "/v1");
    }

    private GatedChatModel model(ProviderKind kind, String basePath) {
        final ProviderConfig config = new ProviderConfig(
                "test", kind, URI.create(server.baseUrl() + basePath), Duration.ofSeconds(2), Duration.ofSeconds(1));
        final ProviderClientFactory clients = new ProviderClientFactory(
                new HttpExchange(new HttpClients()), new LlmModule().objectMapper(), System::nanoTime);
        final RetryPolicy retryPolicy = new RetryPolicy(CLOCK, () -> 0.5, ignored -> {});
        return new GatedChatModel(clients.create(config), MODEL_ID, new InferenceGate(), retryPolicy);
    }
}
