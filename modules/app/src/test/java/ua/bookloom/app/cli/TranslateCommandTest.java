package ua.bookloom.app.cli;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Guice;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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
                        "Usage: translate <book> [--to <lang>] [--from <lang>] [--overwrite]");
        assertThat(children()).isEqualTo(before);
    }

    private TranslateCommand command() throws IOException {
        final Path logDir = Files.createDirectories(tempDir.resolve("test-logs"));
        final StartupContext startup = new StartupContext(AppPaths.of(tempDir, logDir), AppEnvironment.DEV);
        return Guice.createInjector(new CoreModules(startup)).getInstance(TranslateCommand.class);
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
}
