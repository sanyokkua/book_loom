package ua.bookloom.llm.client.openai;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import ua.bookloom.api.AppError;
import ua.bookloom.api.llm.ChatMessage;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.ChatRole;
import ua.bookloom.api.llm.ProviderAuth;
import ua.bookloom.api.llm.ProviderConfig;
import ua.bookloom.api.llm.ProviderKind;
import ua.bookloom.api.llm.ReasoningLevel;
import ua.bookloom.api.llm.ServerEngine;
import ua.bookloom.llm.LlmModule;
import ua.bookloom.llm.http.HttpClients;
import ua.bookloom.llm.http.HttpExchange;
import ua.bookloom.llm.provider.ProviderCallResult;

/**
 * Shared seam for the engine error-row tests: a fixture file named {@code <name>.<status>.json|sse} is served as the
 * reply of one chat call and the resulting error is read back. The request carries no response format and no seed, so
 * no refusal resend can fire and exactly one request reaches the server.
 */
abstract class ErrorRowsWireMockSupport {

    private static final String CHAT_PATH = "/v1/chat/completions";
    private static final String MODEL_ID = "test-model";
    private static final int WINDOW = 8192;
    private static final int CAP = 256;
    private static final Pattern STATUS_IN_NAME = Pattern.compile("\\.(\\d{3})\\.[a-z]+$");

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

    /** The fixture folder under {@code errors/}, for example {@code lmstudio}. */
    abstract String folder();

    /** The engine the provider under test is saved with; the rows tied to it apply. */
    ServerEngine engine() {
        return ServerEngine.GENERIC;
    }

    /** The classpath path of a fixture; by default under {@code /errors/<folder>/}. */
    String resourcePath(final String fixture) {
        return "/errors/" + folder() + "/" + fixture;
    }

    /** Serves the fixture with the status its name carries and returns the error the client reports. */
    final AppError chatError(final String fixture) {
        return Objects.requireNonNull(chatCall(fixture, aResponse()).result().error(), "error");
    }

    /**
     * Serves the fixture with the status its name carries, on top of the given reply (its headers), and returns the
     * whole failed call.
     */
    final ProviderCallResult<ChatResponse> chatCall(final String fixture, final ResponseDefinitionBuilder reply) {
        server.stubFor(post(urlEqualTo(CHAT_PATH))
                .willReturn(reply.withStatus(statusOf(fixture)).withBody(body(fixture))));
        final ProviderCallResult<ChatResponse> call = client().chat(MODEL_ID, request());
        assertThat(call.result().isErr()).as("fixture %s must fail", fixture).isTrue();
        return call;
    }

    final void verifyOneRequest() {
        server.verify(1, postRequestedFor(urlEqualTo(CHAT_PATH)));
    }

    final String body(final String fixture) {
        final String path = resourcePath(fixture);
        try (InputStream in = ErrorRowsWireMockSupport.class.getResourceAsStream(path)) {
            return new String(Objects.requireNonNull(in, path).readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // The second-to-last dot segment of the name is the status.
    private static int statusOf(final String fixture) {
        final Matcher matcher = STATUS_IN_NAME.matcher(fixture);
        assertThat(matcher.find()).as("status in fixture name %s", fixture).isTrue();
        return Integer.parseInt(matcher.group(1));
    }

    private static ChatRequest request() {
        return new ChatRequest(
                List.of(new ChatMessage(ChatRole.USER, "hello")),
                null,
                null,
                ReasoningLevel.OFF,
                WINDOW,
                null,
                CAP,
                null,
                null);
    }

    private OpenAiCompatibleClient client() {
        final ProviderConfig config = new ProviderConfig(
                "engine",
                ProviderKind.OPENAI_COMPATIBLE,
                URI.create(server.baseUrl() + "/v1"),
                Duration.ofSeconds(2),
                Duration.ofSeconds(2),
                null,
                ProviderAuth.NONE,
                null,
                null,
                null,
                null,
                Map.of(),
                List.of(),
                ServerEngine.GENERIC,
                engine(),
                null,
                false);
        return new OpenAiCompatibleClient(
                config, new HttpExchange(new HttpClients()), new LlmModule().objectMapper(), System::nanoTime);
    }
}
