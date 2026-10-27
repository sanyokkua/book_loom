package ua.bookloom.llm.client.openai;

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
import ua.bookloom.api.AppError;
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

/** Sign-in headers and path overrides of the OpenAI-compatible client, seen at the HTTP seam. */
class OpenAiCompatibleClientAuthTest {

    private static final String KEY_VARIABLE = "BOOKLOOM_TEST_KEY";
    private static final String KEY_VALUE = "sk-test-123";
    private static final String CHAT_REPLY =
            "{\"model\":\"m\",\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"hi\"},\"finish_reason\":\"stop\"}]}";
    private static final String MODELS_REPLY = "{\"data\":[{\"id\":\"model-a\",\"object\":\"model\"}]}";

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

    // IF the provider has a bearer key and a custom header, THEN a chat request carries both.
    @Test
    void chat_bearerAuthAndCustomHeader_sendsBothHeaders() {
        server.stubFor(post(urlEqualTo("/v1/chat/completions")).willReturn(okJson(CHAT_REPLY)));
        final ProviderConfig config = base().withAuth(ProviderAuth.BEARER)
                .withCredentialRef(KEY_VARIABLE)
                .withHeaders(Map.of("X-Org", "acme"));

        final Result<ChatResponse> result = client(config, Map.of(KEY_VARIABLE, KEY_VALUE))
                .chat("model-a", request())
                .result();

        assertThat(result.isOk()).as("result: " + result.error()).isTrue();
        server.verify(postRequestedFor(urlEqualTo("/v1/chat/completions"))
                .withHeader("Authorization", equalTo("Bearer " + KEY_VALUE))
                .withHeader("X-Org", equalTo("acme")));
    }

    // IF the chat path is overridden, THEN chat goes to the override and not to /chat/completions.
    @Test
    void chat_chatPathOverride_postsToTheOverride() {
        server.stubFor(post(urlEqualTo("/v1/custom/chat")).willReturn(okJson(CHAT_REPLY)));

        final Result<ChatResponse> result = client(base().withChatPath("/custom/chat"), Map.of())
                .chat("model-a", request())
                .result();

        assertThat(result.isOk()).as("result: " + result.error()).isTrue();
        server.verify(1, postRequestedFor(urlEqualTo("/v1/custom/chat")));
        server.verify(0, postRequestedFor(urlEqualTo("/v1/chat/completions")));
    }

    // IF the models path is overridden, THEN model listing goes to the override.
    @Test
    void listModels_modelsPathOverride_getsTheOverride() {
        server.stubFor(get(urlEqualTo("/v1/catalog")).willReturn(okJson(MODELS_REPLY)));

        final Result<List<ModelInfo>> result =
                client(base().withModelsPath("/catalog"), Map.of()).listModels().result();

        assertThat(Objects.requireNonNull(result.data(), "models")).containsExactly(new ModelInfo("model-a"));
        server.verify(0, getRequestedFor(urlEqualTo("/v1/models")));
    }

    // IF the models path is overridden, THEN the reachability probe asks the override too.
    @Test
    void probe_modelsPathOverride_getsTheOverride() {
        server.stubFor(get(urlEqualTo("/v1/catalog")).willReturn(okJson(MODELS_REPLY)));

        final Result<Boolean> result =
                client(base().withModelsPath("/catalog"), Map.of()).probe().result();

        assertThat(result.data()).isTrue();
        server.verify(1, getRequestedFor(urlEqualTo("/v1/catalog")));
    }

    // IF the key variable is unset, THEN listing models fails with missingCredential, not discoveryFailed.
    @Test
    void listModels_unsetKey_isMissingCredentialNotDiscoveryFailed() {
        final ProviderConfig config = base().withAuth(ProviderAuth.BEARER).withCredentialRef(KEY_VARIABLE);

        final Result<List<ModelInfo>> result =
                client(config, Map.of()).listModels().result();

        assertThat(error(result).code()).isEqualTo(ErrorCode.missingCredential);
        assertThat(server.getAllServeEvents()).isEmpty();
    }

    // IF the key variable is unset, THEN chat fails with missingCredential and no request is sent.
    @Test
    void chat_unsetKey_isMissingCredentialAndSendsNothing() {
        final ProviderConfig config = base().withAuth(ProviderAuth.BEARER).withCredentialRef(KEY_VARIABLE);

        final Result<ChatResponse> result =
                client(config, Map.of()).chat("model-a", request()).result();

        assertThat(error(result).code()).isEqualTo(ErrorCode.missingCredential);
        assertThat(server.getAllServeEvents()).isEmpty();
    }

    private ProviderConfig base() {
        return new ProviderConfig(
                "remote",
                ProviderKind.OPENAI_COMPATIBLE,
                URI.create(server.baseUrl() + "/v1"),
                Duration.ofSeconds(2),
                Duration.ofSeconds(3));
    }

    private static OpenAiCompatibleClient client(ProviderConfig config, Map<String, String> env) {
        return new OpenAiCompatibleClient(
                config,
                new HttpExchange(new HttpClients(), System::nanoTime, env::get),
                new LlmModule().objectMapper(),
                System::nanoTime);
    }

    private static ChatRequest request() {
        return new ChatRequest(List.of(new ChatMessage(ChatRole.USER, "hello")));
    }

    private static <T> AppError error(Result<T> result) {
        return Objects.requireNonNull(result.error(), "error");
    }
}
