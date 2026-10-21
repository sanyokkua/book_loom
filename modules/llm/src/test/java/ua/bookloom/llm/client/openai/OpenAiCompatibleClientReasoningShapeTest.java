package ua.bookloom.llm.client.openai;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ua.bookloom.api.llm.ChatMessage;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ChatRole;
import ua.bookloom.api.llm.ProviderConfig;
import ua.bookloom.api.llm.ProviderKind;
import ua.bookloom.api.llm.ReasoningLevel;
import ua.bookloom.api.pipeline.CallKind;
import ua.bookloom.llm.LlmModule;
import ua.bookloom.llm.http.HttpClients;
import ua.bookloom.llm.http.HttpExchange;

/**
 * Thinking per call (15i.X2) at the OpenAI-compatible HTTP seam (LM Studio): a call with thinking on states a
 * {@code reasoning_effort} and gets room in {@code max_tokens} for its reasoning, a call with it off sends today's
 * request unchanged.
 */
class OpenAiCompatibleClientReasoningShapeTest {

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
        server.stubFor(post(urlEqualTo(CHAT_PATH)).willReturn(okJson(REPLY)));
    }

    @AfterEach
    void stopServer() {
        server.stop();
    }

    // The effort a thinking call asks for is the server's usual middle level.
    @Test
    void chat_thinkingOn_sendsAReasoningEffortOfMedium() {
        client().chat(MODEL_ID, request(300, ReasoningLevel.ON));

        server.verify(postRequestedFor(urlEqualTo(CHAT_PATH))
                .withRequestBody(matchingJsonPath("$.reasoning_effort", equalTo("medium"))));
    }

    // The three levels map to none / low / medium; no level sends no key at all.
    @ParameterizedTest
    @CsvSource({"OFF,none", "LOW,low", "ON,medium"})
    void chat_level_sendsTheMatchingReasoningEffort(final ReasoningLevel level, final String effort) {
        client().chat(MODEL_ID, request(300, level));

        server.verify(postRequestedFor(urlEqualTo(CHAT_PATH))
                .withRequestBody(matchingJsonPath("$.reasoning_effort", equalTo(effort))));
    }

    // IF the level is Low, THEN max_tokens grows by the reasoning headroom as for On; Off keeps the cap as asked.
    @ParameterizedTest
    @CsvSource({"OFF,300", "LOW,1324", "ON,1324"})
    void chat_level_growsMaxTokensOnlyWhenTheModelReasons(final ReasoningLevel level, final String maxTokens) {
        client().chat(MODEL_ID, request(300, level));

        server.verify(postRequestedFor(urlEqualTo(CHAT_PATH))
                .withRequestBody(matchingJsonPath("$.max_tokens", equalTo(maxTokens))));
    }

    // IF a call has thinking on, THEN its max_tokens grows by 1,024 tokens for the reasoning.
    @Test
    void chat_thinkingOn_growsMaxTokensByTheReasoningHeadroom() {
        client().chat(MODEL_ID, request(300, ReasoningLevel.ON));

        server.verify(postRequestedFor(urlEqualTo(CHAT_PATH))
                .withRequestBody(matchingJsonPath("$.max_tokens", equalTo("1324"))));
    }

    @Test
    void chat_thinkingOff_sendsNoneAndTheCapAsAsked() {
        client().chat(MODEL_ID, request(300, ReasoningLevel.OFF));

        server.verify(postRequestedFor(urlEqualTo(CHAT_PATH))
                .withRequestBody(matchingJsonPath("$.reasoning_effort", equalTo("none")))
                .withRequestBody(matchingJsonPath("$.max_tokens", equalTo("300"))));
    }

    @Test
    void chat_thinkingUnspecified_omitsTheEffortAndKeepsTheCap() {
        client().chat(MODEL_ID, request(300, null));

        assertThat(server.getAllServeEvents())
                .extracting(event -> event.getRequest().getBodyAsString())
                .allSatisfy(body ->
                        assertThat(body).doesNotContain("reasoning_effort").contains("\"max_tokens\":300"));
    }

    @Test
    void chat_thinkingOnWithoutACap_staysUncapped() {
        client().chat(
                        MODEL_ID,
                        new ChatRequest(
                                List.of(new ChatMessage(ChatRole.USER, "hello")),
                                null,
                                null,
                                ReasoningLevel.ON,
                                null,
                                null,
                                null,
                                null,
                                null));

        assertThat(server.getAllServeEvents())
                .extracting(event -> event.getRequest().getBodyAsString())
                .allSatisfy(body -> assertThat(body)
                        .contains("\"reasoning_effort\":\"medium\"")
                        .doesNotContain("max_tokens"));
    }

    private static ChatRequest request(final int cap, final @Nullable ReasoningLevel thinking) {
        return new ChatRequest(
                List.of(new ChatMessage(ChatRole.USER, "hello")),
                null,
                null,
                thinking,
                8192,
                cap,
                cap,
                null,
                CallKind.DRAFT);
    }

    private OpenAiCompatibleClient client() {
        final ProviderConfig config = new ProviderConfig(
                "lmstudio",
                ProviderKind.OPENAI_COMPATIBLE,
                URI.create(server.baseUrl() + "/v1"),
                Duration.ofSeconds(2),
                Duration.ofSeconds(2));
        return new OpenAiCompatibleClient(
                config, new HttpExchange(new HttpClients()), new LlmModule().objectMapper(), System::nanoTime);
    }
}
