package ua.bookloom.llm.client;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.any;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.FailureReason;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ChatMessage;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ChatRole;
import ua.bookloom.api.llm.ProviderAuth;
import ua.bookloom.api.llm.ProviderConfig;
import ua.bookloom.api.llm.ProviderKind;
import ua.bookloom.llm.LlmModule;
import ua.bookloom.llm.client.ollama.OllamaClient;
import ua.bookloom.llm.client.openai.OpenAiCompatibleClient;
import ua.bookloom.llm.http.HttpClients;
import ua.bookloom.llm.http.HttpExchange;
import ua.bookloom.llm.provider.ProviderClient;

/**
 * A server that answered is never worded as one that did not: a 401 or 403 says the server answered and the sign-in
 * is missing or wrong, and an HTTP 200 whose body is only {@code {"error": …}} (LM Studio asked without {@code /v1},
 * or a server of another kind) fails the check with a hint at the address, in both dialects.
 */
class ServerAnswerFailureTest {

    private static final String LM_STUDIO_WRONG_PATH = "{\"error\":\"Unexpected endpoint or method. (GET /models)\"}";

    private final WireMockServer server =
            new WireMockServer(WireMockConfiguration.options().dynamicPort().bindAddress("127.0.0.1"));

    @BeforeEach
    void start() {
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop();
    }

    @ParameterizedTest
    @CsvSource({
        "OLLAMA,probe,401,not authorised",
        "OLLAMA,models,403,forbidden",
        "OLLAMA,chat,401,not authorised",
        "OPENAI_COMPATIBLE,probe,401,not authorised",
        "OPENAI_COMPATIBLE,models,403,forbidden",
        "OPENAI_COMPATIBLE,chat,401,not authorised"
    })
    void call_refusedSignIn_isAuthSayingTheServerAnsweredWithItsStatus(
            final ProviderKind kind, final String call, final int status, final String meaning) {
        server.stubFor(any(anyUrl()).willReturn(aResponse().withStatus(status).withBody("{\"error\":\"no\"}")));

        final AppError error = Objects.requireNonNull(call(kind, "/v1", call).error(), "error");

        assertThat(error.code()).isEqualTo(ErrorCode.auth);
        assertThat(error.message())
                .isEqualTo("The server answered " + status + " (" + meaning
                        + "): the sign-in is missing or wrong. Check the sign-in type and the key variable.");
    }

    // The list keeps its own code (a list that cannot be read is a soft finding), but carries the same reason.
    @ParameterizedTest
    @CsvSource({
        "OLLAMA,probe,validation",
        "OLLAMA,models,discoveryFailed",
        "OPENAI_COMPATIBLE,probe,validation",
        "OPENAI_COMPATIBLE,models,discoveryFailed",
        "OPENAI_COMPATIBLE,chat,validation"
    })
    void call_okStatusWithOnlyAnErrorBody_failsAsTheWrongPath(
            final ProviderKind kind, final String call, final ErrorCode expected) {
        server.stubFor(any(anyUrl()).willReturn(aResponse().withStatus(200).withBody(LM_STUDIO_WRONG_PATH)));

        final AppError error = Objects.requireNonNull(call(kind, "", call).error(), "error");

        assertThat(error.code()).isEqualTo(expected);
        assertThat(FailureReason.in(error.details())).contains(FailureReason.WRONG_PATH);
    }

    @Test
    void probe_openAiAddressWithoutV1AnswersAnErrorBody_saysTheAddressProbablyNeedsV1() {
        server.stubFor(any(anyUrl()).willReturn(aResponse().withStatus(200).withBody(LM_STUDIO_WRONG_PATH)));

        final AppError error = Objects.requireNonNull(
                call(ProviderKind.OPENAI_COMPATIBLE, "", "probe").error(), "error");

        assertThat(error.message())
                .isEqualTo("The server answered, but it does not serve this path: the address probably needs /v1"
                        + " at the end.");
    }

    @Test
    void probe_ollamaKindAnswersAnErrorBody_saysToCheckTheAddressAndKind() {
        server.stubFor(any(anyUrl()).willReturn(aResponse().withStatus(200).withBody(LM_STUDIO_WRONG_PATH)));

        final AppError error =
                Objects.requireNonNull(call(ProviderKind.OLLAMA, "", "probe").error(), "error");

        assertThat(error.message())
                .isEqualTo("The server answered, but it does not serve this path: check the address and the kind of"
                        + " server.");
    }

    @Test
    void chat_missingKeyVariable_namesTheVariableAndWhereToSetIt() {
        final ProviderConfig config = config(ProviderKind.OPENAI_COMPATIBLE, "/v1")
                .withAuth(ProviderAuth.BEARER)
                .withCredentialRef("LMS_KEY");

        final AppError error = Objects.requireNonNull(
                client(config).chat("model-a", request()).result().error(), "error");

        assertThat(error.code()).isEqualTo(ErrorCode.missingCredential);
        assertThat(error.message())
                .startsWith("The environment variable LMS_KEY is not set where BookLoom can read it.")
                .contains("launchctl setenv LMS_KEY")
                .contains("setx LMS_KEY")
                .contains("environment.d");
        assertThat(server.getAllServeEvents()).isEmpty();
    }

    private Result<?> call(final ProviderKind kind, final String openAiSuffix, final String call) {
        final ProviderClient client = client(config(kind, openAiSuffix));
        return switch (call) {
            case "probe" -> client.probe().result();
            case "models" -> client.listModels().result();
            default -> client.chat("model-a", request()).result();
        };
    }

    private ProviderConfig config(final ProviderKind kind, final String openAiSuffix) {
        final String base = server.baseUrl() + (kind == ProviderKind.OLLAMA ? "" : openAiSuffix);
        return new ProviderConfig("p", kind, URI.create(base), Duration.ofSeconds(2), Duration.ofSeconds(2));
    }

    private static ProviderClient client(final ProviderConfig config) {
        final HttpExchange exchange =
                new HttpExchange(new HttpClients(), System::nanoTime, Map.<String, String>of()::get);
        return config.kind() == ProviderKind.OLLAMA
                ? new OllamaClient(config, exchange, new LlmModule().objectMapper())
                : new OpenAiCompatibleClient(config, exchange, new LlmModule().objectMapper(), System::nanoTime);
    }

    private static ChatRequest request() {
        return new ChatRequest(List.of(new ChatMessage(ChatRole.USER, "hello")));
    }
}
