package ua.bookloom.document.epub;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.api.document.Document;

/**
 * One corpus book (68 entries, none named {@code mimetype}) opened with 836 segments and then refused every
 * write. OCF fixes the entry completely, so emitting it is not invention (ADR-0030).
 */
class EpubMimetypeSynthesisTest {

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
            <head><title>Chapter</title></head>
            <body>
            <p>Paragraph 0.</p>
            </body>
            </html>
            """;

    @TempDir
    private Path tempDir;

    // WHEN the source archive carries no mimetype entry, THEN the export synthesizes one — first, STORED, with the
    // exact OCF content — and writes every other entry unchanged.
    @Test
    void write_sourceWithoutMimetype_synthesizesMimetypeFirstAndStored() {
        final Path epub = tempDir.resolve("book.epub");
        new EpubZipBuilder()
                .entry("META-INF/container.xml", CONTAINER_XML)
                .entry("OEBPS/content.opf", OPF)
                .entry("OEBPS/c01.xhtml", CHAPTER)
                .writeTo(epub);
        final OpenEpubRegistry registry = new OpenEpubRegistry();
        final Document document = new EpubReader(registry).read(epub);

        final Path output = new EpubWriter(registry).write(document, tempDir.resolve("out.epub"), "uk");

        final List<ZipRecord> written = readZip(output);
        assertThat(written)
                .extracting(ZipRecord::name)
                .containsExactly("mimetype", "META-INF/container.xml", "OEBPS/content.opf", "OEBPS/c01.xhtml");
        assertThat(written.get(0).method()).isEqualTo(ZipEntry.STORED);
        assertThat(new String(written.get(0).content(), StandardCharsets.US_ASCII))
                .isEqualTo("application/epub+zip");
        assertThat(new String(written.get(1).content(), StandardCharsets.UTF_8)).isEqualTo(CONTAINER_XML);
    }

    private static List<ZipRecord> readZip(Path zip) {
        final List<ZipRecord> result = new ArrayList<>();
        try (ZipInputStream in = new ZipInputStream(Files.newInputStream(zip))) {
            for (ZipEntry entry = in.getNextEntry(); entry != null; entry = in.getNextEntry()) {
                result.add(new ZipRecord(entry.getName(), entry.getMethod(), in.readAllBytes()));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return result;
    }

    @SuppressWarnings("ArrayRecordComponent") // deliberate: raw zip content read back purely for assertions.
    private record ZipRecord(String name, int method, byte[] content) {}
}
