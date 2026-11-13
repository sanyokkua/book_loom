package ua.bookloom.llm;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
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
import com.google.inject.Guice;
import com.google.inject.Injector;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.llm.ChatMessage;
import ua.bookloom.api.llm.ChatModel;
import ua.bookloom.api.llm.ChatModelFactory;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ChatRole;
import ua.bookloom.api.llm.ModelCapabilities;
import ua.bookloom.api.llm.ModelCatalog;
import ua.bookloom.api.llm.ModelInfo;
import ua.bookloom.api.llm.ModelSelection;
import ua.bookloom.api.llm.ProviderConfig;
import ua.bookloom.api.llm.ProviderKind;
import ua.bookloom.api.llm.ProviderVerifier;
import ua.bookloom.api.llm.ReasoningLevel;
import ua.bookloom.api.llm.SourceSize;
import ua.bookloom.api.llm.VerificationPolicy;
import ua.bookloom.llm.testing.FakeProviderConfigs;
import ua.bookloom.llm.testing.LlmTestStorageModule;

/** Proves on the wire that each request follows what the model is (design D19, D20). */
class ModelProfilesAcceptanceTest {

    private static final String PROVIDER_ID = "profile-under-test";
    private static final String OLLAMA_CHAT = "/api/chat";
    private static final String OPENAI_CHAT = "/v1/chat/completions";
    private static final String SYSTEM_TEXT = "You translate prose.";
    private static final String USER_TEXT = "Translate: a red door.";
    private static final String HF_MODEL = "hf.co/unsloth/Qwen3.5-4B-GGUF:Q4_K_M";
    private static final int ANSWER_CAP = 500;
    // max(2048, 4 * 800 + 35 * 4) for a source of 800 tokens in 4 items; above the floor, so it proves the source size
    // reaches the request (a source of 300 tokens would give only the floor of 2,048).
    private static final int ALWAYS_REASONS_RESERVE = 3340;
    private static final int SOURCE_TOKENS = 300;
    private static final int RESERVE_SOURCE_TOKENS = 800;
    private static final int SOURCE_ITEMS = 4;
    private static final String OLLAMA_REPLY =
            "{\"model\":\"m\",\"message\":{\"role\":\"assistant\",\"content\":\"ok\"},\"done\":true,\"done_reason\":\"stop\"}";
    private static final String OPENAI_REPLY =
            "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"ok\"},\"finish_reason\":\"stop\"}]}";

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
    }

    @AfterEach
    void stopServer() {
        server.stop();
    }

    @Test
    void chat_qwen25SmallModelWithoutThinkingCapability_sendsNoThinkField() throws IOException {
        stubOllama("qwen2.5:1.5b", "\"capabilities\":[\"completion\",\"tools\"]");

        final JsonNode body = ollamaChat("qwen2.5:1.5b", request(ANSWER_CAP, ReasoningLevel.OFF));

        assertThat(body.has("think")).as("think sent").isFalse();
    }

    @Test
    void chat_gemma3_sendsOneUserMessageBeginningWithTheSystemText() throws IOException {
        stubOllama("gemma3:12b", "\"capabilities\":[\"completion\",\"vision\"]");

        final JsonNode body = ollamaChat("gemma3:12b", request(ANSWER_CAP, ReasoningLevel.OFF));

        assertThat(body.path("messages")).as("messages").hasSize(1);
        assertThat(body.path("messages").get(0).path("role").asText()).isEqualTo("user");
        assertThat(body.path("messages").get(0).path("content").asText()).isEqualTo(SYSTEM_TEXT + "\n\n" + USER_TEXT);
    }

    @Test
    void discover_llamaServerRouterListingWithMmproj_doesNotOfferTheSidecar() {
        server.stubFor(get(urlEqualTo("/health"))
                .willReturn(okJson("{\"status\":\"ok\"}").withHeader("Server", "llama.cpp")));
        server.stubFor(get(urlEqualTo("/props")).willReturn(okJson(fixture("llama-props-router.json"))));
        server.stubFor(get(urlEqualTo("/api/version")).willReturn(aResponse().withStatus(404)));
        server.stubFor(get(urlEqualTo("/v1/models")).willReturn(okJson(fixture("llama-router-models.json"))));
        register(openAiProvider());

        final Injector injector = Guice.createInjector(new LlmModule(Map.<String, String>of()::get), storage);
        injector.getInstance(ProviderVerifier.class)
                .verify(new ModelSelection(PROVIDER_ID, "qwen2.5-1.5b-instruct-q4_k_m.gguf"), VerificationPolicy.FULL);
        final List<ModelInfo> models = Objects.requireNonNull(
                injector.getInstance(ModelCatalog.class).listModels(PROVIDER_ID).data(), "models");

        assertThat(models).containsExactly(new ModelInfo("qwen2.5-1.5b-instruct-q4_k_m.gguf"));
    }

    @Test
    void chat_hfQwen35GgufWithoutRenderer_sendsNoThinkAndRaisesTheCapByTheReserve() throws IOException {
        stubOllama(HF_MODEL, "\"capabilities\":[\"completion\"]");

        final JsonNode body = ollamaChat(HF_MODEL, request(ANSWER_CAP, ReasoningLevel.OFF));

        assertThat(body.has("think")).as("think sent").isFalse();
        assertThat(body.path("options").path("num_predict").asInt(-1)).isEqualTo(ANSWER_CAP + ALWAYS_REASONS_RESERVE);
    }

    @Test
    void chat_llamaServerQwen38_sendsEnableThinkingJsonBooleanAndNoReasoningEffortInKwargs() throws IOException {
        server.stubFor(get(urlEqualTo("/health"))
                .willReturn(okJson("{\"status\":\"ok\"}").withHeader("Server", "llama.cpp")));
        server.stubFor(
                get(urlEqualTo("/props"))
                        .willReturn(
                                okJson(
                                        "{\"build_info\":\"b11669-620d2225c\",\"role\":\"model\",\"chat_template\":\"{% if enable_thinking %}<think>{% endif %}\"}")));
        server.stubFor(get(urlEqualTo("/api/version")).willReturn(aResponse().withStatus(404)));
        server.stubFor(get(urlEqualTo("/v1/models"))
                .willReturn(okJson("{\"object\":\"list\",\"data\":[{\"id\":\"qwen3.8-8b\",\"object\":\"model\"}]}")));
        server.stubFor(post(urlEqualTo(OPENAI_CHAT)).willReturn(okJson(OPENAI_REPLY)));
        register(openAiProvider());
        final Injector injector = Guice.createInjector(new LlmModule(Map.<String, String>of()::get), storage);
        injector.getInstance(ProviderVerifier.class)
                .verify(new ModelSelection(PROVIDER_ID, "qwen3.8-8b"), VerificationPolicy.FULL);
        final ChatModel model = chatModel(injector, "qwen3.8-8b");
        server.resetRequests();

        assertThat(model.chat(request(ANSWER_CAP, ReasoningLevel.OFF)).isOk()).isTrue();
        final List<LoggedRequest> chats = server.findAll(postRequestedFor(urlEqualTo(OPENAI_CHAT)));
        assertThat(chats).hasSize(1);
        final JsonNode kwargs = json.readTree(chats.get(0).getBodyAsString()).path("chat_template_kwargs");

        assertThat(kwargs.path("enable_thinking").isBoolean())
                .as("enable_thinking is a JSON boolean")
                .isTrue();
        assertThat(kwargs.has("reasoning_effort"))
                .as("reasoning_effort in kwargs")
                .isFalse();
    }

    private void stubOllama(final String modelId, final String capabilities) {
        server.stubFor(post(urlEqualTo("/api/show"))
                .willReturn(okJson("{\"details\":{\"family\":\"x\"}," + capabilities + "}")));
        server.stubFor(
                get(urlEqualTo("/api/tags")).willReturn(okJson("{\"models\":[{\"name\":\"" + modelId + "\"}]}")));
        server.stubFor(post(urlEqualTo(OLLAMA_CHAT)).willReturn(okJson(OLLAMA_REPLY)));
        register(new ProviderConfig(
                PROVIDER_ID,
                ProviderKind.OLLAMA,
                URI.create(server.baseUrl()),
                Duration.ofSeconds(2),
                Duration.ofSeconds(5)));
    }

    // The person chose the model first (Settings › Models), which is when its server's facts are read.
    private JsonNode ollamaChat(final String modelId, final ChatRequest request) throws IOException {
        final Injector injector = Guice.createInjector(new LlmModule(Map.<String, String>of()::get), storage);
        assertThat(injector.getInstance(ModelCapabilities.class)
                        .report(new ModelSelection(PROVIDER_ID, modelId))
                        .isOk())
                .as("model reported")
                .isTrue();
        assertThat(chatModel(injector, modelId).chat(request).isOk())
                .as("chat answered")
                .isTrue();
        final List<LoggedRequest> chats = server.findAll(postRequestedFor(urlEqualTo(OLLAMA_CHAT)));
        assertThat(chats).as("chat requests received").hasSize(1);
        return json.readTree(chats.get(0).getBodyAsString());
    }

    private static ChatModel chatModel(final Injector injector, final String modelId) {
        return Objects.requireNonNull(
                injector.getInstance(ChatModelFactory.class)
                        .create(new ModelSelection(PROVIDER_ID, modelId))
                        .data(),
                "chat model");
    }

    private ProviderConfig openAiProvider() {
        return new ProviderConfig(
                PROVIDER_ID,
                ProviderKind.OPENAI_COMPATIBLE,
                URI.create(server.baseUrl() + "/v1"),
                Duration.ofSeconds(2),
                Duration.ofSeconds(5));
    }

    private void register(final ProviderConfig config) {
        assertThat(configs.save(config).isOk()).isTrue();
    }

    private static ChatRequest request(final int cap, final ReasoningLevel reasoning) {
        return new ChatRequest(
                        List.of(
                                new ChatMessage(ChatRole.SYSTEM, SYSTEM_TEXT),
                                new ChatMessage(ChatRole.USER, USER_TEXT)),
                        null,
                        null,
                        reasoning,
                        null,
                        SOURCE_TOKENS,
                        cap,
                        null,
                        null)
                .withSource(new SourceSize(RESERVE_SOURCE_TOKENS, SOURCE_ITEMS));
    }

    private static String fixture(final String name) {
        try (InputStream in =
                ModelProfilesAcceptanceTest.class.getResourceAsStream("/ua/bookloom/llm/engine/" + name)) {
            return new String(Objects.requireNonNull(in, name).readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }
}
