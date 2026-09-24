package ua.bookloom.app.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.LoggerContext;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import ua.bookloom.util.paths.AppPaths;

class TranslateLauncherTest {

    private static final String MARKDOWN = "He opened the *old* door.\n";
    private static final String USAGE = "Usage: translate <book> [--to <lang>] [--from <lang>] [--overwrite] "
            + "[--provider pseudo|ollama|lmstudio|openai-compatible] [--model <id>] "
            + "[--base-url <url>] [--timeout <seconds>]";

    @TempDir
    private Path tempDir;

    @AfterEach
    void restoreLogging() {
        if (LoggerFactory.getILoggerFactory() instanceof LoggerContext context) {
            context.stop();
            context.reset();
        }
    }

    // WHEN the single-instance lock is held, THEN the launcher returns 1 without creating a translated output.
    @Test
    void run_lockAlreadyHeld_reportsOneUserFacingLineAndDoesNotTranslate() throws IOException {
        final Path source = writeMarkdown(tempDir.resolve("Book.md"));
        final Path dataDir = Files.createDirectories(tempDir.resolve("data"));
        final Path lockFile = dataDir.resolve(AppPaths.LOCK_FILE_NAME);
        final ByteArrayOutputStream console = new ByteArrayOutputStream();

        final SingleInstanceLock holder =
                Objects.requireNonNull(SingleInstanceLock.acquire(lockFile).data());
        try (holder) {
            final int exit = TranslateLauncher.run(
                    List.of(source.toString()), environment(dataDir), emptyProperties(), printStream(console));

            assertThat(exit).isEqualTo(1);
            assertThat(holder).isNotNull();
            assertThat(consoleText(console))
                    .contains("BookLoom is already running")
                    .contains("Another copy of BookLoom is already open")
                    .doesNotContain("details")
                    .doesNotContain("java.");
        }

        assertThat(tempDir.resolve("Book.uk.md")).doesNotExist();
    }

    // WHEN a pre-log directory preparation fails, THEN the launcher prints only the title and message and returns 1.
    @Test
    void run_applicationDirectoryCannotBePrepared_reportsOneUserFacingLineBeforeLogging() throws IOException {
        final Path source = writeMarkdown(tempDir.resolve("Book.md"));
        final Path blocker = Files.writeString(tempDir.resolve("blocker"), "not a directory", StandardCharsets.UTF_8);
        final ByteArrayOutputStream console = new ByteArrayOutputStream();

        final int exit = TranslateLauncher.run(
                List.of(source.toString()),
                environment(blocker.resolve("data")),
                emptyProperties(),
                printStream(console));

        assertThat(exit).isEqualTo(1);
        assertThat(consoleText(console))
                .contains("Cannot create application folders")
                .contains("could not create its data or log folder")
                .doesNotContain("IOException")
                .doesNotContain("java.");
    }

    // WHEN the launcher runs at TRACE, THEN the file-only log follows the segment from prompt through completion.
    @Test
    void run_traceLevel_writesLifecycleDecisionAndBookTextOnlyToTheLogFile() throws IOException {
        final Path source = writeMarkdown(tempDir.resolve("Book.md"));
        final Path dataDir = tempDir.resolve("trace-data");
        final ByteArrayOutputStream console = new ByteArrayOutputStream();
        final List<String> arguments = List.of(source.toString(), "--to", "uk", "--from", "en", "--overwrite");

        final int exit = TranslateLauncher.run(
                arguments, environment(dataDir, "TRACE"), emptyProperties(), printStream(console));
        final List<String> logLines = readLog(dataDir.resolve("logs/bookloom.log"));

        assertThat(exit).isEqualTo(0);
        assertThat(consoleText(console).lines()).hasSize(1);
        assertLauncherInfo(logLines, source, dataDir);
        assertParserSuccess(logLines, source);
        assertCommandCompletion(logLines, source);
        assertJobTrace(logLines);
    }

    private static void assertLauncherInfo(List<String> logLines, Path source, Path dataDir) {
        assertThat(logLines)
                .anyMatch(line -> atLevel(line, "INFO")
                        && line.contains("translate launcher started arguments=")
                        && line.contains(source.toString())
                        && line.contains("dataDir=" + dataDir)
                        && line.contains("logDir=" + dataDir.resolve("logs"))
                        && line.contains("level=TRACE"));
    }

    private static void assertParserSuccess(List<String> logLines, Path source) {
        assertParserOptions(logLines);
        assertParserChecks(logLines, source);
    }

    private static void assertParserOptions(List<String> logLines) {
        assertThat(logLines)
                .anyMatch(line -> atLevel(line, "INFO")
                        && line.contains("translate command arguments=")
                        && line.contains("--from"));
        assertThat(logLines)
                .anyMatch(line -> atLevel(line, "DEBUG")
                        && line.contains("translate parser entry arguments=")
                        && line.contains("--overwrite"));
        assertThat(logLines)
                .anyMatch(line -> atLevel(line, "DEBUG")
                        && line.contains("translate parser option=--to value=uk outcome=accepted"));
        assertThat(logLines)
                .anyMatch(line -> atLevel(line, "DEBUG")
                        && line.contains("translate parser option=--from value=en outcome=accepted"));
        assertThat(logLines)
                .anyMatch(line -> atLevel(line, "DEBUG")
                        && line.contains("translate parser option=--overwrite outcome=accepted"));
    }

    private static void assertParserChecks(List<String> logLines, Path source) {
        assertThat(logLines)
                .anyMatch(line -> atLevel(line, "DEBUG")
                        && line.contains("translate parser check=regular-file")
                        && line.contains("outcome=passed"));
        assertThat(logLines)
                .anyMatch(line -> atLevel(line, "DEBUG")
                        && line.contains("translate parser check=format")
                        && line.contains("format=MARKDOWN")
                        && line.contains("outcome=passed"));
        assertThat(logLines)
                .anyMatch(line -> atLevel(line, "DEBUG")
                        && line.contains("translate parser check=target-language value=uk outcome=passed"));
        assertThat(logLines)
                .anyMatch(line -> atLevel(line, "DEBUG")
                        && line.contains("translate parser check=source-language value=en outcome=passed"));
        assertThat(logLines)
                .anyMatch(line -> atLevel(line, "DEBUG")
                        && line.contains("translate parser validation outcome=accepted source=" + source));
    }

    private static void assertCommandCompletion(List<String> logLines, Path source) {
        assertThat(logLines)
                .anyMatch(line -> atLevel(line, "DEBUG")
                        && line.contains("translate command parsed source=" + source)
                        && line.contains("Book.uk.md")
                        && line.contains("overwrite=true"));
        assertThat(logLines)
                .anyMatch(line -> atLevel(line, "DEBUG")
                        && line.contains("translate command report state=COMPLETED")
                        && line.contains("accepted=1")
                        && line.contains("flagged=0"));
        assertThat(logLines).anyMatch(line -> atLevel(line, "INFO") && line.contains("translate command exitCode=0"));
        assertThat(logLines).anyMatch(line -> atLevel(line, "INFO") && line.contains("translate launcher exitCode=0"));
    }

    private static void assertJobTrace(List<String> logLines) {
        assertThat(logLines)
                .anyMatch(line -> atLevel(line, "INFO")
                        && line.contains("Translation job started")
                        && line.contains("format=MARKDOWN"));
        assertThat(logLines)
                .anyMatch(line -> atLevel(line, "INFO") && line.contains("Translation job ended state=COMPLETED"));
        assertThat(logLines)
                .anyMatch(line -> atLevel(line, "DEBUG") && line.contains("Book.md:0") && line.contains("ACCEPTED"));
        assertThat(logLines).anyMatch(line -> atLevel(line, "TRACE") && line.contains("Pseudo chat lastUserMessage="));
        assertThat(String.join("\n", logLines))
                .contains("He opened the ⟦g0⟧old⟦g1⟧ door.")
                .contains("HE OPENED THE ⟦g0⟧OLD⟦g1⟧ DOOR.");
    }

    // WHEN the launcher runs at INFO, THEN DEBUG diagnostics and book text are suppressed from the file log.
    @Test
    void run_infoLevel_suppressesDebugAndBookTextButKeepsLifecycle() throws IOException {
        final Path source = writeMarkdown(tempDir.resolve("Book.md"));
        final Path dataDir = tempDir.resolve("info-data");
        final ByteArrayOutputStream console = new ByteArrayOutputStream();

        final int exit = TranslateLauncher.run(
                List.of(source.toString()), environment(dataDir, "INFO"), emptyProperties(), printStream(console));
        final List<String> logLines = readLog(dataDir.resolve("logs/bookloom.log"));

        assertThat(exit).isEqualTo(0);
        assertThat(logLines).anyMatch(line -> atLevel(line, "INFO") && line.contains("Translation job started"));
        assertThat(logLines)
                .anyMatch(line -> atLevel(line, "INFO") && line.contains("Translation job ended state=COMPLETED"));
        assertThat(logLines).noneMatch(line -> line.contains("DEBUG"));
        assertThat(logLines).noneMatch(line -> line.contains(" TRACE "));
        assertThat(logLines).noneMatch(line -> line.contains("He opened"));
    }

    // WHEN an invalid parser branch is selected, THEN DEBUG and WARN explain the reason, and the console shows only the
    // reason and the usage.
    @ParameterizedTest(name = "{0}")
    @ValueSource(
            strings = {
                "unknown-option",
                "missing-target",
                "duplicate-target",
                "duplicate-overwrite",
                "folder",
                "unsupported-type",
                "invalid-target",
                "invalid-source",
                "missing-path",
                "nonexistent-path"
            })
    void run_invalidArguments_logsParserReasonAndKeepsConsoleUserFacing(String invalidCase) throws IOException {
        final Path dataDir = tempDir.resolve("invalid-" + invalidCase);
        final ByteArrayOutputStream console = new ByteArrayOutputStream();
        final int exit = TranslateLauncher.run(
                invalidArgumentsFor(invalidCase),
                environment(dataDir, "DEBUG"),
                emptyProperties(),
                printStream(console));
        final List<String> logLines = readLog(dataDir.resolve("logs/bookloom.log"));
        final String reason = invalidReason(invalidCase);

        assertThat(exit).isEqualTo(2);
        assertThat(consoleText(console))
                .isEqualTo("Invalid command arguments: " + reason + System.lineSeparator() + USAGE
                        + System.lineSeparator());
        assertThat(logLines)
                .anyMatch(line -> atLevel(line, "DEBUG") && line.contains("translate parser entry arguments="));
        assertThat(logLines)
                .anyMatch(line -> atLevel(line, "DEBUG")
                        && line.contains("translate parser branch=validation outcome=rejected reason=" + reason));
        assertThat(logLines)
                .anyMatch(line -> atLevel(line, "WARN")
                        && line.contains("Rejected translate command arguments reason=" + reason));
        assertThat(logLines).anyMatch(line -> atLevel(line, "INFO") && line.contains("translate command exitCode=2"));
        assertThat(logLines).anyMatch(line -> atLevel(line, "INFO") && line.contains("translate launcher exitCode=2"));
    }

    private Path writeMarkdown(Path destination) throws IOException {
        return Files.writeString(destination, MARKDOWN, StandardCharsets.UTF_8);
    }

    private List<String> invalidArgumentsFor(String invalidCase) throws IOException {
        final Path source = writeMarkdown(tempDir.resolve("Book-" + invalidCase + ".md"));
        return switch (invalidCase) {
            case "unknown-option" -> List.of(source.toString(), "--bogus");
            case "missing-target" -> List.of(source.toString(), "--to");
            case "duplicate-target" -> List.of(source.toString(), "--to", "uk", "--to", "de");
            case "duplicate-overwrite" -> List.of(source.toString(), "--overwrite", "--overwrite");
            case "folder" ->
                List.of(Files.createDirectories(tempDir.resolve("folder-" + invalidCase))
                        .toString());
            case "unsupported-type" ->
                List.of(Files.writeString(
                                tempDir.resolve("Book-" + invalidCase + ".pdf"),
                                "not a supported book",
                                StandardCharsets.UTF_8)
                        .toString());
            case "invalid-target" -> List.of(source.toString(), "--to", "../x");
            case "invalid-source" -> List.of(source.toString(), "--from", "../x");
            case "missing-path" -> List.of();
            case "nonexistent-path" -> List.of(tempDir.resolve("Missing.md").toString());
            default -> throw new IllegalArgumentException("unknown test case: " + invalidCase);
        };
    }

    private String invalidReason(String invalidCase) {
        return switch (invalidCase) {
            case "unknown-option" -> "unknown option";
            case "missing-target" -> "--to needs a language";
            case "duplicate-target" -> "--to was repeated";
            case "duplicate-overwrite" -> "--overwrite was repeated";
            case "folder", "nonexistent-path" -> "book path is not a regular file";
            case "unsupported-type" -> "book type is not supported";
            case "invalid-target" -> "target language is invalid";
            case "invalid-source" -> "source language is invalid";
            case "missing-path" -> "book path is missing";
            default -> throw new IllegalArgumentException("unknown test case: " + invalidCase);
        };
    }

    private static Function<String, String> environment(Path dataDir) {
        return Map.of("BOOKLOOM_DATA_DIR", dataDir.toString())::get;
    }

    private static Function<String, String> environment(Path dataDir, String level) {
        return Map.of("BOOKLOOM_DATA_DIR", dataDir.toString(), "BOOKLOOM_LOG_LEVEL", level)::get;
    }

    private static Function<String, String> emptyProperties() {
        return Map.<String, String>of()::get;
    }

    private static List<String> readLog(Path logFile) throws IOException {
        if (LoggerFactory.getILoggerFactory() instanceof LoggerContext context) {
            context.stop();
        }
        return Files.readAllLines(logFile);
    }

    private static PrintStream printStream(ByteArrayOutputStream output) {
        return new PrintStream(output, true, StandardCharsets.UTF_8);
    }

    private static String consoleText(ByteArrayOutputStream output) {
        return output.toString(StandardCharsets.UTF_8);
    }

    private static boolean atLevel(String line, String level) {
        return switch (level) {
            case "INFO" -> line.contains(" INFO  [job=");
            case "DEBUG" -> line.contains(" DEBUG [job=");
            case "TRACE" -> line.contains(" TRACE [job=");
            case "WARN" -> line.contains(" WARN  [job=");
            default -> false;
        };
    }
}
