package ua.bookloom.llm;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import com.google.inject.Guice;
import com.google.inject.Injector;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ua.bookloom.api.llm.ChatMessage;
import ua.bookloom.api.llm.ChatModel;
import ua.bookloom.api.llm.ChatModelFactory;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ChatRole;
import ua.bookloom.api.llm.ModelCapabilities;
import ua.bookloom.api.llm.ModelSelection;
import ua.bookloom.api.llm.ModelSettings;
import ua.bookloom.api.llm.ProviderConfig;
import ua.bookloom.api.llm.ProviderKind;
import ua.bookloom.api.llm.ReasoningLevel;
import ua.bookloom.api.llm.SamplingOverride;
import ua.bookloom.api.llm.ServerEngine;
import ua.bookloom.api.llm.SourceSize;
import ua.bookloom.api.llm.ThinkingOverride;
import ua.bookloom.api.pipeline.CallKind;
import ua.bookloom.llm.testing.FakeProviderConfigs;
import ua.bookloom.llm.testing.LlmTestStorageModule;

/**
 * Proves on the wire, for both dialects, that each call's reasoning is decided from the model's profile and the person's
 * thinking choice, and is sent in each engine's own fields (design D20): no control a model does not declare, the off
 * value it names, its lowest level when it always reasons, and room for the reasoning on the cap.
 */
class ReasoningPerCallKindTest {

    private static final String PROVIDER_ID = "reasoning-under-test";
    private static final String OLLAMA_CHAT = "/api/chat";
    private static final String OPENAI_CHAT = "/v1/chat/completions";
    private static final String LM_STUDIO_CHAT = "/api/v0/chat/completions";
    private static final int CAP = 500;
    // The least room a call that thinks is given.
    private static final int FLOOR_RESERVE = 2048;
    private static final String OLLAMA_REPLY =
            "{\"model\":\"m\",\"message\":{\"role\":\"assistant\",\"content\":\"ok\"},\"done\":true,\"done_reason\":\"stop\"}";
    private static final String OPENAI_REPLY =
            "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"ok\"},\"finish_reason\":\"stop\"}]}";
    private static final String THINKING_SHOW = "\"capabilities\":[\"completion\",\"thinking\"],\"renderer\":\"r\"";

    private final WireMockServer server =
            new WireMockServer(WireMockConfiguration.options().dynamicPort().bindAddress("127.0.0.1"));
    private final ObjectMapper json = new ObjectMapper();
    private FakeProviderConfigs configs;
    private LlmTestStorageModule storage;

    @BeforeEach
    void startServer() {
        server.start();
        configs = new FakeProviderConfigs();
        storage = new LlmTestStorageModule(configs);
        server.stubFor(post(urlEqualTo(OLLAMA_CHAT)).willReturn(okJson(OLLAMA_REPLY)));
        server.stubFor(post(urlEqualTo(OPENAI_CHAT)).willReturn(okJson(OPENAI_REPLY)));
        server.stubFor(post(urlEqualTo(LM_STUDIO_CHAT)).willReturn(okJson(OPENAI_REPLY)));
    }

    @AfterEach
    void stopServer() {
        server.stop();
    }

    // A model whose server lists no thinking capability is sent no control on either of Ollama's paths.
    @ParameterizedTest
    @CsvSource({"OLLAMA, /api/chat, think", "OPENAI_COMPATIBLE, /v1/chat/completions, reasoning_effort"})
    void chat_qwen25NoThinkingCapability_sendsNoThinkField(
            final ProviderKind kind, final String chatPath, final String field) {
        showOllama("\"capabilities\":[\"completion\",\"tools\"]");
        register(ollamaServer(kind));

        final JsonNode body = chat("qwen2.5:1.5b", request(CallKind.DRAFT, null), chatPath);

        assertThat(body.has(field)).as(field + " sent").isFalse();
        assertThat(cap(body)).isEqualTo(CAP);
    }

    // A downloaded Qwen 3.5 GGUF that Ollama serves with no renderer and no thinking capability thinks on every call:
    // no control, and the cap grows by the reserve for its source, max(2048, 4 * 800 + 35 * 4) = 3,340.
    @ParameterizedTest
    @CsvSource({"OLLAMA, /api/chat, think", "OPENAI_COMPATIBLE, /v1/chat/completions, reasoning_effort"})
    void chat_hfQwen35WithoutRenderer_sendsNoThinkAndTheReserve(
            final ProviderKind kind, final String chatPath, final String field) {
        showOllama("\"capabilities\":[\"completion\"]");
        register(ollamaServer(kind));

        final JsonNode body =
                chat("hf.co/unsloth/Qwen3.5-4B-GGUF:Q4_K_M", request(CallKind.DRAFT, new SourceSize(800, 4)), chatPath);

        assertThat(body.has(field)).as(field + " sent").isFalse();
        assertThat(cap(body)).isEqualTo(CAP + 3340);
    }

    // LM Studio loaded at 8,192 with an answer cap of half the window: the reserve of 3,340 must not push the output
    // past
    // what the window leaves after a prompt of about 1,500 tokens or more (4,500 characters), and some room stays for
    // it.
    @ParameterizedTest
    @CsvSource({"OLLAMA, /api/chat", "OPENAI_COMPATIBLE, /v1/chat/completions"})
    void chat_unswitchableModelInAnEightKWindow_capAndReserveFitBesideThePrompt(
            final ProviderKind kind, final String chatPath) {
        showOllama("\"capabilities\":[\"completion\"]");
        register(ollamaServer(kind));
        final ChatRequest request = new ChatRequest(
                        List.of(
                                new ChatMessage(ChatRole.SYSTEM, "You translate prose."),
                                new ChatMessage(ChatRole.USER, "a".repeat(4500))),
                        null,
                        null,
                        ReasoningLevel.OFF,
                        8192,
                        null,
                        4096,
                        null,
                        CallKind.DRAFT)
                .withSource(new SourceSize(800, 4));

        final JsonNode body = chat("hf.co/unsloth/Qwen3.5-4B-GGUF:Q4_K_M", request, chatPath);

        assertThat(cap(body)).isLessThanOrEqualTo(8192 - 1500).isGreaterThan(4096);
    }

    // llama-server whose template reads enable_thinking gets it as a JSON boolean in the kwargs, never an effort there.
    @Test
    void chat_llamaServerQwen38Off_enableThinkingBooleanNoEffortInKwargs() {
        server.stubFor(get(urlEqualTo("/props"))
                .willReturn(okJson(
                        "{\"role\":\"model\",\"chat_template\":\"{% if enable_thinking %}<think>{% endif %}\"}")));
        register(openAi("/v1", ServerEngine.LLAMA_CPP));

        final JsonNode body = chat("qwen3.8-8b", request(CallKind.DRAFT, null), OPENAI_CHAT);

        final JsonNode kwargs = body.path("chat_template_kwargs");
        assertThat(kwargs.path("enable_thinking").isBoolean())
                .as("a JSON boolean")
                .isTrue();
        assertThat(kwargs.path("enable_thinking").asBoolean()).isFalse();
        assertThat(kwargs.has("reasoning_effort"))
                .as("reasoning_effort in kwargs")
                .isFalse();
        assertThat(body.has("reasoning_effort")).isFalse();
    }

    // gpt-oss always reasons; its lowest level is sent and its cap gets the reserve.
    @Test
    void chat_gptOssOllamaNative_sendsLow() {
        showOllama(
                "\"capabilities\":[\"completion\",\"thinking\"],\"thinking\":{\"values\":[\"low\",\"medium\",\"high\"]}");
        register(ollamaServer(ProviderKind.OLLAMA));

        final JsonNode body = chat("gpt-oss:20b", request(CallKind.DRAFT, null), OLLAMA_CHAT);

        assertThat(body.path("think").asText()).isEqualTo("low");
        assertThat(cap(body)).isEqualTo(CAP + FLOOR_RESERVE);
    }

    // A reasoning-only model whose LM Studio options are ["on"] has no switch: nothing is sent, the cap grows.
    @Test
    void chat_ministralReasoningLmStudio_sendsNoControlAndRaisesTheCap() {
        final String model = "mistralai/ministral-3-14b-reasoning";
        server.stubFor(
                get(urlPathMatching("/api/v0/models/.*")).willReturn(aResponse().withStatus(404)));
        server.stubFor(get(urlEqualTo("/api/v1/models"))
                .willReturn(okJson("{\"models\":[{\"key\":\"" + model
                        + "\",\"capabilities\":{\"reasoning\":{\"allowed_options\":[\"on\"],\"default\":\"on\"}}}]}")));
        register(openAi("/v1", ServerEngine.LM_STUDIO));

        final JsonNode body = chat(model, request(CallKind.DRAFT, null), LM_STUDIO_CHAT);

        assertThat(body.has("reasoning_effort")).as("reasoning_effort sent").isFalse();
        assertThat(cap(body)).isEqualTo(CAP + FLOOR_RESERVE);
    }

    @Test
    void chat_gemma4NativeOff_sendsThinkFalse() {
        showOllama(THINKING_SHOW);
        register(ollamaServer(ProviderKind.OLLAMA));

        final JsonNode body = chat("gemma4:e4b-mlx", request(CallKind.DRAFT, null), OLLAMA_CHAT);

        assertThat(body.path("think").isBoolean()).isTrue();
        assertThat(body.path("think").asBoolean()).isFalse();
        assertThat(cap(body)).isEqualTo(CAP);
    }

    @Test
    void chat_ollamaOpenAiOff_sendsEffortNone() {
        showOllama(THINKING_SHOW);
        register(ollamaServer(ProviderKind.OPENAI_COMPATIBLE));

        final JsonNode body = chat("qwen3.5:9b", request(CallKind.DRAFT, null), OPENAI_CHAT);

        assertThat(body.path("reasoning_effort").asText()).isEqualTo("none");
        assertThat(cap(body)).isEqualTo(CAP);
    }

    // "Always" turns thinking on for every call kind, each with the reserve on its cap.
    @ParameterizedTest
    @CsvSource({
        "OLLAMA, gemma4:e4b-mlx, /api/chat, think, true",
        "OPENAI_COMPATIBLE, google/gemma-4-e4b, /api/v0/chat/completions, reasoning_effort, medium"
    })
    void chat_thinkingOverrideAlways_sendsOnForEveryKind(
            final ProviderKind kind, final String model, final String chatPath, final String field, final String on) {
        showOllama(THINKING_SHOW);
        register(kind == ProviderKind.OLLAMA ? ollamaServer(kind) : openAi("/v1", ServerEngine.LM_STUDIO));
        storeThinking(model, ThinkingOverride.ALWAYS);

        final List<JsonNode> bodies = chats(
                model,
                chatPath,
                CallKind.DRAFT,
                CallKind.STRUCTURAL_REPAIR,
                CallKind.PROOFREAD,
                CallKind.TERM_READING,
                CallKind.BRIEF_SYNTHESIS);

        assertThat(bodies).hasSize(5).allSatisfy(body -> {
            assertThat(body.path(field).asText()).isEqualTo(on);
            assertThat(cap(body)).isEqualTo(CAP + FLOOR_RESERVE);
        });
    }

    // "Allow on proofreading and names" thinks for the proofread (the reviewer today) and the names pass only.
    @ParameterizedTest
    @CsvSource({
        "OLLAMA, gemma4:e4b-mlx, /api/chat, think, true, false",
        "OPENAI_COMPATIBLE, google/gemma-4-e4b, /api/v0/chat/completions, reasoning_effort, medium, none"
    })
    void chat_allowProofreadAndNames_onForProofreadOnly(
            final ProviderKind kind,
            final String model,
            final String chatPath,
            final String field,
            final String on,
            final String off) {
        showOllama(THINKING_SHOW);
        register(kind == ProviderKind.OLLAMA ? ollamaServer(kind) : openAi("/v1", ServerEngine.LM_STUDIO));
        storeThinking(model, ThinkingOverride.ALLOW_PROOFREAD_AND_NAMES);

        final List<JsonNode> bodies = chats(
                model, chatPath, CallKind.PROOFREAD, CallKind.TERM_READING, CallKind.DRAFT, CallKind.BRIEF_SYNTHESIS);

        assertThat(bodies).extracting(body -> body.path(field).asText()).containsExactly(on, on, off, off);
        assertThat(bodies)
                .extracting(ReasoningPerCallKindTest::cap)
                .containsExactly(CAP + FLOOR_RESERVE, CAP + FLOOR_RESERVE, CAP, CAP);
    }

    private void showOllama(final String fields) {
        server.stubFor(
                post(urlEqualTo("/api/show")).willReturn(okJson("{\"details\":{\"family\":\"x\"}," + fields + "}")));
    }

    private ProviderConfig ollamaServer(final ProviderKind kind) {
        return kind == ProviderKind.OLLAMA
                ? new ProviderConfig(
                        PROVIDER_ID,
                        ProviderKind.OLLAMA,
                        URI.create(server.baseUrl()),
                        Duration.ofSeconds(2),
                        Duration.ofSeconds(5))
                : openAi("/v1", ServerEngine.OLLAMA);
    }

    private ProviderConfig openAi(final String path, final ServerEngine engine) {
        return new ProviderConfig(
                        PROVIDER_ID,
                        ProviderKind.OPENAI_COMPATIBLE,
                        URI.create(server.baseUrl() + path),
                        Duration.ofSeconds(2),
                        Duration.ofSeconds(5))
                .withEngine(engine);
    }

    private void register(final ProviderConfig config) {
        assertThat(configs.save(config).isOk()).isTrue();
    }

    private void storeThinking(final String model, final ThinkingOverride thinking) {
        assertThat(storage.modelSettings()
                        .save(ModelSettings.defaults(PROVIDER_ID, model)
                                .withOverrides(thinking, SamplingOverride.profileDefault()))
                        .isOk())
                .isTrue();
    }

    private JsonNode chat(final String model, final ChatRequest request, final String chatPath) {
        final ChatModel chatModel = selected(model);
        assertThat(chatModel.chat(request).isOk()).as("chat answered").isTrue();
        final List<LoggedRequest> sent = server.findAll(postRequestedFor(urlEqualTo(chatPath)));
        assertThat(sent).as("chat requests received").hasSize(1);
        return read(sent.get(0));
    }

    private List<JsonNode> chats(final String model, final String chatPath, final CallKind... kinds) {
        final ChatModel chatModel = selected(model);
        for (final CallKind kind : kinds) {
            assertThat(chatModel.chat(request(kind, null)).isOk())
                    .as("chat answered")
                    .isTrue();
        }
        return server.findAll(postRequestedFor(urlEqualTo(chatPath))).stream()
                .map(this::read)
                .toList();
    }

    // The person chose the model (Settings › Models), which is when its server's facts are read.
    private ChatModel selected(final String model) {
        final Injector injector = Guice.createInjector(new LlmModule(Map.<String, String>of()::get), storage);
        assertThat(injector.getInstance(ModelCapabilities.class)
                        .report(new ModelSelection(PROVIDER_ID, model))
                        .isOk())
                .isTrue();
        return Objects.requireNonNull(
                injector.getInstance(ChatModelFactory.class)
                        .create(new ModelSelection(PROVIDER_ID, model))
                        .data(),
                "chat model");
    }

    private JsonNode read(final LoggedRequest request) {
        try {
            return json.readTree(request.getBodyAsString());
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    private static int cap(final JsonNode body) {
        return body.has("options")
                ? body.path("options").path("num_predict").asInt(-1)
                : body.path("max_tokens").asInt(-1);
    }

    // Every call BookLoom makes asks for thinking off; the profile and the person's choice decide what is sent.
    private static ChatRequest request(final CallKind kind, final @Nullable SourceSize source) {
        return new ChatRequest(
                        List.of(
                                new ChatMessage(ChatRole.SYSTEM, "You translate prose."),
                                new ChatMessage(ChatRole.USER, "Translate: a red door.")),
                        null,
                        null,
                        ReasoningLevel.OFF,
                        null,
                        null,
                        CAP,
                        null,
                        kind)
                .withSource(source);
    }
}
