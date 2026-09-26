package ua.bookloom.llm;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.google.inject.Guice;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ModelCatalog;
import ua.bookloom.api.llm.ModelInfo;
import ua.bookloom.api.llm.ProviderConfig;
import ua.bookloom.api.llm.ProviderKind;
import ua.bookloom.llm.http.HttpClients;
import ua.bookloom.llm.http.HttpExchange;
import ua.bookloom.llm.provider.ProviderClientFactory;

/** Exercises model listing through the real provider clients against a WireMock HTTP endpoint. */
class ModelCatalogServiceTest {

    private static final String OLLAMA_TAGS_PATH = "/api/tags";
    private static final String OPENAI_MODELS_PATH = "/v1/models";
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(2);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(3);

    private final WireMockServer server =
            new WireMockServer(WireMockConfiguration.options().dynamicPort());
    private InMemoryProviderConfigs configs;
    private ModelCatalogService catalog;

    @BeforeEach
    void startServer() {
        server.start();
        configs = new InMemoryProviderConfigs();
        catalog = new ModelCatalogService(
                configs,
                new ProviderClientFactory(new HttpExchange(new HttpClients()), new LlmModule().objectMapper()));
    }

    @AfterEach
    void stopServer() {
        server.stop();
    }

    // IF Ollama reports two tags, THEN the catalog returns their ids in the order the provider reported them.
    @Test
    void listModels_ollamaTagsWithTwoModels_returnsIdsInOrder() {
        register("ollama", ProviderKind.OLLAMA, URI.create(server.baseUrl()));
        server.stubFor(
                get(urlEqualTo(OLLAMA_TAGS_PATH))
                        .willReturn(
                                okJson(
                                        "{\"models\":[{\"name\":\"gemma3:12b\",\"model\":\"gemma3:12b\",\"size\":1},{\"name\":\"qwen3:8b\",\"model\":\"qwen3:8b\",\"size\":2}]}")));

        final Result<List<ModelInfo>> result = catalog.listModels("ollama");

        assertThat(result.isOk()).as("result: " + result.error()).isTrue();
        assertThat(Objects.requireNonNull(result.data(), "models"))
                .containsExactly(new ModelInfo("gemma3:12b"), new ModelInfo("qwen3:8b"));
        server.verify(1, getRequestedFor(urlEqualTo(OLLAMA_TAGS_PATH)));
    }

    // IF an OpenAI-compatible server reports one model, THEN the catalog returns that model id.
    @Test
    void listModels_openAiModelsWithOneModel_returnsItsId() {
        register("lmstudio", ProviderKind.OPENAI_COMPATIBLE, URI.create(server.baseUrl() + "/v1"));
        server.stubFor(get(urlEqualTo(OPENAI_MODELS_PATH))
                .willReturn(okJson(
                        "{\"object\":\"list\",\"data\":[{\"id\":\"google/gemma-3-12b\",\"object\":\"model\"}]}")));

        final Result<List<ModelInfo>> result = catalog.listModels("lmstudio");

        assertThat(result.isOk()).as("result: " + result.error()).isTrue();
        assertThat(Objects.requireNonNull(result.data(), "models"))
                .containsExactly(new ModelInfo("google/gemma-3-12b"));
        server.verify(1, getRequestedFor(urlEqualTo(OPENAI_MODELS_PATH)));
    }

    // IF the provider id is not registered, THEN the result is a validation error and no request is sent.
    @Test
    void listModels_unknownProviderId_isValidationErrorAndRecordsNoRequest() {
        // Both presets point at the recording server, so any fallback to a registered provider would be seen.
        register("ollama", ProviderKind.OLLAMA, URI.create(server.baseUrl()));
        register("lmstudio", ProviderKind.OPENAI_COMPATIBLE, URI.create(server.baseUrl() + "/v1"));

        final Result<List<ModelInfo>> result = catalog.listModels("not-a-provider");

        assertThat(result.isErr()).isTrue();
        assertThat(Objects.requireNonNull(result.error(), "error").code()).isEqualTo(ErrorCode.validation);
        assertThat(server.getAllServeEvents()).isEmpty();
    }

    // IF the endpoint refuses the connection, THEN the client's own error is passed through, not a validation error.
    @Test
    void listModels_unreachableEndpoint_passesTheClientErrorThrough() throws IOException {
        register("ollama", ProviderKind.OLLAMA, closedEndpoint());

        final Result<List<ModelInfo>> result = catalog.listModels("ollama");

        assertThat(result.isErr()).isTrue();
        assertThat(Objects.requireNonNull(result.error(), "error").code()).isEqualTo(ErrorCode.unreachable);
    }

    // IF the composition root is built, THEN ModelCatalog resolves to the service.
    @Test
    void llmModule_bindsModelCatalog() {
        final ModelCatalog bound = Guice.createInjector(new LlmModule()).getInstance(ModelCatalog.class);

        assertThat(bound).isInstanceOf(ModelCatalogService.class);
    }

    private void register(String id, ProviderKind kind, URI baseUrl) {
        final ProviderConfig config = new ProviderConfig(id, kind, baseUrl, CONNECT_TIMEOUT, REQUEST_TIMEOUT);
        assertThat(configs.register(config).isOk()).isTrue();
    }

    private static URI closedEndpoint() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return URI.create("http://127.0.0.1:" + socket.getLocalPort());
        }
    }
}
