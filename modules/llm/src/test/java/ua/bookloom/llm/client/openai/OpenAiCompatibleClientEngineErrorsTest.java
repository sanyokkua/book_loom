package ua.bookloom.llm.client.openai;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.FailureReason;
import ua.bookloom.api.llm.ChatMessage;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ChatRole;
import ua.bookloom.api.llm.ProviderConfig;
import ua.bookloom.api.llm.ProviderKind;
import ua.bookloom.api.llm.ResponseFormat;
import ua.bookloom.api.llm.ServerEngine;
import ua.bookloom.llm.LlmModule;
import ua.bookloom.llm.http.HttpClients;
import ua.bookloom.llm.http.HttpExchange;
import ua.bookloom.llm.provider.ProviderCallResult;
import ua.bookloom.llm.provider.RejectedCapability;

/** The Unsloth rows apply to that engine only, and an error envelope with an integer code is read like the string. */
class OpenAiCompatibleClientEngineErrorsTest {

    private static final String CHAT_PATH = "/v1/chat/completions";
    private static final String MODEL_ID = "unsloth-model";
    private static final String KEY_SENTENCE =
            "Unsloth needs an API key. Create one in Unsloth › Settings › API and put"
                    + " its environment-variable name here.";
    private static final String SCHEMA = "{\"type\":\"object\",\"properties\":{\"t\":{\"type\":\"string\"}}}";

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

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {"{\"detail\":{\"type\":\"authentication_error\",\"message\":\"Invalid API key\"}}", "''"})
    void chat_unslothReplyWithoutKey401_isAuthWithTheSentence(final String body) {
        server.stubFor(post(urlEqualTo(CHAT_PATH))
                .willReturn(aResponse().withStatus(401).withBody(body)));

        final AppError error = chatError(ServerEngine.UNSLOTH, plainRequest());

        assertThat(error.code()).isEqualTo(ErrorCode.auth);
        assertThat(error.message()).isEqualTo(KEY_SENTENCE);
        assertThat(FailureReason.in(error.details())).contains(FailureReason.AUTH);
    }

    @Test
    void chat_otherEngineReply401_keepsTheGenericSignInWords() {
        server.stubFor(post(urlEqualTo(CHAT_PATH))
                .willReturn(aResponse().withStatus(401).withBody("{\"error\":{\"type\":\"authentication_error\"}}")));

        final AppError error = chatError(ServerEngine.GENERIC, plainRequest());

        assertThat(error.code()).isEqualTo(ErrorCode.auth);
        assertThat(error.message()).doesNotContain("Unsloth");
    }

    @Test
    void chat_unslothSchemaRefused400_isStructuredUnavailableAndDropsTheSchema() {
        server.stubFor(post(urlEqualTo(CHAT_PATH))
                .willReturn(aResponse()
                        .withStatus(400)
                        .withBody("{\"detail\":\"response_format json_schema is not supported by this model\"}")));
        final OpenAiCompatibleClient client = client(ServerEngine.UNSLOTH);

        final ProviderCallResult<?> call = client.chat(MODEL_ID, request(new ResponseFormat("t", SCHEMA)));
        final AppError error = Objects.requireNonNull(call.result().error(), "error");

        assertThat(error.code()).isEqualTo(ErrorCode.validation);
        assertThat(FailureReason.in(error.details())).contains(FailureReason.STRUCTURED_UNAVAILABLE);
        assertThat(call.rejectedCapability()).isEqualTo(RejectedCapability.STRUCTURED_OUTPUT);
    }

    @Test
    void chat_unslothRowOnAnotherEngine_isPlainBadRequest() {
        server.stubFor(post(urlEqualTo(CHAT_PATH))
                .willReturn(aResponse()
                        .withStatus(400)
                        .withBody("{\"detail\":\"response_format json_schema is not supported by this model\"}")));

        final AppError error = chatError(ServerEngine.GENERIC, plainRequest());

        assertThat(FailureReason.in(error.details())).contains(FailureReason.UNCLASSIFIED);
    }

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                "400|{\"error\":{\"code\":400,\"message\":\"The context size has been exceeded.\"}}"
                        + "|contextWindow|context-overflow",
                "400|{\"object\":\"error\",\"code\":400,\"message\":\"The context size has been exceeded.\"}"
                        + "|contextWindow|context-overflow",
                "400|{\"error\":{\"code\":400,\"message\":\"Bad value\"}}|validation|unclassified",
                "500|{\"object\":\"error\",\"code\":500,\"message\":\"boom\"}|upstream|unclassified",
                "200|{\"error\":{\"code\":404,\"message\":\"Unexpected endpoint\"}}|validation|wrong-path"
            })
    void chat_integerCodeEnvelope_isReadByStatusAndMessage(
            final int status, final String body, final ErrorCode code, final String reason) {
        server.stubFor(post(urlEqualTo(CHAT_PATH))
                .willReturn(aResponse().withStatus(status).withBody(body)));

        final AppError error = chatError(ServerEngine.GENERIC, plainRequest());

        assertThat(error.code()).isEqualTo(code);
        assertThat(FailureReason.in(error.details()).map(FailureReason::token)).contains(reason);
    }

    private AppError chatError(final ServerEngine engine, final ChatRequest request) {
        return Objects.requireNonNull(
                client(engine).chat(MODEL_ID, request).result().error(), "error");
    }

    private static ChatRequest plainRequest() {
        return request(null);
    }

    private static ChatRequest request(final @Nullable ResponseFormat format) {
        return new ChatRequest(
                List.of(new ChatMessage(ChatRole.USER, "hello")), null, format, null, null, null, 256, null, null);
    }

    private OpenAiCompatibleClient client(final ServerEngine engine) {
        final ProviderConfig config = new ProviderConfig(
                        "engine-under-test",
                        ProviderKind.OPENAI_COMPATIBLE,
                        URI.create(server.baseUrl() + "/v1"),
                        Duration.ofSeconds(2),
                        Duration.ofSeconds(2))
                .withEngine(engine);
        return new OpenAiCompatibleClient(
                config, new HttpExchange(new HttpClients()), new LlmModule().objectMapper(), System::nanoTime);
    }
}
