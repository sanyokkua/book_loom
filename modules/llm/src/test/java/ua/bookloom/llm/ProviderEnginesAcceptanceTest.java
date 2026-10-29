package ua.bookloom.llm;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
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
import java.net.URI;
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
import ua.bookloom.api.llm.ModelSelection;
import ua.bookloom.api.llm.ModelSettings;
import ua.bookloom.api.llm.OutputLengthField;
import ua.bookloom.api.llm.ProviderAuth;
import ua.bookloom.api.llm.ProviderConfig;
import ua.bookloom.api.llm.ProviderKind;
import ua.bookloom.api.llm.ProviderVerifier;
import ua.bookloom.api.llm.ReasoningLevel;
import ua.bookloom.api.llm.VerificationPolicy;
import ua.bookloom.llm.testing.FakeProviderConfigs;
import ua.bookloom.llm.testing.LlmTestStorageModule;

/** Proves on the wire that each server engine gets the request fields its own server reads (design D17). */
class ProviderEnginesAcceptanceTest {

    private static final String PROVIDER_ID = "engine-under-test";
    private static final String PROBE_MODEL = "probe-model";
    private static final String CHAT_PATH = "/v1/chat/completions";
    private static final String CHAT_REPLY =
            "{\"model\":\"m\",\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"ok\"},\"finish_reason\":\"stop\"}]}";
    private static final String UNSLOTH_KEY_VARIABLE = "UNSLOTH_API_KEY";
    private static final String UNSLOTH_KEY = "sk-test-0000";
    private static final int OUTPUT_CAP = 512;

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
        server.stubFor(post(urlEqualTo(CHAT_PATH)).willReturn(okJson(CHAT_REPLY)));
        server.stubFor(get(urlEqualTo("/v1/models"))
                .willReturn(okJson("{\"object\":\"list\",\"data\":[{\"id\":\"probe-model\",\"object\":\"model\"}]}")));
    }

    @AfterEach
    void stopServer() {
        server.stop();
    }

    @Test
    void draft_llamaServerWhoseTemplateReadsEnableThinking_sendsKwargFalse() throws IOException {
        stubLlamaServer();
        register(plainProvider());

        final JsonNode body = testThenDraft("qwen3.5-4b", Map.of());

        assertThat(body.path("chat_template_kwargs").path("enable_thinking").isBoolean())
                .as("enable_thinking is a JSON boolean")
                .isTrue();
        assertThat(body.path("chat_template_kwargs").path("enable_thinking").asBoolean(true))
                .as("enable_thinking value")
                .isFalse();
        assertThat(body.has("reasoning_effort")).as("reasoning_effort sent").isFalse();
    }

    @Test
    void draft_llamaServerGptOssId_sendsReasoningEffortLowAndNoKwarg() throws IOException {
        stubLlamaServer();
        register(plainProvider());

        final JsonNode body = testThenDraft("gpt-oss-20b", Map.of());

        assertThat(body.path("reasoning_effort").asText())
                .as("reasoning_effort")
                .isEqualTo("low");
        assertThat(body.has("chat_template_kwargs"))
                .as("chat_template_kwargs sent")
                .isFalse();
    }

    @Test
    void draft_ollamaBehindOpenAiKindWithStoredCompletionField_sendsMaxTokens() throws IOException {
        server.stubFor(get(urlEqualTo("/api/version")).willReturn(okJson("{\"version\":\"0.40.11\"}")));
        server.stubFor(get(urlEqualTo("/api/tags"))
                .willReturn(okJson("{\"models\":[{\"name\":\"gemma4:e4b\",\"details\":{\"family\":\"gemma4\"}}]}")));
        server.stubFor(
                get(urlEqualTo("/v1/models"))
                        .willReturn(
                                okJson(
                                        "{\"object\":\"list\",\"data\":[{\"id\":\"gemma4:e4b\",\"object\":\"model\",\"owned_by\":\"library\"}]}")));
        register(plainProvider());
        storage.modelSettings()
                .save(ModelSettings.defaults(PROVIDER_ID, "gemma4:e4b")
                        .withChoices(
                                ModelSettings.defaults(PROVIDER_ID, "gemma4:e4b")
                                        .temperatureChoice(),
                                null,
                                null,
                                null,
                                OutputLengthField.MAX_COMPLETION_TOKENS));

        final JsonNode body = testThenDraft("gemma4:e4b", Map.of());

        assertThat(body.path("max_tokens").asInt(-1)).as("max_tokens").isEqualTo(OUTPUT_CAP);
        assertThat(body.has("max_completion_tokens"))
                .as("max_completion_tokens sent")
                .isFalse();
    }

    @Test
    void detect_koboldCppLookAlike_isNotTreatedAsOllama() throws IOException {
        server.stubFor(get(urlEqualTo("/api/version")).willReturn(okJson("{\"version\":\"1.2.3\"}")));
        server.stubFor(get(urlEqualTo("/api/tags"))
                .willReturn(
                        okJson("{\"models\":[{\"name\":\"koboldcpp/x\",\"details\":{\"family\":\"koboldcpp\"}}]}")));
        register(plainProvider());

        final JsonNode body = testThenDraft("koboldcpp/x", Map.of());

        assertThat(body.has("think")).as("think sent").isFalse();
        assertThat(body.has("reasoning_effort")).as("reasoning_effort sent").isFalse();
        assertThat(body.has("chat_template_kwargs"))
                .as("chat_template_kwargs sent")
                .isFalse();
    }

    @Test
    void draft_unslothServer_sendsEnableToolsFalseAndBearerKey() throws IOException {
        server.stubFor(get(urlEqualTo("/api/health"))
                .willReturn(okJson("{\"status\":\"healthy\",\"service\":\"Unsloth UI Backend\"}")));
        register(unslothProvider());

        final LoggedRequest request = testThenDraftRequest("unsloth-model", Map.of(UNSLOTH_KEY_VARIABLE, UNSLOTH_KEY));

        assertThat(request.getHeader("Authorization")).as("Authorization").isEqualTo("Bearer " + UNSLOTH_KEY);
        assertThat(json.readTree(request.getBodyAsString()).path("enable_tools").isBoolean())
                .as("enable_tools is a JSON boolean")
                .isTrue();
        assertThat(json.readTree(request.getBodyAsString()).path("enable_tools").asBoolean(true))
                .as("enable_tools value")
                .isFalse();
    }

    private void stubLlamaServer() {
        server.stubFor(get(urlEqualTo("/health"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Server", "llama.cpp")
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"status\":\"ok\"}")));
        server.stubFor(
                get(urlEqualTo("/props"))
                        .willReturn(
                                okJson(
                                        "{\"build_info\":\"b11669-620d2225c\",\"role\":\"model\",\"chat_template\":\"{% if enable_thinking %}<think>{% endif %}\"}")));
        server.stubFor(get(urlEqualTo("/api/version")).willReturn(aResponse().withStatus(404)));
    }

    private ProviderConfig plainProvider() {
        return new ProviderConfig(
                PROVIDER_ID,
                ProviderKind.OPENAI_COMPATIBLE,
                URI.create(server.baseUrl() + "/v1"),
                Duration.ofSeconds(2),
                Duration.ofSeconds(5));
    }

    private ProviderConfig unslothProvider() {
        return new ProviderConfig(
                PROVIDER_ID,
                ProviderKind.OPENAI_COMPATIBLE,
                URI.create(server.baseUrl() + "/v1"),
                Duration.ofSeconds(2),
                Duration.ofSeconds(5),
                null,
                ProviderAuth.BEARER,
                null,
                UNSLOTH_KEY_VARIABLE,
                null,
                null,
                Map.of(),
                List.of());
    }

    private void register(final ProviderConfig config) {
        assertThat(configs.save(config).isOk()).isTrue();
    }

    private JsonNode testThenDraft(final String modelId, final Map<String, String> environment) throws IOException {
        return json.readTree(testThenDraftRequest(modelId, environment).getBodyAsString());
    }

    private LoggedRequest testThenDraftRequest(final String modelId, final Map<String, String> environment) {
        final Injector injector = Guice.createInjector(new LlmModule(environment::get), storage);
        injector.getInstance(ProviderVerifier.class)
                .verify(new ModelSelection(PROVIDER_ID, PROBE_MODEL), VerificationPolicy.FULL);
        final ChatModel model = Objects.requireNonNull(
                injector.getInstance(ChatModelFactory.class)
                        .create(new ModelSelection(PROVIDER_ID, modelId))
                        .data(),
                "chat model");
        server.resetRequests();
        assertThat(model.chat(draft()).isOk()).as("draft call answered").isTrue();
        final List<LoggedRequest> chats = server.findAll(
                postRequestedFor(urlEqualTo(CHAT_PATH)).withRequestBody(matchingJsonPath("$.model", equalTo(modelId))));
        assertThat(chats).as("draft requests received").hasSize(1);
        return chats.get(0);
    }

    private static ChatRequest draft() {
        return new ChatRequest(
                List.of(new ChatMessage(ChatRole.USER, "Translate: a red door.")),
                null,
                null,
                ReasoningLevel.OFF,
                null,
                null,
                OUTPUT_CAP,
                null,
                null);
    }
}
