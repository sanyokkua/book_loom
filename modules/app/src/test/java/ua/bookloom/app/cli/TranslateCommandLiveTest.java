package ua.bookloom.app.cli;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Guice;
import com.google.inject.Injector;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.Document;
import ua.bookloom.api.document.DocumentPort;
import ua.bookloom.api.document.Segment;
import ua.bookloom.app.CoreModules;
import ua.bookloom.app.StartupContext;
import ua.bookloom.util.paths.AppEnvironment;
import ua.bookloom.util.paths.AppPaths;

/** Proves the command translates a small Markdown book through each configured real local provider. */
@Tag("liveLocal")
class TranslateCommandLiveTest {

    private static final String OLLAMA_MODEL_ENV = "BOOKLOOM_LIVE_OLLAMA_MODEL";
    private static final String OLLAMA_OPENAI_MODEL_ENV = "BOOKLOOM_LIVE_OLLAMA_OPENAI_MODEL";
    private static final String LM_STUDIO_MODEL_ENV = "BOOKLOOM_LIVE_LMSTUDIO_MODEL";
    private static final String DEFAULT_OLLAMA_MODEL = "gemma4:e4b-mlx";
    private static final String DEFAULT_LM_STUDIO_MODEL = "google/gemma-4-e4b";

    @TempDir
    private Path tempDir;

    // A real native run must translate English prose into Ukrainian and restore every inline placeholder.
    @Test
    @EnabledIfEnvironmentVariable(named = "BOOKLOOM_LIVE_OLLAMA_URL", matches = ".+")
    void run_ollamaProvider_englishMarkdown_translatesToUkrainianAndRetainsInlinePlaceholders() throws IOException {
        assertTranslated(
                "ollama",
                System.getenv().getOrDefault(OLLAMA_MODEL_ENV, DEFAULT_OLLAMA_MODEL),
                environmentUrl("BOOKLOOM_LIVE_OLLAMA_URL"),
                UnaryOperator.identity());
    }

    // A real compatible run must translate English prose into Ukrainian and restore every inline placeholder.
    @Test
    @EnabledIfEnvironmentVariable(named = "BOOKLOOM_LIVE_LMSTUDIO_URL", matches = ".+")
    void run_lmStudioProvider_englishMarkdown_translatesToUkrainianAndRetainsInlinePlaceholders() throws IOException {
        assertTranslated(
                "lmstudio",
                System.getenv().getOrDefault(LM_STUDIO_MODEL_ENV, DEFAULT_LM_STUDIO_MODEL),
                environmentUrl("BOOKLOOM_LIVE_LMSTUDIO_URL"),
                origin -> origin.resolve("/v1"));
    }

    // The Ollama OpenAI shim must be exercised through the custom-provider CLI route, not the native client.
    @Test
    @EnabledIfEnvironmentVariable(named = "BOOKLOOM_LIVE_OLLAMA_OPENAI_URL", matches = ".+")
    void run_ollamaOpenAiCompatibleProvider_englishMarkdown_translatesToUkrainianAndRetainsInlinePlaceholders()
            throws IOException {
        assertTranslated(
                "openai-compatible",
                System.getenv().getOrDefault(OLLAMA_OPENAI_MODEL_ENV, DEFAULT_OLLAMA_MODEL),
                environmentUrl("BOOKLOOM_LIVE_OLLAMA_OPENAI_URL"),
                UnaryOperator.identity());
    }

    private void assertTranslated(
            final String provider, final String model, final URI endpoint, final UnaryOperator<URI> baseUrl)
            throws IOException {
        final Path source = Files.writeString(tempDir.resolve("Book.md"), """
                The *first* marked paragraph.

                The *second* marked paragraph.

                The *third* marked paragraph.
                """);
        final Injector injector = Guice.createInjector(new CoreModules(startup()));
        final Path destination = tempDir.resolve("Book.uk.md");
        final CommandRun run = runCommand(injector, source, provider, model, baseUrl.apply(endpoint));

        assertThat(run.exit()).isEqualTo(0);
        assertThat(run.console()).contains("connection: ok", "models: ok", "inference: ok (structured output:");
        assertThat(run.console()).contains("Completed: " + destination + " (accepted=3, flagged=0)");
        assertThat(Files.readString(destination))
                .contains("*")
                .containsPattern("[\\p{IsCyrillic}]")
                .doesNotContain("The *first* marked paragraph.", "⟦g", "⟧");
        assertReopenedStructure(injector.getInstance(DocumentPort.class), source, destination);
    }

    private static CommandRun runCommand(
            final Injector injector, final Path source, final String provider, final String model, final URI baseUrl) {
        final ByteArrayOutputStream console = new ByteArrayOutputStream();
        final int exit = injector.getInstance(TranslateCommand.class)
                .run(
                        List.of(
                                source.toString(),
                                "--provider",
                                provider,
                                "--model",
                                model,
                                "--base-url",
                                baseUrl.toString(),
                                "--from",
                                "en",
                                "--to",
                                "uk",
                                "--timeout",
                                "180"),
                        new PrintStream(console, true, StandardCharsets.UTF_8));
        return new CommandRun(exit, console.toString(StandardCharsets.UTF_8));
    }

    private StartupContext startup() throws IOException {
        return new StartupContext(
                AppPaths.of(tempDir.resolve("data"), Files.createDirectories(tempDir.resolve("logs"))),
                AppEnvironment.DEV);
    }

    private static URI environmentUrl(final String variable) {
        return URI.create(Objects.requireNonNull(System.getenv(variable), variable));
    }

    private static void assertReopenedStructure(
            final DocumentPort documents, final Path source, final Path destination) {
        final Document sourceDocument = dataOf(documents.open(source));
        try {
            final Document outputDocument = dataOf(documents.open(destination));
            try {
                assertThat(segments(outputDocument)).hasSize(3);
                assertThat(placeholderFragments(outputDocument)).isEqualTo(placeholderFragments(sourceDocument));
            } finally {
                assertThat(dataOf(documents.close(outputDocument))).isTrue();
            }
        } finally {
            assertThat(dataOf(documents.close(sourceDocument))).isTrue();
        }
    }

    private static List<Segment> segments(final Document document) {
        return document.units().stream()
                .flatMap(unit -> unit.segments().stream())
                .toList();
    }

    private static List<List<String>> placeholderFragments(final Document document) {
        return segments(document).stream()
                .map(segment -> List.copyOf(segment.placeholders().values()))
                .toList();
    }

    private static <T> T dataOf(final Result<T> result) {
        assertThat(result.isOk()).as("document result: " + result.error()).isTrue();
        return Objects.requireNonNull(result.data(), "successful result data");
    }

    private record CommandRun(int exit, String console) {}
}
