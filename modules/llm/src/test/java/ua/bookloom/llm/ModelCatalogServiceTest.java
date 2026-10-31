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
import ua.bookloom.api.llm.ServerEngine;
import ua.bookloom.llm.http.HttpClients;
import ua.bookloom.llm.http.HttpExchange;
import ua.bookloom.llm.provider.ProviderClientFactory;
import ua.bookloom.llm.testing.FakeProviderConfigs;
import ua.bookloom.llm.testing.LlmTestStorageModule;

/** Exercises model listing through the real provider clients against a WireMock HTTP endpoint. */
class ModelCatalogServiceTest {

    private static final String OLLAMA_TAGS_PATH = "/api/tags";
    private static final String OPENAI_MODELS_PATH = "/v1/models";
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(2);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(3);

    private final WireMockServer server =
            new WireMockServer(WireMockConfiguration.options().dynamicPort().bindAddress("127.0.0.1"));
    private FakeProviderConfigs configs;
    private ModelCatalogService catalog;

    @BeforeEach
    void startServer() {
        server.start();
        configs = new FakeProviderConfigs();
        catalog = new ModelCatalogService(
                configs,
                new ProviderClientFactory(
                        new HttpExchange(new HttpClients()), new LlmModule().objectMapper(), System::nanoTime));
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

    // LM Studio's OpenAI list says nothing about a model's kind, so its own list is read and its embedding model left
    // out.
    @Test
    void discover_lmStudioEmbeddingEntry_isLeftOut() {
        register(
                "lmstudio",
                ProviderKind.OPENAI_COMPATIBLE,
                URI.create(server.baseUrl() + "/v1"),
                ServerEngine.LM_STUDIO);
        server.stubFor(get(urlEqualTo("/api/v0/models"))
                .willReturn(okJson("{\"object\":\"list\",\"data\":[{\"id\":\"google/gemma-4-e4b\",\"type\":\"vlm\"},"
                        + "{\"id\":\"lapa-v0.1.2-instruct\",\"type\":\"llm\"},"
                        + "{\"id\":\"text-embedding-nomic-embed-text-v1.5\",\"type\":\"embeddings\"}]}")));

        final Result<List<ModelInfo>> result = catalog.listModels("lmstudio");

        assertThat(result.data())
                .containsExactly(new ModelInfo("google/gemma-4-e4b"), new ModelInfo("lapa-v0.1.2-instruct"));
        server.verify(0, getRequestedFor(urlEqualTo(OPENAI_MODELS_PATH)));
    }

    // A vision projector file next to the weights is no model to translate with.
    @Test
    void discover_routerListingWithMmproj_dropsTheSidecar() {
        register("llama", ProviderKind.OPENAI_COMPATIBLE, URI.create(server.baseUrl() + "/v1"), ServerEngine.LLAMA_CPP);
        server.stubFor(get(urlEqualTo(OPENAI_MODELS_PATH))
                .willReturn(okJson("{\"object\":\"list\",\"data\":["
                        + "{\"id\":\"Model-9B-Q4_K_M.gguf\",\"object\":\"model\",\"owned_by\":\"llamacpp\"},"
                        + "{\"id\":\"mmproj-BF16.gguf\",\"object\":\"model\",\"owned_by\":\"llamacpp\"}]}")));

        assertThat(catalog.listModels("llama").data()).containsExactly(new ModelInfo("Model-9B-Q4_K_M.gguf"));
    }

    // An older LM Studio with no own list is still listed through its OpenAI route.
    @Test
    void discover_lmStudioWithoutOwnList_readsTheOpenAiList() {
        register(
                "lmstudio",
                ProviderKind.OPENAI_COMPATIBLE,
                URI.create(server.baseUrl() + "/v1"),
                ServerEngine.LM_STUDIO);
        server.stubFor(
                get(urlEqualTo(OPENAI_MODELS_PATH)).willReturn(okJson("{\"data\":[{\"id\":\"google/gemma-4-e4b\"}]}")));

        assertThat(catalog.listModels("lmstudio").data()).containsExactly(new ModelInfo("google/gemma-4-e4b"));
    }

    // The owner's decision: an Ollama cloud model is listed with a neutral label, never hidden.
    @Test
    void discover_ollamaCloudSuffix_isLabelledCloudAndListed() {
        register("ollama", ProviderKind.OLLAMA, URI.create(server.baseUrl()), ServerEngine.OLLAMA);
        server.stubFor(get(urlEqualTo(OLLAMA_TAGS_PATH))
                .willReturn(okJson("{\"models\":[{\"name\":\"gemma4:e4b-mlx\"},{\"name\":\"gpt-oss:120b-cloud\"},"
                        + "{\"name\":\"qwen3-coder:480b\",\"remote_host\":\"https://ollama.com:443\"}]}")));

        final Result<List<ModelInfo>> result = catalog.listModels("ollama");

        assertThat(result.data())
                .containsExactly(
                        new ModelInfo("gemma4:e4b-mlx", false),
                        new ModelInfo("gpt-oss:120b-cloud", true),
                        new ModelInfo("qwen3-coder:480b", true));
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
        final ModelCatalog bound = Guice.createInjector(new LlmModule(), new LlmTestStorageModule())
                .getInstance(ModelCatalog.class);

        assertThat(bound).isInstanceOf(ModelCatalogService.class);
    }

    private void register(String id, ProviderKind kind, URI baseUrl) {
        register(id, kind, baseUrl, ServerEngine.GENERIC);
    }

    private void register(String id, ProviderKind kind, URI baseUrl, ServerEngine engine) {
        final ProviderConfig config =
                new ProviderConfig(id, kind, baseUrl, CONNECT_TIMEOUT, REQUEST_TIMEOUT).withEngine(engine);
        assertThat(configs.save(config).isOk()).isTrue();
    }

    private static URI closedEndpoint() {
        return ClosedPorts.endpoint("");
    }
}
