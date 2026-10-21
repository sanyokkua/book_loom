package ua.bookloom.llm.client.ollama;

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
import ua.bookloom.api.Result;
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

/** A model's stored settings reach an Ollama-native server in the shape its API takes. */
class OllamaClientModelSettingsTest {

    private static final String MODEL = "gemma4:e4b-mlx";
    private static final String REPLY = """
            {"model":"gemma4:e4b-mlx","message":{"content":"reply"},"done":true,"done_reason":"stop"}
            """;

    private final WireMockServer server =
            new WireMockServer(WireMockConfiguration.options().dynamicPort().bindAddress("127.0.0.1"));
    private LlmTestStorageModule storage;
    private ChatModelFactory factory;

    @BeforeEach
    void startServer() {
        server.start();
        server.stubFor(post(urlEqualTo("/api/chat")).willReturn(okJson(REPLY)));
        final FakeProviderConfigs configs = new FakeProviderConfigs();
        configs.save(new ProviderConfig(
                "ollama",
                ProviderKind.OLLAMA,
                URI.create(server.baseUrl()),
                ProviderConfig.DEFAULT_CONNECT_TIMEOUT,
                ProviderConfig.DEFAULT_REQUEST_TIMEOUT));
        storage = new LlmTestStorageModule(configs);
        factory = Guice.createInjector(new LlmModule(), storage).getInstance(ChatModelFactory.class);
    }

    @AfterEach
    void stopServer() {
        server.stop();
    }

    // IF the temperature is Don't send, THEN options carry no temperature even though the call kind has one.
    @Test
    void chat_temperatureOmit_optionsHaveNoTemperature() {
        storage.modelSettings().save(settings(TemperatureChoice.OMIT, null, null, null, OutputLengthField.MAX_TOKENS));

        model().chat(request(0.2, 200));

        assertThat(body()).contains("\"options\"").doesNotContain("temperature");
    }

    // Ollama has no max_completion_tokens: the cap is always options.num_predict.
    @Test
    void chat_maxCompletionTokensChosen_bodyHasNumPredictOnly() {
        storage.modelSettings()
                .save(settings(TemperatureChoice.CHOOSE, null, null, 128, OutputLengthField.MAX_COMPLETION_TOKENS));

        model().chat(request(0.2, 400));

        assertThat(body())
                .contains("\"num_predict\":128")
                .doesNotContain("max_completion_tokens")
                .doesNotContain("max_tokens");
    }

    @Test
    void chat_temperatureValue_optionsCarryOnlyThatValue() {
        storage.modelSettings().save(settings(TemperatureChoice.VALUE, 0.7, null, null, OutputLengthField.MAX_TOKENS));

        model().chat(request(0.2, 200));

        assertThat(body()).contains("\"temperature\":0.7");
    }

    // A request that carries the window keeps it: the stored override is applied by the pipeline, not rewritten here.
    @Test
    void chat_requestCarriesWindowUnderOverride_numCtxIsTheRequestsWindow() {
        storage.modelSettings()
                .save(settings(TemperatureChoice.CHOOSE, null, 8192, null, OutputLengthField.MAX_TOKENS));
        final ChatModel model = model();

        model.chat(request(0.2, 200).sizedTo(8192, 4096));

        assertThat(model.contextWindowOverride()).isEqualTo(8192);
        assertThat(body()).contains("\"num_ctx\":8192");
    }

    private ChatModel model() {
        final Result<ChatModel> created = factory.create(new ModelSelection("ollama", MODEL));
        return Objects.requireNonNull(created.data(), "model");
    }

    private String body() {
        return server.getAllServeEvents().getFirst().getRequest().getBodyAsString();
    }

    private static ModelSettings settings(
            TemperatureChoice choice,
            @Nullable Double temperature,
            @Nullable Integer window,
            @Nullable Integer maxOutput,
            OutputLengthField field) {
        return new ModelSettings("ollama", MODEL, choice, temperature, window, maxOutput, field, List.of(), null);
    }

    private static ChatRequest request(double temperature, int cap) {
        return new ChatRequest(
                List.of(new ChatMessage(ChatRole.USER, "Translate this.")), temperature, null, null, null, null, cap);
    }
}
