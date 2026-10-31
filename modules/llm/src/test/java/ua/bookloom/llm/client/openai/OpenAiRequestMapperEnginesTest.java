package ua.bookloom.llm.client.openai;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import ua.bookloom.api.llm.ChatMessage;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ChatRole;
import ua.bookloom.api.llm.OutputLengthField;
import ua.bookloom.api.llm.ProbeVerdicts;
import ua.bookloom.api.llm.ProbeVerdicts.Outcome;
import ua.bookloom.api.llm.ProbeVerdicts.ThinkingVerdict;
import ua.bookloom.api.llm.ProbeVerdicts.Verdict;
import ua.bookloom.api.llm.ProviderConfig;
import ua.bookloom.api.llm.ProviderKind;
import ua.bookloom.api.llm.ReasoningLevel;
import ua.bookloom.api.llm.ResponseFormat;
import ua.bookloom.api.llm.SamplingParams;
import ua.bookloom.api.llm.ServerEngine;
import ua.bookloom.api.pipeline.CallKind;
import ua.bookloom.llm.LlmModule;
import ua.bookloom.llm.engine.EngineFacts;
import ua.bookloom.llm.http.HttpClients;
import ua.bookloom.llm.http.HttpExchange;
import ua.bookloom.llm.provider.ProviderClient;
import ua.bookloom.llm.provider.RejectedCapability;
import ua.bookloom.llm.provider.SettingDrops;

/** Each engine's request fields at the OpenAI-compatible HTTP seam: one mapper, one traits row per engine (D17). */
class OpenAiRequestMapperEnginesTest {

    private static final String CHAT_PATH = "/v1/chat/completions";
    private static final String MODEL_ID = "mara-7b";
    private static final String REPLY = """
            {"model":"mara-7b","choices":[{"message":{"content":"reply"},"finish_reason":"stop"}]}
            """;
    private static final String PROPS_READING_THE_VARIABLE = """
            {"build_info":"b11669-620d2225c","role":"model","chat_template":"{% if enable_thinking %}<think>{% endif %}"}
            """;
    private static final String PROPS_NOT_READING_IT = """
            {"build_info":"b11669-620d2225c","role":"model","chat_template":"{{ messages }}"}
            """;
    private static final int CAP = 400;
    private static final Instant CHECKED = Instant.parse("2026-10-29T10:00:00Z");

    private final WireMockServer server =
            new WireMockServer(WireMockConfiguration.options().dynamicPort().bindAddress("127.0.0.1"));
    private final ObjectMapper json = new ObjectMapper();

    @BeforeEach
    void startServer() {
        server.start();
        server.stubFor(post(urlEqualTo(CHAT_PATH)).willReturn(okJson(REPLY)));
    }

    @AfterEach
    void stopServer() {
        server.stop();
    }

    @Test
    void serializeRequest_llamaServerOff_putsEnableThinkingFalseInKwargsOnly() throws IOException {
        server.stubFor(get(urlEqualTo("/props")).willReturn(okJson(PROPS_READING_THE_VARIABLE)));

        final JsonNode body = sentBody(client(ServerEngine.LLAMA_CPP), "qwen3.5-4b", off(null));

        assertThat(body.path("chat_template_kwargs").path("enable_thinking").isBoolean())
                .isTrue();
        assertThat(body.path("chat_template_kwargs").path("enable_thinking").asBoolean(true))
                .isFalse();
        assertThat(body.path("chat_template_kwargs").size()).isEqualTo(1);
        assertThat(body.has("reasoning_effort")).isFalse();
        assertThat(body.has("enable_thinking")).isFalse();
    }

    @Test
    void serializeRequest_llamaServerTemplateWithoutThatVariable_sendsNoKwarg() throws IOException {
        server.stubFor(get(urlEqualTo("/props")).willReturn(okJson(PROPS_NOT_READING_IT)));

        final JsonNode body = sentBody(client(ServerEngine.LLAMA_CPP), "qwen3.5-4b", off(null));

        assertThat(body.has("chat_template_kwargs")).isFalse();
        assertThat(body.has("reasoning_effort")).isFalse();
    }

    // gpt-oss's profile decides its lowest level; on llama-server it goes as an effort, never as the template switch,
    // which would erase it and fall back to medium.
    @Test
    void serializeRequest_llamaServerGptOss_sendsLowAndNoKwarg() throws IOException {
        server.stubFor(get(urlEqualTo("/props")).willReturn(okJson(PROPS_READING_THE_VARIABLE)));

        final JsonNode body =
                sentBody(client(ServerEngine.LLAMA_CPP), "gpt-oss-20b", request(null, ReasoningLevel.LOW, null));

        assertThat(body.path("reasoning_effort").asText()).isEqualTo("low");
        assertThat(body.has("chat_template_kwargs")).isFalse();
    }

    @Test
    void serializeRequest_ollamaOpenAiWithCompletionChoice_sendsMaxTokens() throws IOException {
        final JsonNode body = sentBody(
                client(ServerEngine.OLLAMA),
                "qwen3.5:9b",
                request(null, null, OutputLengthField.MAX_COMPLETION_TOKENS));

        assertThat(body.path("max_tokens").asInt(-1)).isEqualTo(CAP);
        assertThat(body.has("max_completion_tokens")).isFalse();
    }

    @Test
    void serializeRequest_lmStudioWithCompletionChoice_keepsTheChoice() throws IOException {
        final JsonNode body = sentBody(
                client(ServerEngine.LM_STUDIO), MODEL_ID, request(null, null, OutputLengthField.MAX_COMPLETION_TOKENS));

        assertThat(body.path("max_completion_tokens").asInt(-1)).isEqualTo(CAP);
        assertThat(body.has("max_tokens")).isFalse();
    }

    @Test
    void serializeRequest_lmStudioOff_sendsReasoningEffortNone() throws IOException {
        final JsonNode body = sentBody(client(ServerEngine.LM_STUDIO), MODEL_ID, off(null));

        assertThat(body.path("reasoning_effort").asText()).isEqualTo("none");
        assertThat(body.has("chat_template_kwargs")).isFalse();
    }

    @Test
    void serializeRequest_unslothOff_sendsTopLevelEnableThinkingFalse() throws IOException {
        final JsonNode body = sentBody(client(ServerEngine.UNSLOTH), MODEL_ID, off(null));

        assertThat(body.path("enable_thinking").isBoolean()).isTrue();
        assertThat(body.path("enable_thinking").asBoolean(true)).isFalse();
        assertThat(body.has("reasoning_effort")).isFalse();
        assertThat(body.has("chat_template_kwargs")).isFalse();
    }

    @Test
    void serializeRequest_genericWithoutVerdict_sendsNoSchemaAndNoReasoning() throws IOException {
        final JsonNode body = sentBody(client(ServerEngine.GENERIC), MODEL_ID, off(schema()));

        assertThat(body.has("response_format")).isFalse();
        assertThat(body.has("reasoning_effort")).isFalse();
        assertThat(body.has("chat_template_kwargs")).isFalse();
        assertThat(body.path("stream").asBoolean(true)).isFalse();
        assertThat(body.path("max_tokens").asInt(-1)).isEqualTo(CAP);
    }

    @Test
    void serializeRequest_genericWithHonouredSchemaVerdict_sendsSchema() throws IOException {
        final ProbeVerdicts honoured = new ProbeVerdicts(honoured(), null, null, null);

        final JsonNode body = sentBody(client(ServerEngine.GENERIC, honoured), MODEL_ID, off(schema()));

        assertThat(body.path("response_format").path("type").asText()).isEqualTo("json_schema");
        assertThat(body.has("reasoning_effort")).isFalse();
    }

    @Test
    void serializeRequest_genericWithHonouredKwargSwitch_sendsThatSwitchOnly() throws IOException {
        final ProbeVerdicts proved = new ProbeVerdicts(
                null, new ThinkingVerdict("chat_template_kwargs.enable_thinking", honoured()), null, null);

        final JsonNode body = sentBody(client(ServerEngine.GENERIC, proved), MODEL_ID, off(null));

        assertThat(body.path("chat_template_kwargs").path("enable_thinking").asBoolean(true))
                .isFalse();
        assertThat(body.has("reasoning_effort")).isFalse();
    }

    @Test
    void serializeRequest_openRouterWithoutVerdict_isTreatedAsGeneric() throws IOException {
        final JsonNode body = sentBody(client(ServerEngine.OPENROUTER), MODEL_ID, off(schema()));

        assertThat(body.has("response_format")).isFalse();
        assertThat(body.has("reasoning_effort")).isFalse();
    }

    // The Test's checks send what they ask for, so a check can earn a field for an unknown server.
    @Test
    void serializeRequest_genericForChecks_sendsSchemaAndEffortAsAsked() throws IOException {
        final JsonNode body = sentBody(client(ServerEngine.GENERIC).forChecks(), MODEL_ID, off(schema()));

        assertThat(body.path("response_format").path("type").asText()).isEqualTo("json_schema");
        assertThat(body.path("reasoning_effort").asText()).isEqualTo("none");
    }

    // A llama-server named by "Treat this server as..." with no Test still learns its template, by one GET.
    @Test
    void chat_llamaServerWithoutATest_readsPropsOnceForTwoCalls() {
        server.stubFor(get(urlEqualTo("/props")).willReturn(okJson(PROPS_READING_THE_VARIABLE)));
        final OpenAiCompatibleClient client = client(ServerEngine.LLAMA_CPP);

        client.chat("qwen3.5-4b", off(null));
        client.chat("qwen3.5-4b", off(null));

        server.verify(1, getRequestedFor(urlEqualTo("/props")));
        server.verify(2, postRequestedFor(urlEqualTo(CHAT_PATH)));
    }

    @Test
    void chat_llamaServerPropsUnreadable_sendsNoKwarg() throws IOException {
        server.stubFor(get(urlEqualTo("/props")).willReturn(okJson("not json")));

        final JsonNode body = sentBody(client(ServerEngine.LLAMA_CPP), "qwen3.5-4b", off(null));

        assertThat(body.has("chat_template_kwargs")).isFalse();
        assertThat(body.has("reasoning_effort")).isFalse();
    }

    @Test
    void chat_llamaServerRouter_readsTheTemplateOfTheModelId() throws IOException {
        server.stubFor(get(urlEqualTo("/props"))
                .willReturn(okJson("{\"build_info\":\"b11669-620d2225c\",\"role\":\"router\",\"n_ctx\":0}")));
        server.stubFor(get(urlEqualTo("/props?model=qwen3.5-4b")).willReturn(okJson(PROPS_READING_THE_VARIABLE)));

        final JsonNode body = sentBody(client(ServerEngine.LLAMA_CPP), "qwen3.5-4b", off(null));

        assertThat(body.path("chat_template_kwargs").path("enable_thinking").asBoolean(true))
                .isFalse();
        server.verify(1, getRequestedFor(urlEqualTo("/props?model=qwen3.5-4b")));
    }

    // A call that leaves reasoning alone asks for no switch, so llama-server is not asked about its template.
    @Test
    void chat_llamaServerNoReasoningLevel_sendsNoSwitchAndReadsPropsOnlyForTheRouterCheck() throws IOException {
        final JsonNode body = sentBody(client(ServerEngine.LLAMA_CPP), "qwen3.5-4b", request(null, null, null));

        assertThat(body.has("chat_template_kwargs")).isFalse();
        // One read of /props tells whether the server is a router whose ids are checked; no per-model read follows.
        server.verify(1, getRequestedFor(urlEqualTo("/props")));
    }

    @Test
    void serializeRequest_llamaServer_sendsFourSamplersNeutral() throws IOException {
        final JsonNode body = sentBody(client(ServerEngine.LLAMA_CPP), MODEL_ID, request(null, null, null));

        assertThat(body.path("top_p").asDouble(-1)).isEqualTo(1.0);
        assertThat(body.path("top_k").asInt(-1)).isEqualTo(0);
        assertThat(body.path("min_p").asDouble(-1)).isEqualTo(0.0);
        assertThat(body.path("repeat_penalty").asDouble(-1)).isEqualTo(1.0);
        assertThat(body.has("repetition_penalty")).isFalse();
    }

    @Test
    void serializeRequest_lmStudio_sendsFourSamplersNeutralUnderRepeatPenalty() throws IOException {
        final JsonNode body = sentBody(client(ServerEngine.LM_STUDIO), MODEL_ID, request(null, null, null));

        assertThat(body.path("top_p").asDouble(-1)).isEqualTo(1.0);
        assertThat(body.path("top_k").asInt(-1)).isEqualTo(0);
        assertThat(body.path("min_p").asDouble(-1)).isEqualTo(0.0);
        assertThat(body.path("repeat_penalty").asDouble(-1)).isEqualTo(1.0);
    }

    @Test
    void serializeRequest_unsloth_sendsEnableToolsFalseAndRepetitionPenalty() throws IOException {
        final JsonNode body = sentBody(client(ServerEngine.UNSLOTH), MODEL_ID, request(null, null, null));

        assertThat(body.path("enable_tools").isBoolean()).isTrue();
        assertThat(body.path("enable_tools").asBoolean(true)).isFalse();
        assertThat(body.path("repetition_penalty").asDouble(-1)).isEqualTo(1.0);
        assertThat(body.path("top_p").asDouble(-1)).isEqualTo(1.0);
        assertThat(body.path("top_k").asInt(-1)).isEqualTo(0);
        assertThat(body.path("min_p").asDouble(-1)).isEqualTo(0.0);
        assertThat(body.has("repeat_penalty")).isFalse();
    }

    @ParameterizedTest
    @EnumSource(
            value = ServerEngine.class,
            names = {"OLLAMA", "GENERIC", "OPENROUTER"})
    void serializeRequest_enginesWithNeutralDefaults_sendNoExtraSamplers(final ServerEngine engine) throws IOException {
        final JsonNode body = sentBody(client(engine), MODEL_ID, request(null, null, null));

        assertThat(body.has("top_p")).isFalse();
        assertThat(body.has("top_k")).isFalse();
        assertThat(body.has("min_p")).isFalse();
        assertThat(body.has("repeat_penalty")).isFalse();
        assertThat(body.has("repetition_penalty")).isFalse();
        assertThat(body.has("enable_tools")).isFalse();
    }

    @Test
    void serializeRequest_ollamaOpenAi_sendsNoExtraSamplers() throws IOException {
        final JsonNode body = sentBody(client(ServerEngine.OLLAMA), MODEL_ID, request(null, null, null));

        assertThat(body.has("top_p")).isFalse();
        assertThat(body.has("top_k")).isFalse();
        assertThat(body.has("min_p")).isFalse();
        assertThat(body.has("repeat_penalty")).isFalse();
    }

    @Test
    void serializeRequest_unslothWithExplicitSamplers_keepsTheRequestsOwnValues() throws IOException {
        final ChatRequest explicit = request(null, null, null).withSampling(new SamplingParams(0.8, 20, null, 1.1));

        final JsonNode body = sentBody(client(ServerEngine.UNSLOTH), MODEL_ID, explicit);

        assertThat(body.path("top_p").asDouble(-1)).isEqualTo(0.8);
        assertThat(body.path("top_k").asInt(-1)).isEqualTo(20);
        assertThat(body.path("min_p").asDouble(-1)).isEqualTo(0.0);
        assertThat(body.path("repeat_penalty").asDouble(-1)).isEqualTo(1.1);
        assertThat(body.has("repetition_penalty")).isFalse();
    }

    // A setting the model refused is dropped from the request and marked withheld, so its stand-in is not sent either.
    @Test
    void serializeRequest_refusedTopK_isNotSentAsNeutralEither() throws IOException {
        final ChatRequest refused = request(null, null, null).withSampling(new SamplingParams(null, 20, null, null));
        final ChatRequest dropped = SettingDrops.stripAll(refused, EnumSet.of(RejectedCapability.TOP_K));

        final JsonNode body = sentBody(client(ServerEngine.LM_STUDIO), MODEL_ID, dropped);

        assertThat(body.has("top_k")).isFalse();
        assertThat(body.path("top_p").asDouble(-1)).isEqualTo(1.0);
    }

    private JsonNode sentBody(final ProviderClient client, final String model, final ChatRequest request)
            throws IOException {
        client.chat(model, request);
        final List<LoggedRequest> sent = server.findAll(postRequestedFor(urlEqualTo(CHAT_PATH)));
        assertThat(sent).hasSize(1);
        return json.readTree(sent.get(0).getBodyAsString());
    }

    private static ChatRequest off(final @Nullable ResponseFormat format) {
        return request(format, ReasoningLevel.OFF, null);
    }

    private static ChatRequest request(
            final @Nullable ResponseFormat format,
            final @Nullable ReasoningLevel level,
            final @Nullable OutputLengthField field) {
        return new ChatRequest(
                List.of(new ChatMessage(ChatRole.USER, "Translate: a red door.")),
                0.2,
                format,
                level,
                null,
                null,
                CAP,
                null,
                CallKind.DRAFT,
                null,
                field,
                null,
                null,
                false);
    }

    private static ResponseFormat schema() {
        return new ResponseFormat("draft", """
                {"type":"object","properties":{"t":{"type":"string"}},"required":["t"]}
                """);
    }

    private static Verdict honoured() {
        return new Verdict(Outcome.HONOURED, "test", CHECKED);
    }

    private OpenAiCompatibleClient client(final ServerEngine engine) {
        return client(engine, ProbeVerdicts.none());
    }

    private OpenAiCompatibleClient client(final ServerEngine engine, final ProbeVerdicts verdicts) {
        final ProviderConfig config = new ProviderConfig(
                        "engine-under-test",
                        ProviderKind.OPENAI_COMPATIBLE,
                        URI.create(server.baseUrl() + "/v1"),
                        Duration.ofSeconds(2),
                        Duration.ofSeconds(5))
                .withEngine(engine);
        final HttpExchange exchange = new HttpExchange(new HttpClients());
        final ObjectMapper mapper = new LlmModule().objectMapper();
        return new OpenAiCompatibleClient(
                config, exchange, mapper, System::nanoTime, new EngineFacts(exchange, mapper), model -> verdicts);
    }
}
