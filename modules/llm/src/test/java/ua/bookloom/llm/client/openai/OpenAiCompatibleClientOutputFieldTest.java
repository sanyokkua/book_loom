package ua.bookloom.llm.client.openai;

import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.google.inject.Guice;
import java.net.URI;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
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
import ua.bookloom.api.llm.ProviderConfig;
import ua.bookloom.api.llm.ProviderKind;
import ua.bookloom.api.llm.TemperatureChoice;
import ua.bookloom.llm.LlmModule;
import ua.bookloom.llm.testing.FakeProviderConfigs;
import ua.bookloom.llm.testing.LlmTestStorageModule;

/** The model's stored output field decides whether an OpenAI-compatible body says max_tokens or max_completion_tokens. */
class OpenAiCompatibleClientOutputFieldTest {

    private static final String MODEL = "google/gemma-4-e4b";
    private static final String REPLY = """
            {"model":"google/gemma-4-e4b","choices":[{"message":{"content":"reply"},"finish_reason":"stop"}]}
            """;

    private final WireMockServer server =
            new WireMockServer(WireMockConfiguration.options().dynamicPort().bindAddress("127.0.0.1"));
    private LlmTestStorageModule storage;
    private ChatModelFactory factory;

    @BeforeEach
    void startServer() {
        server.start();
        server.stubFor(post(urlEqualTo("/v1/chat/completions")).willReturn(okJson(REPLY)));
        final FakeProviderConfigs configs = new FakeProviderConfigs();
        configs.save(new ProviderConfig(
                "lmstudio",
                ProviderKind.OPENAI_COMPATIBLE,
                URI.create(server.baseUrl() + "/v1"),
                ProviderConfig.DEFAULT_CONNECT_TIMEOUT,
                ProviderConfig.DEFAULT_REQUEST_TIMEOUT));
        storage = new LlmTestStorageModule(configs);
        factory = Guice.createInjector(new LlmModule(), storage).getInstance(ChatModelFactory.class);
    }

    @AfterEach
    void stopServer() {
        server.stop();
    }

    @Test
    void chat_maxCompletionTokensChosen_bodyHasThatFieldAndNoMaxTokens() {
        save(settings(null, 512, OutputLengthField.MAX_COMPLETION_TOKENS));

        model().chat(request(1000));

        assertThat(body()).contains("\"max_completion_tokens\":512").doesNotContain("\"max_tokens\"");
    }

    @Test
    void chat_requestCapBelowStoredCap_chosenFieldCarriesRequestCap() {
        save(settings(null, 512, OutputLengthField.MAX_COMPLETION_TOKENS));

        model().chat(request(80));

        assertThat(body()).contains("\"max_completion_tokens\":80").doesNotContain("\"max_tokens\"");
    }

    @Test
    void chat_maxTokensChosen_bodyHasMaxTokensAndNoMaxCompletionTokens() {
        save(settings(null, 64, OutputLengthField.MAX_TOKENS));

        model().chat(request(80));

        assertThat(body()).contains("\"max_tokens\":64").doesNotContain("max_completion_tokens");
    }

    // A call that states no cap sends neither field, whatever the model's field choice.
    @Test
    void chat_noCapAnywhere_bodyHasNeitherField() {
        save(settings(null, null, OutputLengthField.MAX_COMPLETION_TOKENS));

        model().chat(request(null));

        assertThat(body()).doesNotContain("max_tokens").doesNotContain("max_completion_tokens");
    }

    // Reasoning headroom raises the cap in the field the model chose.
    @Test
    void chat_reasoningSpentTheCap_raisedCapGoesInTheChosenField() {
        save(settings(null, null, OutputLengthField.MAX_COMPLETION_TOKENS));
        server.stubFor(post(urlEqualTo("/v1/chat/completions")).willReturn(okJson("""
                {"model":"google/gemma-4-e4b","choices":[{"message":{"content":"","reasoning_content":"thinking"},"finish_reason":"length"}]}
                """)));

        model().chat(request(80));

        assertThat(server.getAllServeEvents())
                .extracting(event -> event.getRequest().getBodyAsString())
                .allSatisfy(sent ->
                        assertThat(sent).contains("max_completion_tokens").doesNotContain("\"max_tokens\""));
    }

    private void save(ModelSettings settings) {
        storage.modelSettings().save(settings);
    }

    private ChatModel model() {
        return Objects.requireNonNull(
                factory.create(new ModelSelection("lmstudio", MODEL)).data(), "model");
    }

    private String body() {
        return server.getAllServeEvents().getFirst().getRequest().getBodyAsString();
    }

    private static ModelSettings settings(
            @Nullable Integer window, @Nullable Integer maxOutput, OutputLengthField field) {
        return new ModelSettings(
                "lmstudio", MODEL, TemperatureChoice.CHOOSE, null, window, maxOutput, field, List.of(), null, null);
    }

    private static ChatRequest request(@Nullable Integer cap) {
        return new ChatRequest(
                List.of(new ChatMessage(ChatRole.USER, "Translate this.")), 0.2, null, null, null, null, cap);
    }
}
