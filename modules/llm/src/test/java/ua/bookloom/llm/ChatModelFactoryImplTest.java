package ua.bookloom.llm;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.google.inject.Guice;
import com.google.inject.Injector;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ChatMessage;
import ua.bookloom.api.llm.ChatModel;
import ua.bookloom.api.llm.ChatModelFactory;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.ChatRole;
import ua.bookloom.api.llm.FinishReason;
import ua.bookloom.api.llm.ModelSelection;
import ua.bookloom.api.llm.ProviderConfig;
import ua.bookloom.api.llm.ProviderConfigs;
import ua.bookloom.api.llm.ProviderKind;
import ua.bookloom.llm.gate.InferenceGate;
import ua.bookloom.llm.http.HttpClients;
import ua.bookloom.llm.http.HttpExchange;
import ua.bookloom.llm.provider.ProviderClientFactory;
import ua.bookloom.llm.retry.RetryPolicy;

/** Verifies provider-neutral model resolution while preserving the pseudo model's behavior. */
class ChatModelFactoryImplTest {

    private static final String OLLAMA_REPLY =
            "{\"model\":\"gemma4:e4b-mlx\",\"message\":{\"content\":\"hello\"},\"done_reason\":\"stop\"}";
    private static final String OPENAI_REPLY =
            "{\"model\":\"google/gemma-4-e4b\",\"choices\":[{\"message\":{\"content\":\"hello\"},\"finish_reason\":\"stop\"}]}";
    private final WireMockServer server =
            new WireMockServer(WireMockConfiguration.options().dynamicPort());
    private ProviderConfigs configs;

    @BeforeEach
    void startServer() {
        server.start();
        configs = new InMemoryProviderConfigs();
    }

    @AfterEach
    void stopServer() {
        server.stop();
    }

    // The pseudo provider remains offline and deterministic after real clients are registered.
    @Test
    void create_pseudoUppercase_returnsUppercaseStopResponse() {
        final ChatModel model = pseudoModel();

        final Result<ChatResponse> result =
                model.chat(new ChatRequest(List.of(new ChatMessage(ChatRole.USER, "hello"))));

        assertThat(responseOf(result).content()).isEqualTo("HELLO");
        assertThat(responseOf(result).finishReason()).isEqualTo(FinishReason.STOP);
        assertThat(server.getAllServeEvents()).isEmpty();
    }

    // A provider id absent from the in-memory registry is refused as typed validation.
    @Test
    void create_unknownProvider_returnsValidationError() {
        final Result<ChatModel> result = factory().create(new ModelSelection("gemini", "gemini-2.5-flash"));

        assertThat(result.data()).isNull();
        assertThat(errorOf(result).code()).isEqualTo(ErrorCode.validation);
    }

    // An empty model id cannot bind a real client even when its provider is registered.
    @Test
    void create_blankModelId_returnsValidationError() {
        final Result<ChatModel> result = factory().create(new ModelSelection("ollama", " "));

        assertThat(result.data()).isNull();
        assertThat(errorOf(result).code()).isEqualTo(ErrorCode.validation);
        assertThat(server.getAllServeEvents()).isEmpty();
    }

    // An empty model id is refused rather than treated as a provider default.
    @Test
    void create_emptyModelId_returnsValidationError() {
        final Result<ChatModel> result = factory().create(new ModelSelection("pseudo", ""));

        assertThat(result.data()).isNull();
        assertThat(errorOf(result).code()).isEqualTo(ErrorCode.validation);
    }

    // Protected placeholders and entity references remain unchanged by the existing pseudo implementation.
    @Test
    void chat_placeholderAndEntityReferences_preservesReferencesWhileUppercasingText() {
        final Result<ChatResponse> result = pseudoModel()
                .chat(new ChatRequest(
                        List.of(new ChatMessage(ChatRole.USER, "Tom ⟦g0⟧ran⟦g1⟧ &amp; hid&nbsp;&#160;&#xA0;⟦g12⟧"))));

        assertThat(responseOf(result).content()).isEqualTo("TOM ⟦g0⟧RAN⟦g1⟧ &amp; HID&nbsp;&#160;&#xA0;⟦g12⟧");
        assertThat(responseOf(result).finishReason()).isEqualTo(FinishReason.STOP);
    }

    // A draft prompt must yield the segment source, not the catalog instructions surrounding it.
    @Test
    void chat_draftPromptUppercasesEscapedSource_returnsOnlyTranslatedSegment() {
        final String prompt = """
                <Text>
                He said "hi" to ⟦g0⟧old⟦g1⟧.
                </Text>
                """;

        final Result<ChatResponse> result =
                pseudoModel().chat(new ChatRequest(List.of(new ChatMessage(ChatRole.USER, prompt))));

        assertThat(responseOf(result).content()).isEqualTo("HE SAID \"HI\" TO ⟦g0⟧OLD⟦g1⟧.");
        assertThat(responseOf(result).finishReason()).isEqualTo(FinishReason.STOP);
    }

    // Locale.ROOT makes uppercase conversion stable when the process default locale is Turkish.
    @ResourceLock(Resources.LOCALE)
    @Test
    void chat_turkishDefaultLocale_usesRootUppercase() {
        final Locale originalLocale = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr"));

            final Result<ChatResponse> result =
                    pseudoModel().chat(new ChatRequest(List.of(new ChatMessage(ChatRole.USER, "title"))));

            assertThat(responseOf(result).content()).isEqualTo("TITLE");
        } finally {
            Locale.setDefault(originalLocale);
        }
    }

    // The pseudo model answers the last user turn, independent of earlier system and assistant messages.
    @Test
    void chat_multipleUserMessages_answersLastUserMessage() {
        final Result<ChatResponse> result = pseudoModel()
                .chat(new ChatRequest(List.of(
                        new ChatMessage(ChatRole.SYSTEM, "Translate into uk"),
                        new ChatMessage(ChatRole.USER, "one"),
                        new ChatMessage(ChatRole.ASSISTANT, "ONE"),
                        new ChatMessage(ChatRole.USER, "two"))));

        assertThat(responseOf(result).content()).isEqualTo("TWO");
    }

    // An empty final user turn is still a successful normal completion.
    @Test
    void chat_emptyLastUserMessage_returnsEmptyStopResponse() {
        final Result<ChatResponse> result =
                pseudoModel().chat(new ChatRequest(List.of(new ChatMessage(ChatRole.USER, ""))));

        assertThat(responseOf(result).content()).isEmpty();
        assertThat(responseOf(result).finishReason()).isEqualTo(FinishReason.STOP);
    }

    // A conversation without a user turn has the same empty normal completion as an empty user turn.
    @Test
    void chat_withoutUserMessage_returnsEmptyStopResponse() {
        final Result<ChatResponse> result =
                pseudoModel().chat(new ChatRequest(List.of(new ChatMessage(ChatRole.SYSTEM, "Translate into uk"))));

        assertThat(responseOf(result).content()).isEmpty();
        assertThat(responseOf(result).finishReason()).isEqualTo(FinishReason.STOP);
    }

    // A registered Ollama model is lazy and its first call uses the native /api/chat route and bound model id.
    @Test
    void create_registeredOllamaModel_isLazyAndPostsBoundModelToNativeRoute() {
        server.stubFor(post(urlEqualTo("/api/chat"))
                .willReturn(aResponse().withStatus(200).withBody(OLLAMA_REPLY)));
        register(new ProviderConfig(
                "ollama",
                ProviderKind.OLLAMA,
                URI.create(server.baseUrl()),
                ProviderConfig.DEFAULT_CONNECT_TIMEOUT,
                ProviderConfig.DEFAULT_REQUEST_TIMEOUT));

        final ChatModel model = Objects.requireNonNull(
                factory().create(new ModelSelection("ollama", "gemma4:e4b-mlx")).data(), "model");

        assertThat(server.getAllServeEvents()).isEmpty();
        assertThat(model.chat(chatRequest()).data()).isNotNull();
        server.verify(postRequestedFor(urlEqualTo("/api/chat"))
                .withRequestBody(matchingJsonPath("$.model", equalTo("gemma4:e4b-mlx"))));
    }

    // A registered OpenAI-compatible model uses the /v1 route and its selected model id.
    @Test
    void create_registeredOpenAiModel_postsBoundModelToCompatibleRoute() {
        server.stubFor(post(urlEqualTo("/v1/chat/completions"))
                .willReturn(aResponse().withStatus(200).withBody(OPENAI_REPLY)));
        register(new ProviderConfig(
                "lmstudio",
                ProviderKind.OPENAI_COMPATIBLE,
                URI.create(server.baseUrl() + "/v1"),
                ProviderConfig.DEFAULT_CONNECT_TIMEOUT,
                ProviderConfig.DEFAULT_REQUEST_TIMEOUT));

        final ChatModel model = Objects.requireNonNull(
                factory()
                        .create(new ModelSelection("lmstudio", "google/gemma-4-e4b"))
                        .data(),
                "model");

        assertThat(server.getAllServeEvents()).isEmpty();
        assertThat(model.chat(chatRequest()).data()).isNotNull();
        server.verify(postRequestedFor(urlEqualTo("/v1/chat/completions"))
                .withRequestBody(matchingJsonPath("$.model", equalTo("google/gemma-4-e4b"))));
    }

    // Guice shares the registry, client factory, gate, and retry policy across all model resolutions.
    @Test
    void configure_llmModuleResolvesSharedServicesAndChatModelFactory() {
        final Injector injector = Guice.createInjector(new LlmModule());

        assertThat(injector.getInstance(ChatModelFactory.class)).isInstanceOf(ChatModelFactoryImpl.class);
        assertThat(injector.getInstance(ProviderConfigs.class)).isSameAs(injector.getInstance(ProviderConfigs.class));
        assertThat(injector.getInstance(InferenceGate.class)).isSameAs(injector.getInstance(InferenceGate.class));
        assertThat(injector.getInstance(ProviderClientFactory.class))
                .isSameAs(injector.getInstance(ProviderClientFactory.class));
        assertThat(injector.getInstance(RetryPolicy.class)).isSameAs(injector.getInstance(RetryPolicy.class));
    }

    private ChatModelFactory factory() {
        final ProviderClientFactory clients =
                new ProviderClientFactory(new HttpExchange(new HttpClients()), new LlmModule().objectMapper());
        final RetryPolicy retries = new RetryPolicy(
                Clock.fixed(Instant.parse("2026-09-22T12:00:00Z"), ZoneOffset.UTC), () -> 0.5, ignored -> {});
        return new ChatModelFactoryImpl(configs, clients, new InferenceGate(), retries, new LlmModule().objectMapper());
    }

    private void register(ProviderConfig config) {
        assertThat(configs.register(config).isOk()).isTrue();
    }

    private ChatModel pseudoModel() {
        return Objects.requireNonNull(
                factory().create(new ModelSelection("pseudo", "uppercase")).data(), "pseudo model");
    }

    private static ChatRequest chatRequest() {
        return new ChatRequest(List.of(new ChatMessage(ChatRole.USER, "hello")));
    }

    private static ChatResponse responseOf(Result<ChatResponse> result) {
        return Objects.requireNonNull(result.data(), "response");
    }

    private static AppError errorOf(Result<?> result) {
        return Objects.requireNonNull(result.error(), "error");
    }
}
