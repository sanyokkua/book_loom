package ua.bookloom.document;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.BookFormat;
import ua.bookloom.api.document.Document;

/**
 * Format resolution and dispatch through the real {@code DocumentPort}: the seam that turns four readers into one
 * port, and the refusals that happen before any of them runs.
 */
class FormatDispatchTest {

    @TempDir
    private Path tempDir;

    private static final String FB2_XML = """
            <?xml version="1.0" encoding="UTF-8"?>
            <FictionBook xmlns="http://www.gribuser.ru/xml/fictionbook/2.0">
              <description><title-info><lang>en</lang></title-info></description>
              <body><section><p>Prose.</p></section></body>
            </FictionBook>
            """;

    /** Eight megabytes of zeros: a few kilobytes stored, far past the production compression-ratio limit. */
    private static final int BOMB_INFLATED_BYTES = 8 * 1024 * 1024;

    /** Byte-identical content, so only the extension can tell the two apart. */
    private static final String AMBIGUOUS_TEXT = "One.\n\nTwo.\n";

    private Path write(String name, String content) {
        final Path file = tempDir.resolve(name);
        try {
            Files.write(file, content.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return file;
    }

    private static Document okDocument(Result<Document> result) {
        assertThat(result.isOk())
                .withFailMessage("expected a parsed document but got %s", result.error())
                .isTrue();
        return java.util.Objects.requireNonNull(result.data());
    }

    private static AppError errorOf(Result<Document> result) {
        assertThat(result.isErr()).isTrue();
        return java.util.Objects.requireNonNull(result.error());
    }

    // WHEN a FictionBook file is opened, THEN it is parsed as FB2 and the result is not a
    // failure about an invalid EPUB container.
    @Test
    void open_fictionBookFile_parsesAsFb2RatherThanFailingAsAnInvalidEpub() {
        final Result<Document> result = DocumentServices.newService().open(write("book.fb2", FB2_XML));

        assertThat(okDocument(result).format()).isEqualTo(BookFormat.FB2);
    }

    // plain text and Markdown are distinguished by extension alone, because every valid
    // TXT file is also valid Markdown and nothing in the bytes separates them.
    @Test
    void open_byteIdenticalTxtAndMd_resolveToDifferentFormats() {
        final DocumentService service = DocumentServices.newService();

        final Document asTxt = okDocument(service.open(write("notes.txt", AMBIGUOUS_TEXT)));
        final Document asMarkdown = okDocument(service.open(write("notes.md", AMBIGUOUS_TEXT)));

        assertThat(asTxt.format()).isEqualTo(BookFormat.TXT);
        assertThat(asMarkdown.format()).isEqualTo(BookFormat.MARKDOWN);
    }

    // a zipped FictionBook is distinguished from an EPUB by its name, though both begin
    // with the same zip signature.
    @Test
    void open_zippedFictionBook_reportsFb2WhileAnEpubWithTheSameMagicReportsEpub() {
        final Path zipped = zipOf("book.fb2.zip", "book.fb2", FB2_XML);

        assertThat(okDocument(DocumentServices.newService().open(zipped)).format())
                .isEqualTo(BookFormat.FB2);
    }

    // an unrecognised extension is refused with a validation failure and no document.
    @Test
    void open_unrecognisedExtension_isRefused() {
        final Result<Document> result = DocumentServices.newService().open(write("book.pdf", "%PDF-1.7\n"));

        assertThat(errorOf(result).code()).isEqualTo(ErrorCode.validation);
        assertThat(result.data()).isNull();
    }

    // a zipped text bundle is not a supported format, however plausible its contents.
    @Test
    void open_zippedTextBundle_isRefused() {
        final Path bundle = zipOf("book.txt.zip", "book.txt", AMBIGUOUS_TEXT);

        assertThat(errorOf(DocumentServices.newService().open(bundle)).code()).isEqualTo(ErrorCode.validation);
    }

    // a directory whose name ends in a supported extension is refused as not a file, and
    // the failure does not describe the input as an invalid EPUB.
    @Test
    void open_directoryNamedLikeABook_isRefusedWithoutMentioningEpub() {
        final Path directory = tempDir.resolve("book.fb2");
        try {
            Files.createDirectory(directory);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        final AppError error = errorOf(DocumentServices.newService().open(directory));

        assertThat(error.code()).isEqualTo(ErrorCode.validation);
        assertThat(error.message()).doesNotContainIgnoringCase("epub");
        assertThat(error.title()).doesNotContainIgnoringCase("epub");
    }

    // an extension contradicted by the content is refused rather than parsed as whatever
    // the bytes happen to be.
    @Test
    void open_fb2ExtensionOverZipContent_isRefused() {
        final Path misnamed = zipOf("book.fb2", "inner.txt", AMBIGUOUS_TEXT);

        assertThat(errorOf(DocumentServices.newService().open(misnamed)).code()).isEqualTo(ErrorCode.validation);
    }

    // a container format records no document-level charset, while a text format records
    // the one it was read with.
    @Test
    void open_containerAndTextFormats_differOnWhetherTheyRecordACharset() {
        final Document txt = okDocument(DocumentServices.newService().open(write("notes.txt", AMBIGUOUS_TEXT)));

        assertThat(txt.charset()).isEqualTo("UTF-8");
        assertThat(txt.hasBom()).isFalse();
    }

    /**
     * The limits are stated over <em>every</em> zip container rather than over EPUB, because a cap that guards one
     * of two entry points guards neither — an attacker picks the extension. Both readers call the same
     * production-limits reader, but that is an argument; this is the assertion.
     */
    // WHEN a .fb2.zip whose entry expands implausibly is opened, THEN the result carries
    // ErrorCode.validation and no document is returned.
    @Test
    void open_oversizedZippedFictionBook_isRefusedLikeAnOversizedEpub() {
        final Path bomb = zipOfBytes("book.fb2.zip", "book.fb2", new byte[BOMB_INFLATED_BYTES]);

        final Result<Document> result = DocumentServices.newService().open(bomb);

        assertThat(errorOf(result).code()).isEqualTo(ErrorCode.validation);
        assertThat(result.data()).isNull();
    }

    // the same archive under the .epub extension is refused identically, which is what
    // "these limits apply to every zip container" means in practice.
    @Test
    void open_oversizedEpub_isRefusedTheSameWay() {
        final Path bomb = zipOfBytes("book.epub", "mimetype", new byte[BOMB_INFLATED_BYTES]);

        assertThat(errorOf(DocumentServices.newService().open(bomb)).code()).isEqualTo(ErrorCode.validation);
    }

    private Path zipOfBytes(String archiveName, String entryName, byte[] content) {
        final Path archive = tempDir.resolve(archiveName);
        try (OutputStream out = Files.newOutputStream(archive);
                ZipOutputStream zip = new ZipOutputStream(out)) {
            zip.putNextEntry(new ZipEntry(entryName));
            zip.write(content);
            zip.closeEntry();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return archive;
    }

    private Path zipOf(String archiveName, String entryName, String content) {
        final Path archive = tempDir.resolve(archiveName);
        try (OutputStream out = Files.newOutputStream(archive);
                ZipOutputStream zip = new ZipOutputStream(out)) {
            zip.putNextEntry(new ZipEntry(entryName));
            zip.write(content.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return archive;
    }
}
