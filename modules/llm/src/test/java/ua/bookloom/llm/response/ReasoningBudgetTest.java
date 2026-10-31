package ua.bookloom.llm.response;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.FailureReason;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ChatMessage;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.ChatRole;
import ua.bookloom.api.llm.ProviderConfig;
import ua.bookloom.api.llm.ProviderKind;
import ua.bookloom.api.llm.ReasoningLevel;
import ua.bookloom.api.llm.ServerEngine;
import ua.bookloom.llm.LlmModule;
import ua.bookloom.llm.client.ollama.OllamaClient;
import ua.bookloom.llm.client.openai.OpenAiCompatibleClient;
import ua.bookloom.llm.http.HttpClients;
import ua.bookloom.llm.http.HttpExchange;
import ua.bookloom.llm.provider.ProviderClient;

/** A reply that spent its whole answer thinking is asked once more, on both dialects, and only once (design D20). */
class ReasoningBudgetTest {

    private static final int CAP = 128;
    private static final int WINDOW = 8192;

    private final WireMockServer server =
            new WireMockServer(WireMockConfiguration.options().dynamicPort().bindAddress("127.0.0.1"));

    /** The two dialects: where each chats, what a reply spent on thinking looks like, and its fields. */
    enum Dialect {
        OLLAMA(
                "/api/chat",
                "{\"model\":\"m\",\"message\":{\"role\":\"assistant\",\"content\":\"\",\"thinking\":\"Let me see\"},"
                        + "\"done\":true,\"done_reason\":\"length\"}",
                "{\"model\":\"m\",\"message\":{\"role\":\"assistant\",\"content\":\"Привіт\"},\"done\":true,"
                        + "\"done_reason\":\"stop\"}",
                "$.think",
                "$.options.num_predict"),
        LM_STUDIO(
                "/api/v0/chat/completions",
                "{\"model\":\"m\",\"choices\":[{\"message\":{\"content\":\"\",\"reasoning_content\":\"Thinking"
                        + " Process\"},\"finish_reason\":\"length\"}]}",
                "{\"model\":\"m\",\"choices\":[{\"message\":{\"content\":\"Привіт\"},\"finish_reason\":\"stop\"}]}",
                "$.reasoning_effort",
                "$.max_tokens");

        private final String path;
        private final String spent;
        private final String answer;
        private final String thinkPath;
        private final String capPath;

        Dialect(
                final String path,
                final String spent,
                final String answer,
                final String thinkPath,
                final String capPath) {
            this.path = path;
            this.spent = spent;
            this.answer = answer;
            this.thinkPath = thinkPath;
            this.capPath = capPath;
        }
    }

    @BeforeEach
    void start() {
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop();
    }

    @ParameterizedTest
    @EnumSource(Dialect.class)
    void reask_blankTextWithThinkingAndLength_retriesOnceWithReasoningOff(final Dialect dialect) {
        stubSequence(dialect, dialect.spent, dialect.answer);

        final Result<ChatResponse> result =
                client(dialect).chat("m", request(ReasoningLevel.ON)).result();

        assertThat(result.data()).extracting(ChatResponse::content).isEqualTo("Привіт");
        server.verify(2, postRequestedFor(urlEqualTo(dialect.path)));
        server.verify(1, postRequestedFor(urlEqualTo(dialect.path)).withRequestBody(off(dialect)));
    }

    @ParameterizedTest
    @EnumSource(Dialect.class)
    void reask_modelCannotTurnOff_raisesCapFourTimes(final Dialect dialect) {
        stubSequence(dialect, dialect.spent, dialect.answer);

        final Result<ChatResponse> result =
                client(dialect).chat("m", request(ReasoningLevel.OFF)).result();

        assertThat(result.data()).extracting(ChatResponse::content).isEqualTo("Привіт");
        server.verify(1, postRequestedFor(urlEqualTo(dialect.path)).withRequestBody(cap(dialect, 512)));
    }

    // Four times a cap of 3,000 is past the window; the repeat gets what the window leaves after a prompt of 4,500
    // characters (about 1,500 tokens or more), never the whole window.
    @ParameterizedTest
    @EnumSource(Dialect.class)
    void reask_raisedCapPastTheWindow_leavesRoomForThePrompt(final Dialect dialect) {
        stubSequence(dialect, dialect.spent, dialect.answer);
        final ChatRequest request = new ChatRequest(
                List.of(
                        new ChatMessage(ChatRole.SYSTEM, "Translate."),
                        new ChatMessage(ChatRole.USER, "a".repeat(4500))),
                null,
                null,
                ReasoningLevel.OFF,
                WINDOW,
                null,
                3000,
                null,
                null);

        client(dialect).chat("m", request);

        final List<Integer> caps = server.findAll(postRequestedFor(urlEqualTo(dialect.path))).stream()
                .map(sent -> com.jayway.jsonpath.JsonPath.<Integer>read(sent.getBodyAsString(), dialect.capPath))
                .toList();
        assertThat(caps).hasSize(2);
        assertThat(caps.get(1)).isGreaterThan(3000).isLessThanOrEqualTo(WINDOW - 1500);
    }

    @ParameterizedTest
    @EnumSource(Dialect.class)
    void reask_sameAgain_isReasoningExhausted(final Dialect dialect) {
        stubSequence(dialect, dialect.spent, dialect.spent);

        final AppError error =
                client(dialect).chat("m", request(ReasoningLevel.ON)).result().error();

        assertThat(error).isNotNull();
        assertThat(error.code()).isEqualTo(ErrorCode.emptyCompletion);
        assertThat(FailureReason.tokenIn(error.details())).isEqualTo("reasoning-exhausted");
        server.verify(2, postRequestedFor(urlEqualTo(dialect.path)));
    }

    // The scenario's reply: an opener cut off at the cap counts as reasoning though no field holds any.
    @Test
    void reask_unclosedOpenerAtTheCap_isSentOnceMore() {
        stubSequence(
                Dialect.LM_STUDIO,
                "{\"model\":\"m\",\"choices\":[{\"message\":{\"content\":\"<think>first I will\"},"
                        + "\"finish_reason\":\"length\"}]}",
                Dialect.LM_STUDIO.answer);

        final Result<ChatResponse> result =
                client(Dialect.LM_STUDIO).chat("m", request(ReasoningLevel.OFF)).result();

        assertThat(result.data()).extracting(ChatResponse::content).isEqualTo("Привіт");
        server.verify(2, postRequestedFor(urlEqualTo(Dialect.LM_STUDIO.path)));
    }

    @Test
    void reask_aCheckOnOllama_isSentOnceAsAsked() {
        stubSequence(Dialect.OLLAMA, Dialect.OLLAMA.spent, Dialect.OLLAMA.answer);

        client(Dialect.OLLAMA).forChecks().chat("m", request(ReasoningLevel.ON));

        server.verify(1, postRequestedFor(urlEqualTo(Dialect.OLLAMA.path)));
    }

    private static com.github.tomakehurst.wiremock.matching.StringValuePattern off(final Dialect dialect) {
        return matchingJsonPath(dialect.thinkPath, equalTo(dialect == Dialect.OLLAMA ? "false" : "none"));
    }

    private static com.github.tomakehurst.wiremock.matching.StringValuePattern cap(
            final Dialect dialect, final int value) {
        return matchingJsonPath(dialect.capPath, equalTo(Integer.toString(value)));
    }

    private void stubSequence(final Dialect dialect, final String first, final String second) {
        server.stubFor(post(urlEqualTo(dialect.path))
                .inScenario("budget")
                .whenScenarioStateIs(Scenario.STARTED)
                .willReturn(aResponse().withStatus(200).withBody(first))
                .willSetStateTo("second"));
        server.stubFor(post(urlEqualTo(dialect.path))
                .inScenario("budget")
                .whenScenarioStateIs("second")
                .willReturn(aResponse().withStatus(200).withBody(second)));
    }

    private static ChatRequest request(final ReasoningLevel level) {
        return new ChatRequest(
                List.of(new ChatMessage(ChatRole.SYSTEM, "Translate."), new ChatMessage(ChatRole.USER, "Hello")),
                null,
                null,
                level,
                WINDOW,
                null,
                CAP,
                null,
                null);
    }

    private ProviderClient client(final Dialect dialect) {
        final HttpExchange exchange = new HttpExchange(new HttpClients());
        if (dialect == Dialect.OLLAMA) {
            return new OllamaClient(config(ProviderKind.OLLAMA, ""), exchange, new LlmModule().objectMapper());
        }
        return new OpenAiCompatibleClient(
                config(ProviderKind.OPENAI_COMPATIBLE, "/v1").withEngine(ServerEngine.LM_STUDIO),
                exchange,
                new LlmModule().objectMapper(),
                System::nanoTime);
    }

    private ProviderConfig config(final ProviderKind kind, final String suffix) {
        return new ProviderConfig(
                "budget", kind, URI.create(server.baseUrl() + suffix), Duration.ofSeconds(2), Duration.ofSeconds(2));
    }
}
