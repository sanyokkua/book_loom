package ua.bookloom.document;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.document.DocumentServiceTestFiles.entries;
import static ua.bookloom.document.DocumentServiceTestFiles.zip;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.Document;

/** Exercises document-port diagnostics through the real service and test Logback appender. */
class DocumentServiceDiagnosticsTest {

    private static final Path TEST_LOG = Path.of("build/test-logs/test.log");

    @TempDir
    private Path tempDir;

    // WHEN suffix resolution succeeds but EPUB parsing fails, THEN the outcome names EPUB rather than unresolved.
    @Test
    void open_resolvedEpubReaderFailure_logsResolvedFormatOnOutcome() {
        final Path source = tempDir.resolve("missing-container.epub");
        zip(source, entries("mimetype", "application/epub+zip"));

        final Result<Document> result = DocumentServices.newService().open(source);

        assertThat(result.error()).hasFieldOrPropertyWithValue("code", ErrorCode.validation);
        assertThat(readTestLog()).contains("Opened document source=" + source + " format=EPUB outcome=validation");
    }

    private static String readTestLog() {
        try {
            return Files.readString(TEST_LOG);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
