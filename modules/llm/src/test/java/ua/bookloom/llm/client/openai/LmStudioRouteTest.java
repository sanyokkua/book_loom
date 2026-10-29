package ua.bookloom.llm.client.openai;

import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.FailureReason;
import ua.bookloom.api.Result;
import ua.bookloom.api.SafeDetails;
import ua.bookloom.api.llm.ChatMessage;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.ChatRole;
import ua.bookloom.api.llm.ProviderConfig;
import ua.bookloom.api.llm.ProviderKind;
import ua.bookloom.api.llm.ServerEngine;
import ua.bookloom.llm.LlmModule;
import ua.bookloom.llm.http.HttpClients;
import ua.bookloom.llm.http.HttpExchange;

/**
 * LM Studio's chat goes to its own route and falls back to the OpenAI one for the session; an id its list lacks stops
 * the call before any chat request, a typed variant is never sent, and a reply another model wrote is refused.
 */
class LmStudioRouteTest {

    private static final String MODEL_ID = "google/gemma-4-e4b";
    private static final String NATIVE_CHAT = "/api/v0/chat/completions";
    private static final String OPENAI_CHAT = "/v1/chat/completions";
    private static final String NATIVE_MODELS = "/api/v0/models";
    private static final String UNEXPECTED =
            "{\"error\":\"Unexpected endpoint or method. (POST /api/v0/chat/completions)\"}";

    private final WireMockServer server =
            new WireMockServer(WireMockConfiguration.options().dynamicPort().bindAddress("127.0.0.1"));
    private final ListAppender<ILoggingEvent> logged = new ListAppender<>();

    @BeforeEach
    void start() {
        server.start();
        final Logger bookloom = (Logger) LoggerFactory.getLogger("ua.bookloom");
        bookloom.setLevel(Level.TRACE);
        logged.start();
        bookloom.addAppender(logged);
    }

    @AfterEach
    void stop() {
        ((Logger) LoggerFactory.getLogger("ua.bookloom")).detachAppender(logged);
        server.stop();
    }

    @Test
    void chat_lmStudio_postsToApiV0ChatCompletionsNonStream() {
        stubList(MODEL_ID, "google/gemma-4-26b-a4b-qat");
        server.stubFor(post(urlEqualTo(NATIVE_CHAT)).willReturn(okJson(reply(MODEL_ID, ""))));

        final Result<ChatResponse> result =
                client(ServerEngine.LM_STUDIO).chat(MODEL_ID, request()).result();

        assertThat(result.isOk()).as("result: " + result.error()).isTrue();
        server.verify(
                1,
                postRequestedFor(urlEqualTo(NATIVE_CHAT)).withRequestBody(matchingJsonPath("$[?(@.stream == false)]")));
        server.verify(0, postRequestedFor(urlEqualTo(OPENAI_CHAT)));
    }

    @Test
    void chat_lmStudioUnexpectedEndpointBody_fallsBackToV1ForTheSession() {
        stubList(MODEL_ID);
        server.stubFor(post(urlEqualTo(NATIVE_CHAT)).willReturn(okJson(UNEXPECTED)));
        server.stubFor(post(urlEqualTo(OPENAI_CHAT)).willReturn(okJson(reply(MODEL_ID, ""))));
        final OpenAiCompatibleClient client = client(ServerEngine.LM_STUDIO);

        final Result<ChatResponse> first = client.chat(MODEL_ID, request()).result();
        final Result<ChatResponse> second = client.chat(MODEL_ID, request()).result();

        assertThat(first.isOk() && second.isOk()).isTrue();
        server.verify(1, postRequestedFor(urlEqualTo(NATIVE_CHAT)));
        server.verify(2, postRequestedFor(urlEqualTo(OPENAI_CHAT)));
        assertThat(client.chatUrl()).contains(URI.create(server.baseUrl() + OPENAI_CHAT));
        assertThat(infoLines("no own chat route")).hasSize(1);
    }

    @Test
    void chat_lmStudioStats_fillTimeToFirstToken() {
        stubList(MODEL_ID);
        server.stubFor(post(urlEqualTo(NATIVE_CHAT))
                .willReturn(okJson(reply(
                        MODEL_ID,
                        ",\"usage\":{\"prompt_tokens\":40,\"completion_tokens\":12},"
                                + "\"stats\":{\"time_to_first_token\":0.21,\"tokens_per_second\":40.0,"
                                + "\"generation_time\":0.3}"))));

        final ChatResponse response = Objects.requireNonNull(
                client(ServerEngine.LM_STUDIO)
                        .chat(MODEL_ID, request())
                        .result()
                        .data(),
                "response");

        assertThat(response.usage()).isNotNull();
        assertThat(Objects.requireNonNull(response.usage()).promptEval()).isEqualTo(Duration.ofMillis(210));
        assertThat(Objects.requireNonNull(response.usage()).generation()).isEqualTo(Duration.ofMillis(300));
    }

    @Test
    void chat_lmStudioStatsWithoutTimeToFirstToken_leaveItMissing() {
        stubList(MODEL_ID);
        server.stubFor(post(urlEqualTo(NATIVE_CHAT))
                .willReturn(okJson(reply(MODEL_ID, ",\"usage\":{\"prompt_tokens\":40,\"completion_tokens\":12}"))));

        final ChatResponse response = Objects.requireNonNull(
                client(ServerEngine.LM_STUDIO)
                        .chat(MODEL_ID, request())
                        .result()
                        .data(),
                "response");

        assertThat(Objects.requireNonNull(response.usage()).promptEval()).isNull();
    }

    @Test
    void chat_idNotListed_failsBeforeAnyChatRequest() {
        stubList("google/gemma-4-26b-a4b-qat", MODEL_ID);

        final Result<ChatResponse> result = client(ServerEngine.LM_STUDIO)
                .chat("google/gemma-4-26b-a4b-qatt", request())
                .result();

        final AppError error = Objects.requireNonNull(result.error(), "error");
        assertThat(error.code()).isEqualTo(ErrorCode.modelNotFound);
        assertThat(error.message()).contains("google/gemma-4-26b-a4b-qatt");
        server.verify(0, postRequestedFor(anyUrl()));
    }

    // The window words the stop in its own language from the reason, the engine and the id in the details.
    @Test
    void chat_lmStudioIdNotListed_saysLmStudioHasNoModelWithReasonEngineAndId() {
        stubList(MODEL_ID);

        final AppError error = Objects.requireNonNull(
                client(ServerEngine.LM_STUDIO)
                        .chat("gemma-4-e4b-typo", request())
                        .result()
                        .error(),
                "error");

        assertThat(error.message())
                .isEqualTo("LM Studio has no model \"gemma-4-e4b-typo\". It would answer with the model that is loaded."
                        + " Pick a model from the list.");
        assertThat(FailureReason.in(error.details())).contains(FailureReason.MODEL_NOT_LISTED);
        assertThat(ServerEngine.in(error.details())).contains(ServerEngine.LM_STUDIO);
        assertThat(SafeDetails.modelIn(error.details())).contains("gemma-4-e4b-typo");
    }

    @Test
    void chat_unslothIdNotListed_failsBeforeAnyChatRequestByItsOpenAiList() {
        server.stubFor(get(urlEqualTo("/v1/models")).willReturn(okJson("{\"data\":[{\"id\":\"unsloth/other\"}]}")));

        final Result<ChatResponse> result =
                client(ServerEngine.UNSLOTH).chat("unsloth/typo", request()).result();

        assertThat(Objects.requireNonNull(result.error(), "error").code()).isEqualTo(ErrorCode.modelNotFound);
        server.verify(0, postRequestedFor(anyUrl()));
    }

    @Test
    void chat_variantSuffix_isRemovedAndReportedOnce() {
        stubList(MODEL_ID);
        server.stubFor(post(urlEqualTo(NATIVE_CHAT)).willReturn(okJson(reply(MODEL_ID, ""))));
        final OpenAiCompatibleClient client = client(ServerEngine.LM_STUDIO);

        client.chat(MODEL_ID + "@q4_k_m", request());
        client.chat(MODEL_ID + "@q4_k_m", request());

        server.verify(
                2,
                postRequestedFor(urlEqualTo(NATIVE_CHAT))
                        .withRequestBody(matchingJsonPath("$.model", equalTo(MODEL_ID))));
        server.verify(1, getRequestedFor(urlEqualTo(NATIVE_MODELS)));
        assertThat(infoLines("Model variant removed")).hasSize(1);
    }

    @Test
    void chat_replyNamesAnotherModel_isWrongModelServed() {
        stubList("google/gemma-4-26b-a4b-qat", MODEL_ID);
        server.stubFor(post(urlEqualTo(NATIVE_CHAT)).willReturn(okJson(reply(MODEL_ID, ""))));

        final AppError error = Objects.requireNonNull(
                client(ServerEngine.LM_STUDIO)
                        .chat("google/gemma-4-26b-a4b-qat", request())
                        .result()
                        .error(),
                "error");

        assertThat(error.code()).isEqualTo(ErrorCode.modelNotFound);
        assertThat(FailureReason.in(error.details())).contains(FailureReason.WRONG_MODEL_SERVED);
        assertThat(error.message())
                .isEqualTo("LM Studio answered with google/gemma-4-e4b, not google/gemma-4-26b-a4b-qat."
                        + " Check the model id.");
    }

    @Test
    void chat_genericReplyNamesAnotherModel_keepsTheReply() {
        server.stubFor(post(urlEqualTo(OPENAI_CHAT)).willReturn(okJson(reply("other/model", ""))));

        final Result<ChatResponse> result =
                client(ServerEngine.GENERIC).chat(MODEL_ID, request()).result();

        assertThat(result.isOk()).isTrue();
        server.verify(0, getRequestedFor(anyUrl()));
    }

    private void stubList(final String... ids) {
        final StringBuilder rows = new StringBuilder();
        for (final String id : ids) {
            rows.append(rows.isEmpty() ? "" : ",")
                    .append("{\"id\":\"")
                    .append(id)
                    .append("\",\"type\":\"llm\",\"state\":\"not-loaded\"}");
        }
        server.stubFor(get(urlEqualTo(NATIVE_MODELS))
                .willReturn(okJson("{\"object\":\"list\",\"data\":[" + rows
                        + ",{\"id\":\"text-embedding-nomic-embed-text-v1.5\",\"type\":\"embeddings\"}]}")));
    }

    private List<String> infoLines(final String fragment) {
        return logged.list.stream()
                .filter(event -> event.getLevel() == Level.INFO)
                .map(ILoggingEvent::getFormattedMessage)
                .filter(line -> line.contains(fragment))
                .toList();
    }

    private static String reply(final String model, final String extra) {
        return "{\"model\":\"" + model
                + "\",\"choices\":[{\"message\":{\"content\":\"done\"},\"finish_reason\":\"stop\"}]" + extra + "}";
    }

    private static ChatRequest request() {
        return new ChatRequest(List.of(new ChatMessage(ChatRole.USER, "hello")));
    }

    private OpenAiCompatibleClient client(final ServerEngine engine) {
        final ProviderConfig config = new ProviderConfig(
                        "lmstudio",
                        ProviderKind.OPENAI_COMPATIBLE,
                        URI.create(server.baseUrl() + "/v1"),
                        Duration.ofSeconds(2),
                        Duration.ofSeconds(2))
                .withEngine(engine);
        return new OpenAiCompatibleClient(
                config, new HttpExchange(new HttpClients()), new LlmModule().objectMapper(), System::nanoTime);
    }
}
