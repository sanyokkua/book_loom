package ua.bookloom.pipeline.export;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.pipeline.export.ExportJobFixture.error;
import static ua.bookloom.pipeline.export.ExportJobFixture.ok;
import static ua.bookloom.pipeline.export.ExportJobFixture.request;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.pipeline.ExportReport;
import ua.bookloom.api.pipeline.SideFile;
import ua.bookloom.pipeline.TestBooks;
import ua.bookloom.pipeline.export.BookExporterTestSupport.RecordingDocumentPort;

/**
 * A destination the book cannot be written to, or an occupied book or side-file path without permission to replace
 * it, is refused before anything slow happens — no consistency pass, no read, no write.
 */
class ExportJobDestinationTest {

    @TempDir
    private Path tempDir;

    private ExportJobFixture fixture;

    @BeforeEach
    void setUp() {
        fixture = new ExportJobFixture();
    }

    // A translation is never written over the book it was made from.
    @Test
    void newExport_sourceItself_returnsValidation() {
        final Path source = TestBooks.markdown(tempDir.resolve("Book.md"), "One.");
        final String id = fixture.importBook(source, "en");

        final Result<ExportReport> result = fixture.export(request(id, source, true));

        assertThat(error(result).code()).isEqualTo(ErrorCode.validation);
        assertThat(ExportJobFixture.read(source)).isEqualTo("One.");
    }

    // A zipped FB2 book is written back in its container, so a plain FB2 name is another file type.
    @Test
    void newExport_plainFb2NameForZippedFb2_returnsValidation() {
        final String id =
                fixture.importBook(TestBooks.zippedFb2(tempDir.resolve("Kobzar.fb2.zip"), List.of("One."), "en"), "en");

        final Result<ExportReport> result = fixture.export(request(id, tempDir.resolve("Kobzar.uk.fb2"), false));

        assertThat(error(result).code()).isEqualTo(ErrorCode.validation);
        assertThat(tempDir.resolve("Kobzar.uk.fb2")).doesNotExist();
    }

    // .md and .markdown are both Markdown.
    @Test
    void run_markdownSuffixForMdSource_writesTheBook() {
        final String id = fixture.importBook(TestBooks.markdown(tempDir.resolve("Book.md"), "One."), "en");
        final Path destination = tempDir.resolve("Book.uk.markdown");

        ok(fixture.export(request(id, destination, false)));

        assertThat(ExportJobFixture.read(destination)).isEqualTo("One.");
    }

    // An existing book without permission to replace it is refused before the source is read or anything written.
    @Test
    void run_existingBookWithoutOverwrite_returnsValidationBeforeAnyWrite() throws IOException {
        final String id = fixture.importBook(TestBooks.markdown(tempDir.resolve("Book.md"), "One."), "en");
        final Path destination = TestBooks.markdown(tempDir.resolve("Book.uk.md"), "Existing.");
        final RecordingDocumentPort port = new RecordingDocumentPort(fixture.documents());

        final Result<ExportReport> result =
                ExportJobFixture.export(fixture.serviceOver(port), request(id, destination, false));

        assertThat(error(result).code()).isEqualTo(ErrorCode.validation);
        assertThat(port.openedDocuments()).isEmpty();
        assertThat(port.writtenDocuments()).isEmpty();
        assertThat(Files.readString(destination)).isEqualTo("Existing.");
    }

    // A glossary file someone edited by hand is protected like the book, even when the book's own path is free.
    @Test
    void run_occupiedChosenSideFileWithoutOverwrite_returnsValidationBeforeAnyWrite() throws IOException {
        final String id = fixture.importBook(
                TestBooks.epub(tempDir.resolve("Frankenstein.epub"), List.of(List.of("One.")), "en"), "en");
        final Path glossary = Files.writeString(tempDir.resolve("Frankenstein.uk.glossary.csv"), "term\n");
        final RecordingDocumentPort port = new RecordingDocumentPort(fixture.documents());
        final Path destination = tempDir.resolve("Frankenstein.uk.epub");

        final Result<ExportReport> result = ExportJobFixture.export(
                fixture.serviceOver(port), request(id, destination, false, Set.of(SideFile.GLOSSARY_CSV)));

        assertThat(error(result).code()).isEqualTo(ErrorCode.validation);
        assertThat(error(result).message()).contains("Frankenstein.uk.glossary.csv");
        assertThat(port.writtenDocuments()).isEmpty();
        assertThat(destination).doesNotExist();
        assertThat(Files.readString(glossary)).isEqualTo("term\n");
    }

    // An occupied side-file path that was not chosen is no reason to refuse.
    @Test
    void run_occupiedSideFileNotChosen_writesTheBook() throws IOException {
        final String id = fixture.importBook(
                TestBooks.epub(tempDir.resolve("Frankenstein.epub"), List.of(List.of("One.")), "en"), "en");
        Files.writeString(tempDir.resolve("Frankenstein.uk.glossary.csv"), "term\n");
        final Path destination = tempDir.resolve("Frankenstein.uk.epub");

        final ExportReport report = ok(fixture.export(request(id, destination, false)));

        assertThat(report.sideFiles()).isEmpty();
        assertThat(destination).exists();
    }
}
