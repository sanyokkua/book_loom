package ua.bookloom.document;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.document.DocumentServiceTestFiles.entries;
import static ua.bookloom.document.DocumentServiceTestFiles.zip;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.Document;

/**
 * {@link DocumentPort#write(Document, Path, String, String)}'s relationship to the three-argument overload it was
 * added alongside (task 4.6) — split out of {@link DocumentServiceTest} to keep that class under the project's
 * file-length limit.
 */
class DocumentServiceWriteOverloadTest {

    private static final String CONTAINER_XML = """
            <?xml version="1.0" encoding="UTF-8"?>
            <container xmlns="urn:oasis:names:tc:opendocument:xmlns:container" version="1.0">
              <rootfiles>
                <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
              </rootfiles>
            </container>
            """;

    private static final String OPF = """
            <?xml version="1.0" encoding="UTF-8"?>
            <package xmlns="http://www.idpf.org/2007/opf" version="2.0" unique-identifier="bookid">
              <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                <dc:title>Test Book</dc:title>
                <dc:creator>A. Author</dc:creator>
                <dc:language>en</dc:language>
              </metadata>
              <manifest>
                <item id="c01" href="c01.xhtml" media-type="application/xhtml+xml"/>
              </manifest>
              <spine>
                <itemref idref="c01"/>
              </spine>
            </package>
            """;

    private static final String CHAPTER = """
            <?xml version="1.0" encoding="UTF-8"?>
            <html xmlns="http://www.w3.org/1999/xhtml">
            <head><title>Chapter One</title></head>
            <body><p>Prose.</p></body>
            </html>
            """;

    @TempDir
    private Path tempDir;

    // the three-argument write behaves as the four-argument overload called with a null source
    // language: both write the target language into the same OPF bytes (task 4.6).
    @Test
    void write_threeArgOverload_behavesAsFourArgWithNullSourceLanguage() {
        final Path source = tempDir.resolve("book.epub");
        zip(
                source,
                entries(
                        "mimetype", "application/epub+zip",
                        "META-INF/container.xml", CONTAINER_XML,
                        "OEBPS/content.opf", OPF,
                        "OEBPS/c01.xhtml", CHAPTER));

        final DocumentService threeArgService = DocumentServices.newService();
        final Document openedForThreeArg = openFixture(threeArgService, source);
        final Result<Path> threeArgResult =
                threeArgService.write(openedForThreeArg, tempDir.resolve("three-arg.epub"), "uk");

        final DocumentService fourArgService = DocumentServices.newService();
        final Document openedForFourArg = openFixture(fourArgService, source);
        final Result<Path> fourArgResult =
                fourArgService.write(openedForFourArg, tempDir.resolve("four-arg.epub"), null, "uk");

        assertThat(threeArgResult.isOk()).isTrue();
        assertThat(fourArgResult.isOk()).isTrue();
        assertThat(opfEntryOf(Objects.requireNonNull(threeArgResult.data(), "data")))
                .isEqualTo(opfEntryOf(Objects.requireNonNull(fourArgResult.data(), "data")));
    }

    private static Document openFixture(DocumentService service, Path source) {
        final Result<Document> opened = service.open(source);
        assertThat(opened.isOk())
                .withFailMessage("fixture did not open: %s", opened.error())
                .isTrue();
        return Objects.requireNonNull(opened.data(), "data");
    }

    private static String opfEntryOf(Path epub) {
        try (ZipInputStream in = new ZipInputStream(Files.newInputStream(epub))) {
            ZipEntry entry = in.getNextEntry();
            while (entry != null) {
                if ("OEBPS/content.opf".equals(entry.getName())) {
                    return new String(in.readAllBytes(), StandardCharsets.UTF_8);
                }
                entry = in.getNextEntry();
            }
            throw new AssertionError("no OEBPS/content.opf entry in " + epub);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
