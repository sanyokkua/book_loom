package ua.bookloom.llm;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ModelInfo;
import ua.bookloom.api.llm.ProviderAuth;
import ua.bookloom.api.llm.ProviderConfig;
import ua.bookloom.api.llm.ProviderKind;
import ua.bookloom.llm.http.HttpClients;
import ua.bookloom.llm.http.HttpExchange;
import ua.bookloom.llm.provider.ProviderClientFactory;
import ua.bookloom.llm.testing.FakeProviderConfigs;

/** Listing the models of an unsaved provider description: signed in, never registered. */
class ModelCatalogServiceDraftTest {

    private static final String KEY_VARIABLE = "BOOKLOOM_TEST_KEY";
    private static final String KEY_VALUE = "sk-test-123";

    private final WireMockServer server =
            new WireMockServer(WireMockConfiguration.options().dynamicPort().bindAddress("127.0.0.1"));
    private FakeProviderConfigs configs;
    private @Nullable ProviderClientFactory factory;

    @BeforeEach
    void start() {
        server.start();
        configs = new FakeProviderConfigs();
    }

    @AfterEach
    void stop() {
        server.stop();
    }

    // IF an Ollama draft overrides the models path, THEN the models come from the override and nothing is registered.
    @Test
    void listModels_ollamaDraftWithModelsPath_listsFromOverrideAndRegistersNothing() {
        server.stubFor(get(urlEqualTo("/catalog/tags"))
                .willReturn(okJson("{\"models\":[{\"name\":\"model-a\",\"model\":\"model-a\",\"size\":1}]}")));
        final ProviderConfig draft = ollamaDraft("draft-ollama").withModelsPath("/catalog/tags");

        final Result<List<ModelInfo>> result = catalog(Map.of()).listModels(draft);

        assertThat(Objects.requireNonNull(result.data(), "models")).containsExactly(new ModelInfo("model-a"));
        assertThat(configs.find("draft-ollama")).isEmpty();
        server.verify(0, getRequestedFor(urlEqualTo("/api/tags")));
    }

    // IF an OpenAI-compatible draft has a bearer key, THEN the listing request carries it.
    @Test
    void listModels_openAiDraftWithBearer_sendsBearerHeader() {
        server.stubFor(get(urlEqualTo("/v1/models"))
                .withHeader("Authorization", equalTo("Bearer " + KEY_VALUE))
                .willReturn(okJson("{\"data\":[{\"id\":\"model-a\",\"object\":\"model\"}]}")));

        final Result<List<ModelInfo>> result =
                catalog(Map.of(KEY_VARIABLE, KEY_VALUE)).listModels(openAiDraft("draft-remote"));

        assertThat(Objects.requireNonNull(result.data(), "models")).containsExactly(new ModelInfo("model-a"));
    }

    // IF the draft's key variable is unset, THEN the result is missingCredential and nothing is sent.
    @Test
    void listModels_draftWithUnsetKey_isMissingCredentialAndSendsNothing() {
        final Result<List<ModelInfo>> result = catalog(Map.of()).listModels(openAiDraft("draft-remote"));

        assertThat(Objects.requireNonNull(result.error(), "error").code()).isEqualTo(ErrorCode.missingCredential);
        assertThat(server.getAllServeEvents()).isEmpty();
    }

    // IF the draft has the id of a saved provider, THEN the saved client stays cached and the saved config unchanged.
    @Test
    void listModels_draftWithSavedId_doesNotEvictTheSavedClient() {
        server.stubFor(get(urlEqualTo("/api/tags")).willReturn(okJson("{\"models\":[]}")));
        final ProviderConfig saved = ollamaDraft("remote").withBaseUrl(URI.create("http://localhost:11434"));
        assertThat(configs.save(saved).isOk()).isTrue();
        final ModelCatalogService catalog = catalog(Map.of());
        final var cachedBefore = Objects.requireNonNull(factory, "factory").create(saved);

        catalog.listModels(ollamaDraft("remote"));

        assertThat(Objects.requireNonNull(factory, "factory").create(saved)).isSameAs(cachedBefore);
        assertThat(configs.find("remote")).contains(saved);
    }

    // IF the rules refuse the draft, THEN the result is a validation error and no request is sent.
    @Test
    void listModels_invalidDraft_isValidationWithoutRequest() {
        final ProviderConfig invalid = ollamaDraft("draft-ollama").withChatPath("/custom/chat");

        final Result<List<ModelInfo>> result = catalog(Map.of()).listModels(invalid);

        assertThat(Objects.requireNonNull(result.error(), "error").code()).isEqualTo(ErrorCode.validation);
        assertThat(server.getAllServeEvents()).isEmpty();
    }

    private ProviderConfig ollamaDraft(String id) {
        return new ProviderConfig(
                id, ProviderKind.OLLAMA, URI.create(server.baseUrl()), Duration.ofSeconds(2), Duration.ofSeconds(3));
    }

    private ProviderConfig openAiDraft(String id) {
        return new ProviderConfig(
                id,
                ProviderKind.OPENAI_COMPATIBLE,
                URI.create(server.baseUrl() + "/v1"),
                Duration.ofSeconds(2),
                Duration.ofSeconds(3),
                null,
                ProviderAuth.BEARER,
                null,
                KEY_VARIABLE,
                null,
                null,
                Map.of(),
                List.of());
    }

    private ModelCatalogService catalog(Map<String, String> env) {
        factory = new ProviderClientFactory(
                new HttpExchange(new HttpClients(), System::nanoTime, env::get),
                new LlmModule().objectMapper(),
                System::nanoTime);
        return new ModelCatalogService(configs, factory);
    }
}
