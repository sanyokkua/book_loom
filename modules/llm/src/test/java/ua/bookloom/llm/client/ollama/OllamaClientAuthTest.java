package ua.bookloom.llm.client.ollama;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ChatMessage;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.ChatRole;
import ua.bookloom.api.llm.ModelInfo;
import ua.bookloom.api.llm.ProviderAuth;
import ua.bookloom.api.llm.ProviderConfig;
import ua.bookloom.api.llm.ProviderKind;
import ua.bookloom.llm.LlmModule;
import ua.bookloom.llm.http.HttpClients;
import ua.bookloom.llm.http.HttpExchange;

/** Sign-in headers and the models-path override of the Ollama-native client, seen at the HTTP seam. */
class OllamaClientAuthTest {

    private static final String KEY_VARIABLE = "BOOKLOOM_TEST_KEY";
    private static final String KEY_VALUE = "sk-test-123";
    private static final String TAGS_REPLY = "{\"models\":[{\"name\":\"model-a\",\"model\":\"model-a\",\"size\":1}]}";
    private static final String CHAT_REPLY =
            "{\"model\":\"model-a\",\"message\":{\"role\":\"assistant\",\"content\":\"hi\"},\"done\":true,\"done_reason\":\"stop\"}";

    private final WireMockServer server =
            new WireMockServer(WireMockConfiguration.options().dynamicPort().bindAddress("127.0.0.1"));

    @BeforeEach
    void startServer() {
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop();
    }

    // IF the models path is overridden, THEN listing goes to the override and never to /api/tags.
    @Test
    void listModels_modelsPathOverride_getsTheOverride() {
        server.stubFor(get(urlEqualTo("/catalog/tags")).willReturn(okJson(TAGS_REPLY)));

        final Result<List<ModelInfo>> result = client(base().withModelsPath("/catalog/tags"), Map.of())
                .listModels()
                .result();

        assertThat(Objects.requireNonNull(result.data(), "models")).containsExactly(new ModelInfo("model-a"));
        server.verify(0, getRequestedFor(urlEqualTo("/api/tags")));
    }

    // IF the provider has a bearer key, THEN model listing carries Authorization: Bearer.
    @Test
    void listModels_bearerAuth_sendsAuthorizationHeader() {
        server.stubFor(get(urlEqualTo("/api/tags")).willReturn(okJson(TAGS_REPLY)));
        final ProviderConfig config = base().withAuth(ProviderAuth.BEARER).withCredentialRef(KEY_VARIABLE);

        client(config, Map.of(KEY_VARIABLE, KEY_VALUE)).listModels();

        server.verify(
                getRequestedFor(urlEqualTo("/api/tags")).withHeader("Authorization", equalTo("Bearer " + KEY_VALUE)));
    }

    // IF the provider uses a key header, THEN the streamed /api/chat request carries it.
    @Test
    void chat_apiKeyAuth_streamedRequestCarriesTheKeyHeader() {
        server.stubFor(post(urlEqualTo("/api/chat")).willReturn(okJson(CHAT_REPLY)));
        final ProviderConfig config = base().withAuth(ProviderAuth.API_KEY)
                .withApiKeyHeader("X-Api-Key")
                .withCredentialRef(KEY_VARIABLE);

        final Result<ChatResponse> result = client(config, Map.of(KEY_VARIABLE, KEY_VALUE))
                .chat("model-a", new ChatRequest(List.of(new ChatMessage(ChatRole.USER, "hello"))))
                .result();

        assertThat(result.isOk()).as("result: " + result.error()).isTrue();
        server.verify(postRequestedFor(urlEqualTo("/api/chat"))
                .withHeader("X-Api-Key", equalTo(KEY_VALUE))
                .withoutHeader("Authorization"));
    }

    // IF the key variable is unset, THEN the probe fails with missingCredential and sends nothing.
    @Test
    void probe_unsetKey_isMissingCredentialAndSendsNothing() {
        final ProviderConfig config = base().withAuth(ProviderAuth.BEARER).withCredentialRef(KEY_VARIABLE);

        final Result<Boolean> result = client(config, Map.of()).probe().result();

        assertThat(Objects.requireNonNull(result.error(), "error").code()).isEqualTo(ErrorCode.missingCredential);
        assertThat(server.getAllServeEvents()).isEmpty();
    }

    // IF the key variable is unset, THEN listing models fails with missingCredential, not discoveryFailed.
    @Test
    void listModels_unsetKey_isMissingCredentialNotDiscoveryFailed() {
        final ProviderConfig config = base().withAuth(ProviderAuth.BEARER).withCredentialRef(KEY_VARIABLE);

        final Result<List<ModelInfo>> result =
                client(config, Map.of()).listModels().result();

        assertThat(Objects.requireNonNull(result.error(), "error").code()).isEqualTo(ErrorCode.missingCredential);
    }

    // IF the provider has a bearer key, THEN the context-length probe (/api/show) carries Authorization.
    @Test
    void contextLength_bearerAuth_showRequestCarriesAuthorization() {
        server.stubFor(post(urlEqualTo("/api/show"))
                .willReturn(
                        okJson("{\"model_info\":{\"general.architecture\":\"llama\",\"llama.context_length\":8192}}")));
        final ProviderConfig config = base().withAuth(ProviderAuth.BEARER).withCredentialRef(KEY_VARIABLE);

        client(config, Map.of(KEY_VARIABLE, KEY_VALUE)).contextLength("model-a");

        server.verify(
                postRequestedFor(urlEqualTo("/api/show")).withHeader("Authorization", equalTo("Bearer " + KEY_VALUE)));
    }

    private ProviderConfig base() {
        return new ProviderConfig(
                "remote",
                ProviderKind.OLLAMA,
                URI.create(server.baseUrl()),
                Duration.ofSeconds(2),
                Duration.ofSeconds(3));
    }

    private static OllamaClient client(ProviderConfig config, Map<String, String> env) {
        return new OllamaClient(
                config,
                new HttpExchange(new HttpClients(), System::nanoTime, env::get),
                new LlmModule().objectMapper());
    }
}
