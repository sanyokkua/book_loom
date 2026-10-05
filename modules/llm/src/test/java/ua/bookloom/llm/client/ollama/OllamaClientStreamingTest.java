package ua.bookloom.llm.client.ollama;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import java.net.URI;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ChatMessage;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.ChatRole;
import ua.bookloom.api.llm.FinishReason;
import ua.bookloom.api.llm.ProviderConfig;
import ua.bookloom.api.llm.ProviderKind;
import ua.bookloom.api.llm.TokenUsage;
import ua.bookloom.api.pipeline.CallKind;
import ua.bookloom.llm.LlmModule;
import ua.bookloom.llm.http.HttpClients;
import ua.bookloom.llm.http.HttpExchange;

/**
 * The Ollama-native client streams its reply as NDJSON and assembles the same whole response a single reply gave, so
 * a reply that stops sending is caught by its idle gap instead of by the whole call's timeout.
 */
class OllamaClientStreamingTest {

    private static final String MODEL_ID = "gemma4:e4b-mlx";
    private static final String CHAT_PATH = "/api/chat";
    private static final String STREAM = """
            {"model":"gemma4:e4b-mlx","message":{"role":"assistant","content":"<think>x</think>Hel"},"done":false}
            {"model":"gemma4:e4b-mlx","message":{"role":"assistant","content":"lo"},"done":false}
            {"model":"gemma4:e4b-mlx","message":{"role":"assistant","content":""},"done":true,\
            "done_reason":"stop","prompt_eval_count":12,"eval_count":2,"eval_duration":1000000}
            """;
    private static final int RUNAWAY_CAP = 20;
    private static final String RUNAWAY_STREAM = String.join(
            "",
            Collections.nCopies(
                    2000, "{\"model\":\"gemma4:e4b-mlx\",\"message\":{\"content\":\"la \"},\"done\":false}\n"));
    // Two lines eight seconds apart: far longer than the one-second idle gap the tests allow.
    private static final int STALLED_STREAM_MS = 8000;

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
    void chat_streamedReply_assemblesContentFinishAndUsageAndAsksForAStream() {
        stubStream(aResponse().withBody(STREAM));

        final Result<ChatResponse> result =
                client(Duration.ofSeconds(2)).chat(MODEL_ID, draft()).result();

        assertThat(result.data())
                .isEqualTo(new ChatResponse("Hello", FinishReason.STOP, new TokenUsage(12, 2, Duration.ofMillis(1))));
        server.verify(
                postRequestedFor(urlEqualTo(CHAT_PATH)).withRequestBody(matchingJsonPath("$.stream", equalTo("true"))));
    }

    @Test
    void chat_streamArrivingSlowlyButSteadily_answers() {
        stubStream(aResponse().withBody(STREAM).withChunkedDribbleDelay(5, 1500));

        final Result<ChatResponse> result =
                client(Duration.ofSeconds(1)).chat(MODEL_ID, draft()).result();

        assertThat(result.data()).extracting(ChatResponse::content).isEqualTo("Hello");
    }

    @Test
    void chat_streamThatStopsSending_timesOutAtItsIdleGapNotTheCallLimit() {
        stubStream(aResponse().withBody(STREAM).withChunkedDribbleDelay(2, STALLED_STREAM_MS));
        final long started = System.nanoTime();

        final Result<ChatResponse> result =
                client(Duration.ofSeconds(1)).chat(MODEL_ID, draft()).result();

        assertThat(Objects.requireNonNull(result.error(), "error").code()).isEqualTo(ErrorCode.timeout);
        assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofSeconds(6));
    }

    // A reply still sending when its whole limit runs out is ended as well: no kind and no expected output keep the
    // provider's two seconds, which a steady four-second stream outlasts.
    @Test
    void chat_streamOutlastingTheWholeCallLimit_timesOut() {
        stubStream(aResponse().withBody(STREAM).withChunkedDribbleDelay(10, 4000));
        final long started = System.nanoTime();

        final Result<ChatResponse> result = client(Duration.ofSeconds(2))
                .chat(MODEL_ID, new ChatRequest(List.of(new ChatMessage(ChatRole.USER, "hello"))))
                .result();

        assertThat(Objects.requireNonNull(result.error(), "error").code()).isEqualTo(ErrorCode.timeout);
        assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofMillis(3500));
    }

    @Test
    void chat_interruptedWhileTheStreamStalls_returnsCancelledPromptly() throws Exception {
        stubStream(aResponse().withBody(STREAM).withChunkedDribbleDelay(2, STALLED_STREAM_MS));
        final AtomicReference<@Nullable Thread> caller = new AtomicReference<>();
        final ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            final Future<Result<ChatResponse>> call = executor.submit(() -> {
                caller.set(Thread.currentThread());
                return client(Duration.ofSeconds(30)).chat(MODEL_ID, draft()).result();
            });
            awaitRequest();
            Objects.requireNonNull(caller.get(), "caller").interrupt();

            final Result<ChatResponse> result = call.get(3, TimeUnit.SECONDS);

            assertThat(Objects.requireNonNull(result.error(), "error").code()).isEqualTo(ErrorCode.cancelled);
        } finally {
            executor.shutdownNow();
        }
    }

    // A model that loops past its cap on a server that ignores num_predict: 2,000 one-token lines over 40 s, never a
    // final part. The guard cuts it at twice the cap plus the margin and reads it as a reply that finished for length.
    @Test
    void chat_streamRunningFarPastItsCap_isCutAsLengthWithoutWaitingForTheTimeout() {
        stubStream(aResponse().withBody(RUNAWAY_STREAM).withChunkedDribbleDelay(400, 40_000));
        final long started = System.nanoTime();

        final Result<ChatResponse> result = client(Duration.ofSeconds(60))
                .chat(MODEL_ID, capped(RUNAWAY_CAP))
                .result();

        assertThat(result.data()).extracting(ChatResponse::finishReason).isEqualTo(FinishReason.LENGTH);
        assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofSeconds(15));
        server.verify(postRequestedFor(urlEqualTo(CHAT_PATH))
                .withRequestBody(matchingJsonPath("$.options.num_predict", equalTo(String.valueOf(RUNAWAY_CAP)))));
    }

    @Test
    void maxLines_cappedRequest_isTwiceTheCapPlusTheMargin() {
        assertThat(OllamaClient.maxLines(capped(RUNAWAY_CAP))).isEqualTo(104L);
    }

    @Test
    void maxLines_noCapNoContext_isUnbounded() {
        assertThat(OllamaClient.maxLines(new ChatRequest(List.of(new ChatMessage(ChatRole.USER, "hello")))))
                .isEqualTo(Long.MAX_VALUE);
    }

    @Test
    void chat_errorLineInsideTheStream_returnsUpstream() {
        stubStream(aResponse().withBody("""
                        {"model":"gemma4:e4b-mlx","message":{"content":"Hel"},"done":false}
                        {"error":"llama runner process has terminated"}
                        """));

        final Result<ChatResponse> result =
                client(Duration.ofSeconds(2)).chat(MODEL_ID, draft()).result();

        assertThat(Objects.requireNonNull(result.error(), "error").code()).isEqualTo(ErrorCode.upstream);
    }

    private void stubStream(ResponseDefinitionBuilder response) {
        server.stubFor(
                post(urlEqualTo(CHAT_PATH)).willReturn(response.withHeader("Content-Type", "application/x-ndjson")));
    }

    private void awaitRequest() throws InterruptedException {
        final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (System.nanoTime() < deadline && server.getAllServeEvents().isEmpty()) {
            Thread.sleep(10);
        }
        Thread.sleep(200);
    }

    // A draft expecting 400 tokens may take 200 s in all, while the provider's configured timeout is the idle gap.
    private static ChatRequest draft() {
        return new ChatRequest(
                List.of(new ChatMessage(ChatRole.USER, "hello")),
                null,
                null,
                null,
                null,
                400,
                null,
                null,
                CallKind.DRAFT);
    }

    private static ChatRequest capped(int cap) {
        return new ChatRequest(
                List.of(new ChatMessage(ChatRole.USER, "hello")),
                null,
                null,
                null,
                null,
                cap,
                cap,
                null,
                CallKind.DIRECTED_FIX);
    }

    private OllamaClient client(Duration requestTimeout) {
        final ProviderConfig config = new ProviderConfig(
                "ollama", ProviderKind.OLLAMA, URI.create(server.baseUrl()), Duration.ofSeconds(2), requestTimeout);
        return new OllamaClient(config, new HttpExchange(new HttpClients()), new LlmModule().objectMapper());
    }
}
