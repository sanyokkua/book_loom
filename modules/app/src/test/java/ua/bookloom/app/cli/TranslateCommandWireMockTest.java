package ua.bookloom.app.cli;

import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import com.google.inject.Guice;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.app.CoreModules;
import ua.bookloom.app.StartupContext;
import ua.bookloom.app.bootstrap.ReviewModeResolver;
import ua.bookloom.util.paths.AppEnvironment;
import ua.bookloom.util.paths.AppPaths;

/** The command's real provider clients against a WireMock endpoint: what it sends, and what it never sends. */
class TranslateCommandWireMockTest {

    private static final String REFUSAL =
            "This destination already exists: " + "Choose a new destination or allow the existing file to be replaced.";
    private static final String CONTEXT_LENGTH_URL = "/api/v0/models/qwen3";
    private static final String STATUS_REPLY = "{\"status\":\"ok\"}";
    private static final String TARGET_REPLY = "{\"target\":\"HE OPENED THE ⟦g0⟧OLD⟦g1⟧ DOOR.\"}";

    private final WireMockServer server =
            new WireMockServer(WireMockConfiguration.options().dynamicPort());

    @TempDir
    private Path tempDir;

    @BeforeEach
    void startServer() {
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop();
    }

    // An occupied destination stops the command before the provider is even asked whether it is reachable.
    @Test
    void run_existingDestinationWithoutOverwrite_refusesBeforeAnyProviderRequest() throws IOException {
        final Path source = Files.writeString(tempDir.resolve("Book.md"), "He opened the *old* door.\n");
        final Path destination = Files.writeString(tempDir.resolve("Book.uk.md"), "KEEP THIS FILE\n");
        final ByteArrayOutputStream console = new ByteArrayOutputStream();

        final int exit = run(
                List.of(
                        source.toString(),
                        "--provider",
                        "ollama",
                        "--model",
                        "gemma4:e4b-mlx",
                        "--base-url",
                        server.baseUrl()),
                console);

        assertThat(exit).isEqualTo(1);
        assertThat(text(console).lines()).containsExactly(REFUSAL);
        assertThat(server.getAllServeEvents()).isEmpty();
        assertThat(Files.readString(destination)).isEqualTo("KEEP THIS FILE\n");
    }

    // A custom OpenAI-compatible endpoint receives every request on its own host and no credential header: the model
    // list, the chat calls and the one read of the model's context length, which LM Studio serves beside /v1.
    @Test
    void run_openAiCompatibleProvider_sendsEveryRequestToTheBaseUrlWithoutAuthorization() throws IOException {
        server.stubFor(get(urlEqualTo("/v1/models"))
                .willReturn(okJson("{\"object\":\"list\",\"data\":[{\"id\":\"qwen3\",\"object\":\"model\"}]}")));
        server.stubFor(post(urlEqualTo("/v1/chat/completions")).willReturn(okJson(completion(TARGET_REPLY))));
        server.stubFor(post(urlEqualTo("/v1/chat/completions"))
                .withRequestBody(containing("Return exactly one JSON object"))
                .atPriority(1)
                .willReturn(okJson(completion(STATUS_REPLY))));
        final Path source = Files.writeString(tempDir.resolve("Book.md"), "He opened the *old* door.\n");
        final ByteArrayOutputStream console = new ByteArrayOutputStream();

        final int exit = run(
                List.of(
                        source.toString(),
                        "--provider",
                        "openai-compatible",
                        "--base-url",
                        server.baseUrl() + "/v1",
                        "--model",
                        "qwen3"),
                console);

        assertThat(exit).isEqualTo(0);
        assertThat(server.findAll(anyRequestedFor(anyUrl())))
                .isNotEmpty()
                .extracting(LoggedRequest::getUrl)
                .allSatisfy(url -> assertThat(url).isIn(CONTEXT_LENGTH_URL, "/v1/models", "/v1/chat/completions"));
        assertThat(server.findAll(anyRequestedFor(anyUrl())))
                .noneSatisfy(request ->
                        assertThat(request.containsHeader("Authorization")).isTrue());
        assertThat(tempDir.resolve("Book.uk.md")).isRegularFile();
    }

    private int run(List<String> arguments, ByteArrayOutputStream console) throws IOException {
        final Path logDir = Files.createDirectories(tempDir.resolve("test-logs"));
        final StartupContext startup = new StartupContext(
                AppPaths.of(tempDir.resolve("data"), logDir),
                AppEnvironment.DEV,
                ReviewModeResolver.resolve(name -> null, name -> null));
        return Guice.createInjector(new CoreModules(startup))
                .getInstance(TranslateCommand.class)
                .run(arguments, new PrintStream(console, true, StandardCharsets.UTF_8));
    }

    private static String completion(String content) {
        return "{\"model\":\"qwen3\",\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":" + quote(content)
                + "},\"finish_reason\":\"stop\"}]}";
    }

    private static String quote(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private static String text(ByteArrayOutputStream console) {
        return console.toString(StandardCharsets.UTF_8);
    }
}
