package ua.bookloom.app.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.app.cli.TranslateCommandTestFakes.RecordingChatModelFactory;
import static ua.bookloom.app.cli.TranslateCommandTestFakes.RecordingProviderConfigs;
import static ua.bookloom.app.cli.TranslateCommandTestFakes.ScriptedProviderVerifier;

import com.google.inject.Guice;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.llm.ChatModelFactory;
import ua.bookloom.api.llm.ModelSelection;
import ua.bookloom.api.llm.ProviderConfigs;
import ua.bookloom.api.llm.ProviderKind;
import ua.bookloom.api.llm.ProviderVerifier;
import ua.bookloom.api.llm.StageOutcome;
import ua.bookloom.api.llm.StageStatus;
import ua.bookloom.api.llm.VerificationPolicy;
import ua.bookloom.api.llm.VerificationReport;
import ua.bookloom.api.llm.VerificationStage;
import ua.bookloom.api.pipeline.TranslationEngine;
import ua.bookloom.app.CoreModules;
import ua.bookloom.app.StartupContext;
import ua.bookloom.util.paths.AppEnvironment;
import ua.bookloom.util.paths.AppPaths;

class TranslateCommandTest {

    private static final String MARKDOWN = "He opened the *old* door.\n";

    @TempDir
    private Path tempDir;

    // WHEN one Markdown book is given without a target language, THEN the command writes one uppercase report line
    // and a same-format Ukrainian output beside the source.
    @Test
    void run_markdownWithDefaultTarget_writesOutputAndExactlyOneReportLine() throws IOException {
        final Path source = writeMarkdown(tempDir.resolve("Book.md"));
        final ByteArrayOutputStream console = new ByteArrayOutputStream();

        final int exit = command().run(List.of(source.toString()), printStream(console));

        final Path destination = tempDir.resolve("Book.uk.md");
        assertThat(exit).isEqualTo(0);
        assertThat(Files.readString(destination)).isEqualTo("HE OPENED THE *OLD* DOOR.\n");
        assertThat(consoleText(console).lines())
                .containsExactly("Completed: " + destination + " (accepted=1, flagged=0)");
    }

    // Pseudo remains offline: it creates the fixed model but neither registers nor verifies a provider.
    @Test
    void run_pseudoProvider_skipsRegistrationAndPreflight() throws IOException {
        final RecordingProviderConfigs configs = new RecordingProviderConfigs();
        final ScriptedProviderVerifier verifier = new ScriptedProviderVerifier(successfulPreflight());
        final RecordingChatModelFactory models = new RecordingChatModelFactory();
        final ByteArrayOutputStream console = new ByteArrayOutputStream();

        final int exit = command(configs, verifier, models)
                .run(List.of(writeMarkdown(tempDir.resolve("Book.md")).toString()), printStream(console));

        assertThat(exit).isEqualTo(0);
        assertThat(consoleText(console).lines()).hasSize(1);
        assertThat(configs.registered()).isEmpty();
        assertThat(verifier.selections()).isEmpty();
        assertThat(models.selections()).containsExactly(new ModelSelection("pseudo", "uppercase"));
    }

    // A listed local model registers its unchanged preset, reports all preflight stages, then translates.
    @Test
    void run_ollamaProviderWithListedModel_registersPreflightsAndTranslates() throws IOException {
        final RecordingProviderConfigs configs = new RecordingProviderConfigs();
        final ScriptedProviderVerifier verifier = new ScriptedProviderVerifier(successfulPreflight());
        final RecordingChatModelFactory models = new RecordingChatModelFactory();
        final Path source = writeMarkdown(tempDir.resolve("Book.md"));
        final ByteArrayOutputStream console = new ByteArrayOutputStream();

        final int exit = command(configs, verifier, models)
                .run(
                        List.of(source.toString(), "--provider", "ollama", "--model", "gemma4:e4b-mlx"),
                        printStream(console));

        assertThat(exit).isEqualTo(0);
        assertThat(configs.registered()).containsExactly(configs.ollama());
        assertThat(verifier.selections()).containsExactly(new ModelSelection("ollama", "gemma4:e4b-mlx"));
        assertThat(verifier.policies()).containsExactly(VerificationPolicy.PREFLIGHT);
        assertThat(consoleText(console).lines())
                .containsExactly(
                        "connection: ok",
                        "models: ok",
                        "inference: ok (structured output: supported)",
                        "Completed: " + tempDir.resolve("Book.uk.md") + " (accepted=1, flagged=0)");
    }

    // Explicit endpoint and timeout flags alter only the configuration registered for this command run.
    @Test
    void run_ollamaOverrides_registersConfiguredUrlAndTimeout() throws IOException {
        final RecordingProviderConfigs configs = new RecordingProviderConfigs();
        final Path source = writeMarkdown(tempDir.resolve("Book.md"));

        final int exit = command(
                        configs, new ScriptedProviderVerifier(successfulPreflight()), new RecordingChatModelFactory())
                .run(
                        List.of(
                                source.toString(),
                                "--provider",
                                "ollama",
                                "--model",
                                "gemma4:e4b-mlx",
                                "--base-url",
                                "http://10.0.0.5:11434",
                                "--timeout",
                                "30"),
                        printStream(new ByteArrayOutputStream()));

        assertThat(exit).isEqualTo(0);
        assertThat(configs.registered().getFirst().baseUrl()).isEqualTo(URI.create("http://10.0.0.5:11434"));
        assertThat(configs.registered().getFirst().requestTimeout()).isEqualTo(Duration.ofSeconds(30));
    }

    // A URL-only OpenAI-compatible run registers the supplied endpoint with the compatible wire dialect.
    @Test
    void run_openAiCompatibleProvider_registersCustomConfiguration() throws IOException {
        final RecordingProviderConfigs configs = new RecordingProviderConfigs();
        final Path source = writeMarkdown(tempDir.resolve("Book.md"));

        final int exit = command(
                        configs, new ScriptedProviderVerifier(successfulPreflight()), new RecordingChatModelFactory())
                .run(
                        List.of(
                                source.toString(),
                                "--provider",
                                "openai-compatible",
                                "--base-url",
                                "http://localhost:8080/v1",
                                "--model",
                                "qwen3"),
                        printStream(new ByteArrayOutputStream()));

        assertThat(exit).isEqualTo(0);
        assertThat(configs.registered()).singleElement().satisfies(config -> {
            assertThat(config.id()).isEqualTo("openai-compatible");
            assertThat(config.kind()).isEqualTo(ProviderKind.OPENAI_COMPATIBLE);
            assertThat(config.baseUrl()).isEqualTo(URI.create("http://localhost:8080/v1"));
        });
    }

    // A failed connection is reported before model construction and leaves no translated file behind.
    @Test
    void run_failedConnection_printsStageFailureAndDoesNotWriteOutput() throws IOException {
        final AppError error =
                AppError.of(ErrorCode.unreachable, "Provider unreachable", "The provider is not running.");
        final VerificationReport report = new VerificationReport(
                List.of(new StageOutcome(VerificationStage.CONNECTION, StageStatus.FAILED, error, null)));
        final RecordingChatModelFactory models = new RecordingChatModelFactory();
        final Path source = writeMarkdown(tempDir.resolve("Book.md"));
        final ByteArrayOutputStream console = new ByteArrayOutputStream();

        final int exit = command(new RecordingProviderConfigs(), new ScriptedProviderVerifier(report), models)
                .run(
                        List.of(source.toString(), "--provider", "ollama", "--model", "gemma4:e4b-mlx"),
                        printStream(console));

        assertThat(exit).isEqualTo(1);
        assertThat(consoleText(console).lines())
                .containsExactly("connection: failed - Provider unreachable", "The provider is not running.");
        assertThat(models.selections()).isEmpty();
        assertThat(tempDir.resolve("Book.uk.md")).doesNotExist();
    }

    // A failed model-list stage stops after its stage line and before any model request can be built.
    @Test
    void run_failedModels_printsStageFailureAndDoesNotWriteOutput() throws IOException {
        final AppError error =
                AppError.of(ErrorCode.modelUnavailable, "Model unavailable", "The selected model is absent.");
        final VerificationReport report = new VerificationReport(List.of(
                new StageOutcome(VerificationStage.CONNECTION, StageStatus.PASSED, null, null),
                new StageOutcome(VerificationStage.MODELS, StageStatus.FAILED, error, null)));
        final RecordingChatModelFactory models = new RecordingChatModelFactory();
        final Path source = writeMarkdown(tempDir.resolve("Book.md"));
        final ByteArrayOutputStream console = new ByteArrayOutputStream();

        final int exit = command(new RecordingProviderConfigs(), new ScriptedProviderVerifier(report), models)
                .run(
                        List.of(source.toString(), "--provider", "ollama", "--model", "gemma4:e4b-mlx"),
                        printStream(console));

        assertThat(exit).isEqualTo(1);
        assertThat(consoleText(console).lines())
                .containsExactly(
                        "connection: ok", "models: failed - Model unavailable", "The selected model is absent.");
        assertThat(models.selections()).isEmpty();
        assertThat(tempDir.resolve("Book.uk.md")).doesNotExist();
    }

    // WHEN a compound FB2 zip is given, THEN the command inserts the target before the matched compound suffix.
    @Test
    void run_compoundFb2Zip_preservesTheCompoundSuffix() throws IOException {
        final Path source = writeZippedFb2(tempDir.resolve("Book.fb2.zip"));

        final int exit = command().run(List.of(source.toString()), printStream(new ByteArrayOutputStream()));

        assertThat(exit).isEqualTo(0);
        assertThat(tempDir.resolve("Book.uk.fb2.zip")).isRegularFile();
        assertThat(tempDir.resolve("Book.uk.fb2")).doesNotExist();
    }

    // WHEN the default destination already exists without overwrite, THEN the command fails and leaves its bytes.
    @Test
    void run_existingDestinationWithoutOverwrite_returnsFailureAndPreservesDestination() throws IOException {
        final Path source = writeMarkdown(tempDir.resolve("Book.md"));
        final Path destination = tempDir.resolve("Book.uk.md");
        Files.writeString(destination, "KEEP THIS FILE\n", StandardCharsets.UTF_8);
        final ByteArrayOutputStream console = new ByteArrayOutputStream();

        final int exit = command().run(List.of(source.toString()), printStream(console));

        assertThat(exit).isEqualTo(1);
        assertThat(Files.readString(destination)).isEqualTo("KEEP THIS FILE\n");
        assertThat(consoleText(console)).contains("This destination already exists");
    }

    // WHEN an EPUB has non-archive bytes, THEN the command emits only the document error and returns exit code 1.
    @Test
    void run_corruptEpub_reportsUserFacingErrorWithoutTechnicalCause() throws IOException {
        final Path source = Files.writeString(tempDir.resolve("Book.epub"), "not a zip", StandardCharsets.UTF_8);
        final ByteArrayOutputStream console = new ByteArrayOutputStream();

        final int exit = command().run(List.of(source.toString()), printStream(console));

        assertThat(exit).isEqualTo(1);
        assertThat(consoleText(console))
                .contains("This file could not be opened")
                .contains("This file could not be read as a book")
                .doesNotContain("ZipException")
                .doesNotContain("java.");
        assertThat(tempDir.resolve("Book.uk.epub")).doesNotExist();
        assertThat(tempDir.resolve(".Book.uk.epub")).doesNotExist();
    }

    // WHEN the arguments are invalid, THEN the command prints the parser's reason and then the usage, exits with 2 and
    // writes nothing; without the reason, a book path a shell split at its spaces looks like any other mistake.
    @ParameterizedTest(name = "{0}")
    @ValueSource(
            strings = {
                "unknown-option",
                "folder",
                "missing-path",
                "nonexistent-path",
                "unsupported-type",
                "invalid-target",
                "invalid-source",
                "missing-target",
                "duplicate-target",
                "duplicate-overwrite",
                "extra-book"
            })
    void run_invalidArguments_printUsageExitTwoAndCreatesNoOutput(String invalidCase) throws IOException {
        final List<String> arguments = argumentsFor(invalidCase);
        final TranslateCommand command = command();
        final Map<Path, String> before = children();
        final ByteArrayOutputStream console = new ByteArrayOutputStream();

        final int exit = command.run(arguments, printStream(console));

        assertThat(exit).isEqualTo(2);
        assertThat(consoleText(console).lines())
                .containsExactly(
                        "Invalid command arguments: " + invalidReason(invalidCase),
                        "Usage: translate <book> [--to <lang>] [--from <lang>] [--overwrite] "
                                + "[--provider pseudo|ollama|lmstudio|openai-compatible] [--model <id>] "
                                + "[--base-url <url>] [--timeout <seconds>]");
        assertThat(children()).isEqualTo(before);
    }

    private TranslateCommand command() throws IOException {
        final Path logDir = Files.createDirectories(tempDir.resolve("test-logs"));
        final StartupContext startup = new StartupContext(AppPaths.of(tempDir, logDir), AppEnvironment.DEV);
        return Guice.createInjector(new CoreModules(startup)).getInstance(TranslateCommand.class);
    }

    private TranslateCommand command(ProviderConfigs configs, ProviderVerifier verifier, ChatModelFactory models)
            throws IOException {
        final Path logDir = Files.createDirectories(tempDir.resolve("test-logs"));
        final StartupContext startup = new StartupContext(AppPaths.of(tempDir, logDir), AppEnvironment.DEV);
        final TranslationEngine engine =
                Guice.createInjector(new CoreModules(startup)).getInstance(TranslationEngine.class);
        return new TranslateCommand(engine, models, configs, verifier);
    }

    private List<String> argumentsFor(String invalidCase) throws IOException {
        final Path source = writeMarkdown(tempDir.resolve("Book.md"));
        return switch (invalidCase) {
            case "unknown-option" -> List.of(source.toString(), "--bogus");
            case "folder" ->
                List.of(Files.createDirectories(tempDir.resolve("folder")).toString());
            case "missing-path" -> List.of();
            case "unsupported-type" ->
                List.of(Files.writeString(tempDir.resolve("Book.pdf"), "not a supported book", StandardCharsets.UTF_8)
                        .toString());
            case "invalid-target" -> List.of(source.toString(), "--to", "../x");
            case "invalid-source" -> List.of(source.toString(), "--from", "../x");
            case "missing-target" -> List.of(source.toString(), "--to");
            case "duplicate-target" -> List.of(source.toString(), "--to", "uk", "--to", "de");
            case "duplicate-overwrite" -> List.of(source.toString(), "--overwrite", "--overwrite");
            case "extra-book" ->
                List.of(
                        source.toString(),
                        writeMarkdown(tempDir.resolve("Other.md")).toString());
            case "nonexistent-path" -> List.of(tempDir.resolve("Missing.md").toString());
            default -> throw new IllegalArgumentException("unknown test case: " + invalidCase);
        };
    }

    private static String invalidReason(String invalidCase) {
        return switch (invalidCase) {
            case "unknown-option" -> "unknown option";
            case "folder", "nonexistent-path" -> "book path is not a regular file";
            case "missing-path" -> "book path is missing";
            case "unsupported-type" -> "book type is not supported";
            case "invalid-target" -> "target language is invalid";
            case "invalid-source" -> "source language is invalid";
            case "missing-target" -> "--to needs a language";
            case "duplicate-target" -> "--to was repeated";
            case "duplicate-overwrite" -> "--overwrite was repeated";
            case "extra-book" -> "more than one book path";
            default -> throw new IllegalArgumentException("unknown test case: " + invalidCase);
        };
    }

    private Path writeMarkdown(Path destination) throws IOException {
        return Files.writeString(destination, MARKDOWN, StandardCharsets.UTF_8);
    }

    private Path writeZippedFb2(Path destination) throws IOException {
        final String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<FictionBook xmlns=\"http://www.gribuser.ru/xml/fictionbook/2.0\">"
                + "<description><title-info><genre>prose</genre><book-title>Book</book-title></title-info></description>"
                + "<body><section><p>Hello.</p></section></body></FictionBook>";
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(destination))) {
            zip.putNextEntry(new ZipEntry("Book.fb2"));
            zip.write(xml.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return destination;
    }

    /** Maps each entry of the temporary folder to its content, so a changed file counts, not only a new one. */
    private Map<Path, String> children() throws IOException {
        try (Stream<Path> paths = Files.list(tempDir)) {
            return paths.collect(Collectors.toMap(path -> path, TranslateCommandTest::contentOf));
        }
    }

    private static String contentOf(Path path) {
        try {
            return Files.isDirectory(path) ? "<directory>" : Files.readString(path, StandardCharsets.ISO_8859_1);
        } catch (IOException cause) {
            throw new UncheckedIOException(cause);
        }
    }

    private static PrintStream printStream(ByteArrayOutputStream output) {
        return new PrintStream(output, true, StandardCharsets.UTF_8);
    }

    private static String consoleText(ByteArrayOutputStream output) {
        return output.toString(StandardCharsets.UTF_8);
    }

    private static VerificationReport successfulPreflight() {
        return new VerificationReport(List.of(
                new StageOutcome(VerificationStage.CONNECTION, StageStatus.PASSED, null, null),
                new StageOutcome(VerificationStage.MODELS, StageStatus.PASSED, null, null),
                new StageOutcome(
                        VerificationStage.INFERENCE, StageStatus.PASSED, null, "structured output: supported")));
    }
}
