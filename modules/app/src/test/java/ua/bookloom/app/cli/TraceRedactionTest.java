package ua.bookloom.app.cli;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.LoggerContext;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.google.inject.Guice;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;
import ua.bookloom.app.CoreModules;
import ua.bookloom.app.StartupContext;
import ua.bookloom.app.bootstrap.LoggingBootstrap;
import ua.bookloom.app.bootstrap.ResolvedLogLevel;
import ua.bookloom.app.bootstrap.ResolvedTraceFile;
import ua.bookloom.app.bootstrap.ReviewModeResolver;
import ua.bookloom.util.paths.AppEnvironment;
import ua.bookloom.util.paths.AppPaths;

/**
 * The redaction guard of the detailed diagnostic log: a whole run through each real provider client, logged at TRACE
 * into {@code bookloom-trace.log}, writes the book's text only on TRACE lines and nothing that looks like a
 * credential — even when the provider answers with credential-bearing headers a careless client might echo.
 *
 * <p>The clients send no credential of their own (none is configured anywhere yet), so the provider's headers are the
 * credential-shaped input this run can carry; the patterns would catch a request header's value or a key in a URL
 * too. A header's name alone may be logged — the HTTP seam names the headers a reply carried — and is not a secret.
 */
class TraceRedactionTest {

    private static final String LEAKED_TOKEN = "sk-leak-0123456789abcdef";
    // A credential's value: after a credential's name and a separator, after "Bearer", or the token itself. A header
    // NAME alone (the client logs which headers a reply carried) is not a secret and is not matched.
    private static final Pattern CREDENTIAL = Pattern.compile("(?i)bearer\\s+\\S"
            + "|(authorization|api[-_]?key|access[-_]?token|secret)\\s*[:=]\\s*\\S"
            + "|" + Pattern.quote(LEAKED_TOKEN));
    private static final String BOOK_TEXT = "He opened the";
    private static final String STATUS_REPLY = "{\"status\":\"ok\"}";
    private static final String TARGET_REPLY = "{\"target\":\"Він відчинив ⟦g0⟧старі⟦g1⟧ двері.\"}";
    private static final String STATUS_PROMPT = "Return exactly one JSON object";

    private final WireMockServer server =
            new WireMockServer(WireMockConfiguration.options().dynamicPort());

    @TempDir
    private Path tempDir;

    @BeforeEach
    void startServer() {
        server.start();
    }

    @AfterEach
    void stopServerAndLogging() {
        server.stop();
        if (LoggerFactory.getILoggerFactory() instanceof LoggerContext context) {
            context.stop();
            context.reset();
        }
    }

    @Test
    void run_openAiCompatibleAtTrace_writesBookTextOnlyOnTraceLinesAndNoCredential() throws IOException {
        server.stubFor(get(urlEqualTo("/v1/models"))
                .willReturn(leaky("{\"object\":\"list\",\"data\":[{\"id\":\"qwen3\",\"object\":\"model\"}]}")));
        server.stubFor(post(urlEqualTo("/v1/chat/completions")).willReturn(leaky(openAi(TARGET_REPLY))));
        server.stubFor(post(urlEqualTo("/v1/chat/completions"))
                .withRequestBody(containing(STATUS_PROMPT))
                .atPriority(1)
                .willReturn(leaky(openAi(STATUS_REPLY))));

        final List<String> trace = runAtTrace(
                List.of("--provider", "openai-compatible", "--base-url", server.baseUrl() + "/v1", "--model", "qwen3"));

        assertRedacted(trace);
    }

    @Test
    void run_ollamaAtTrace_writesBookTextOnlyOnTraceLinesAndNoCredential() throws IOException {
        server.stubFor(get(urlEqualTo("/api/version")).willReturn(leaky("{\"version\":\"0.6.0\"}")));
        server.stubFor(get(urlEqualTo("/api/tags")).willReturn(leaky("{\"models\":[{\"name\":\"gemma4:e4b\"}]}")));
        server.stubFor(post(urlEqualTo("/api/show")).willReturn(leaky("{\"model_info\":{}}")));
        server.stubFor(post(urlEqualTo("/api/chat")).willReturn(leaky(ollama(TARGET_REPLY))));
        server.stubFor(post(urlEqualTo("/api/chat"))
                .withRequestBody(containing(STATUS_PROMPT))
                .atPriority(1)
                .willReturn(leaky(ollama(STATUS_REPLY))));

        final List<String> trace =
                runAtTrace(List.of("--provider", "ollama", "--base-url", server.baseUrl(), "--model", "gemma4:e4b"));

        assertRedacted(trace);
    }

    private static void assertRedacted(final List<String> trace) {
        assertThat(trace)
                .as("the run reached the detailed log at TRACE, book text included")
                .anyMatch(line -> line.contains(" TRACE ") && line.contains(BOOK_TEXT));
        assertThat(trace)
                .as("book text appears on TRACE lines only")
                .filteredOn(line -> line.toLowerCase(Locale.ROOT).contains(BOOK_TEXT.toLowerCase(Locale.ROOT)))
                .allMatch(line -> line.contains(" TRACE "));
        assertThat(trace)
                .as("nothing resembling a credential is written at any level")
                .noneMatch(line -> CREDENTIAL.matcher(line).find());
    }

    private List<String> runAtTrace(final List<String> providerArguments) throws IOException {
        final Path logDir = Files.createDirectories(tempDir.resolve("logs"));
        LoggingBootstrap.configure(
                logDir,
                false,
                new ResolvedLogLevel(Level.INFO, ResolvedLogLevel.Source.DEFAULT, null),
                new ResolvedTraceFile(true, ResolvedLogLevel.Source.ENVIRONMENT, null),
                () -> "# header");
        final Path source = Files.writeString(tempDir.resolve("Book.md"), BOOK_TEXT + " *old* door.\n");
        final StartupContext startup = new StartupContext(
                AppPaths.of(tempDir.resolve("data"), logDir),
                AppEnvironment.DEV,
                ReviewModeResolver.resolve(name -> null, name -> null));
        final ByteArrayOutputStream console = new ByteArrayOutputStream();
        final List<String> arguments = java.util.stream.Stream.concat(
                        java.util.stream.Stream.of(source.toString()), providerArguments.stream())
                .toList();

        Guice.createInjector(new CoreModules(startup))
                .getInstance(TranslateCommand.class)
                .run(arguments, new PrintStream(console, true, StandardCharsets.UTF_8));

        if (LoggerFactory.getILoggerFactory() instanceof LoggerContext context) {
            context.stop();
        }
        return Files.readAllLines(logDir.resolve("bookloom-trace.log"));
    }

    /** A JSON reply carrying the credential-shaped headers a careless client might log. */
    private static ResponseDefinitionBuilder leaky(final String body) {
        return aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withHeader("Authorization", "Bearer " + LEAKED_TOKEN)
                .withHeader("X-Api-Key", LEAKED_TOKEN)
                .withBody(body);
    }

    private static String openAi(final String content) {
        return "{\"model\":\"qwen3\",\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":" + quote(content)
                + "},\"finish_reason\":\"stop\"}]}";
    }

    private static String ollama(final String content) {
        return "{\"model\":\"gemma4:e4b\",\"message\":{\"role\":\"assistant\",\"content\":" + quote(content)
                + "},\"done\":true,\"done_reason\":\"stop\"}";
    }

    private static String quote(final String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
