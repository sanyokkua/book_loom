package ua.bookloom.llm.client.ollama;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import java.net.URI;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ChatMessage;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.ChatRole;
import ua.bookloom.api.llm.FinishReason;
import ua.bookloom.api.llm.ProviderConfig;
import ua.bookloom.api.llm.ProviderKind;
import ua.bookloom.api.llm.ReasoningLevel;
import ua.bookloom.api.pipeline.CallKind;
import ua.bookloom.llm.LlmModule;
import ua.bookloom.llm.http.HttpClients;
import ua.bookloom.llm.http.HttpExchange;

/**
 * Thinking per call (15i.X2) at the Ollama-native HTTP seam: a call with thinking on asks for {@code think}, gets the
 * room its reasoning needs on top of the answer's cap whichever model it is, and its thinking lines neither trip the
 * runaway line guard nor count against the reply.
 */
class OllamaClientReasoningTest {

    private static final String MODEL_ID = "gemma4:e4b-mlx";
    private static final String CHAT_PATH = "/api/chat";
    private static final int CAP = 10;
    // Twice the cap plus the 64-line margin: what the guard allowed before thinking had any room of its own.
    private static final int OLD_LIMIT = 84;
    // The same bound with 1,024 tokens of room for thinking: (10 + 1024) * 2 + 64.
    private static final int GROWN_LIMIT = 2132;
    private static final String THINKING_LINE =
            "{\"model\":\"gemma4:e4b-mlx\",\"message\":{\"role\":\"assistant\",\"content\":\"\",\"thinking\":\"hm \"},\"done\":false}\n";
    private static final String CONTENT_LINE =
            "{\"model\":\"gemma4:e4b-mlx\",\"message\":{\"role\":\"assistant\",\"content\":\"la \"},\"done\":false}\n";
    private static final String ANSWER = """
            {"model":"gemma4:e4b-mlx","message":{"role":"assistant","content":"Hi"},"done":false}
            {"model":"gemma4:e4b-mlx","message":{"role":"assistant","content":""},"done":true,"done_reason":"stop"}
            """;
    private static final String SIMPLE_REPLY = """
            {"model":"gemma4:e4b-mlx","message":{"role":"assistant","content":"Hi"},"done":true,"done_reason":"stop"}
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

    // IF a call has thinking on, THEN Ollama is asked to think and its cap grows by 1,024 tokens, on any model.
    @ParameterizedTest
    @CsvSource({"gemma4:e4b-mlx,1324", "qwen3:8b,1324", "gpt-oss:20b,1324"})
    void chat_thinkingOn_sendsThinkTrueAndTheCapWithHeadroom(final String model, final int numPredict) {
        stubReply(SIMPLE_REPLY);

        client().chat(model, request(300, ReasoningLevel.ON));

        assertSent(model, "$.think", "true");
        assertSent(model, "$.options.num_predict", String.valueOf(numPredict));
    }

    // IF thinking is off, THEN the request is today's: think false, the cap as asked (gpt-oss keeps its low level and
    // its room).
    @ParameterizedTest
    @CsvSource({"gemma4:e4b-mlx,false,300", "qwen3:8b,false,300", "gpt-oss:20b,low,1324"})
    void chat_thinkingOff_sendsTodaysRequest(final String model, final String think, final int numPredict) {
        stubReply(SIMPLE_REPLY);

        client().chat(model, request(300, ReasoningLevel.OFF));

        assertSent(model, "$.think", think);
        assertSent(model, "$.options.num_predict", String.valueOf(numPredict));
    }

    // IF the level is Low, THEN Ollama is asked for "low" effort on any model and the cap grows like a thinking call's.
    @ParameterizedTest
    @CsvSource({"gemma4:e4b-mlx,1324", "qwen3:8b,1324", "gpt-oss:20b,1324"})
    void chat_thinkingLow_sendsThinkLowAndTheCapWithHeadroom(final String model, final int numPredict) {
        stubReply(SIMPLE_REPLY);

        client().chat(model, request(300, ReasoningLevel.LOW));

        assertSent(model, "$.think", "low");
        assertSent(model, "$.options.num_predict", String.valueOf(numPredict));
    }

    // Low streams reasoning lines too, so they do not count against the runaway bound.
    @Test
    void chat_thinkingLowWithManyThinkingLines_isNotCutAsARunaway() {
        stubReply(thinkingLines(300) + ANSWER);

        final Result<ChatResponse> result =
                client().chat(MODEL_ID, request(CAP, ReasoningLevel.LOW)).result();

        assertThat(result.data()).isEqualTo(new ChatResponse("Hi", FinishReason.STOP));
    }

    @Test
    void maxLines_thinkingLow_countsTheHeadroomTwice() {
        assertThat(OllamaClient.maxLines(MODEL_ID, request(100, ReasoningLevel.LOW)))
                .isEqualTo(2312L);
    }

    @Test
    void chat_thinkingUnspecified_omitsThinkAndKeepsTheCap() {
        stubReply(SIMPLE_REPLY);

        client().chat(MODEL_ID, request(300, null));

        assertThat(server.getAllServeEvents())
                .extracting(event -> event.getRequest().getBodyAsString())
                .allSatisfy(body -> assertThat(body).doesNotContain("\"think\"").contains("\"num_predict\":300"));
    }

    // 300 thinking lines are past the old 84-line bound, yet inside the room the server gave the thinking.
    @Test
    void chat_thinkingOnWithManyThinkingLines_isNotCutAsARunaway() {
        stubReply(thinkingLines(300) + ANSWER);

        final Result<ChatResponse> result =
                client().chat(MODEL_ID, request(CAP, ReasoningLevel.ON)).result();

        assertThat(result.data()).isEqualTo(new ChatResponse("Hi", FinishReason.STOP));
        assertThat(OLD_LIMIT).isLessThan(300);
    }

    // The thinking lines are not the reply: even more of them than the grown bound allows do not count against it.
    @Test
    void chat_thinkingOnWithMoreThinkingLinesThanTheGrownBound_stillAnswers() {
        stubReply(thinkingLines(GROWN_LIMIT + 200) + ANSWER);

        final Result<ChatResponse> result =
                client().chat(MODEL_ID, request(CAP, ReasoningLevel.ON)).result();

        assertThat(result.data()).isEqualTo(new ChatResponse("Hi", FinishReason.STOP));
    }

    // A reply that never stops is still a runaway with thinking on, cut at the grown bound and read as length.
    @Test
    void chat_thinkingOnWithARunawayAnswer_isCutAsLengthAtTheGrownBound() {
        stubReply(contentLines(GROWN_LIMIT + 200));

        final Result<ChatResponse> result =
                client().chat(MODEL_ID, request(CAP, ReasoningLevel.ON)).result();

        assertThat(result.data()).extracting(ChatResponse::finishReason).isEqualTo(FinishReason.LENGTH);
    }

    // IF thinking is off, THEN the guard is today's: a reply far past twice its cap is cut at 84 lines.
    @Test
    void chat_thinkingOffWithARunawayAnswer_isCutAtTodaysBound() {
        stubReply(contentLines(OLD_LIMIT + 200));

        final Result<ChatResponse> result =
                client().chat(MODEL_ID, request(CAP, ReasoningLevel.OFF)).result();

        assertThat(result.data()).extracting(ChatResponse::finishReason).isEqualTo(FinishReason.LENGTH);
    }

    @Test
    void maxLines_thinkingOn_countsTheHeadroomTwice() {
        assertThat(OllamaClient.maxLines(MODEL_ID, request(100, ReasoningLevel.ON)))
                .isEqualTo(2312L);
    }

    @Test
    void maxLines_thinkingOff_isTwiceTheCapPlusTheMargin() {
        assertThat(OllamaClient.maxLines(MODEL_ID, request(100, ReasoningLevel.OFF)))
                .isEqualTo(264L);
    }

    @Test
    void maxLines_gptOssWithThinkingOff_countsItsHeadroom() {
        assertThat(OllamaClient.maxLines("gpt-oss:20b", request(100, ReasoningLevel.OFF)))
                .isEqualTo(2312L);
    }

    @Test
    void maxLines_gptOssWithThinkingOn_countsTheHeadroomOnce() {
        assertThat(OllamaClient.maxLines("gpt-oss:20b", request(100, ReasoningLevel.ON)))
                .isEqualTo(2312L);
    }

    // With no cap the bound is the context window, which already holds the thinking: no room is added.
    @Test
    void maxLines_thinkingOnWithoutACap_isBoundByTheContextWindowAlone() {
        final ChatRequest uncapped = new ChatRequest(
                List.of(new ChatMessage(ChatRole.USER, "hello")),
                null,
                null,
                ReasoningLevel.ON,
                4096,
                null,
                null,
                null,
                CallKind.DRAFT);

        assertThat(OllamaClient.maxLines(MODEL_ID, uncapped)).isEqualTo(8256L);
    }

    private static String thinkingLines(final int count) {
        return String.join("", Collections.nCopies(count, THINKING_LINE));
    }

    private static String contentLines(final int count) {
        return String.join("", Collections.nCopies(count, CONTENT_LINE));
    }

    private void assertSent(final String model, final String path, final String expected) {
        server.verify(postRequestedFor(urlEqualTo(CHAT_PATH))
                .withRequestBody(matchingJsonPath("$.model", equalTo(model)))
                .withRequestBody(matchingJsonPath(path, equalTo(expected))));
    }

    private void stubReply(final String body) {
        server.stubFor(post(urlEqualTo(CHAT_PATH))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/x-ndjson")
                        .withBody(body)));
    }

    private static ChatRequest request(final int cap, final @Nullable ReasoningLevel thinking) {
        return new ChatRequest(
                List.of(new ChatMessage(ChatRole.USER, "hello")),
                null,
                null,
                thinking,
                null,
                cap,
                cap,
                null,
                CallKind.DRAFT);
    }

    private OllamaClient client() {
        final ProviderConfig config = new ProviderConfig(
                "ollama",
                ProviderKind.OLLAMA,
                URI.create(server.baseUrl()),
                Duration.ofSeconds(2),
                Duration.ofSeconds(30));
        return new OllamaClient(config, new HttpExchange(new HttpClients()), new LlmModule().objectMapper());
    }
}
