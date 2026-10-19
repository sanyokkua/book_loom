package ua.bookloom.document.epub;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.api.document.Document;
import ua.bookloom.document.fixture.TargetedDocuments;

/** EPUB never loses a character to the content document's charset: jsoup writes a reference (task 5.6). */
class EpubWriterEncodingTest {

    @TempDir
    private Path tempDir;

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
              <metadata xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:title>T</dc:title><dc:language>en</dc:language></metadata>
              <manifest><item id="c01" href="c01.xhtml" media-type="application/xhtml+xml"/></manifest>
              <spine><itemref idref="c01"/></spine>
            </package>
            """;

    // WHEN a spine document declared ISO-8859-1 receives a target holding a character that charset cannot encode,
    // THEN the output holds a numeric character reference (not a question mark) and re-reading yields the character.
    @Test
    void write_unencodableCharacterInLatin1Document_writesNumericReference() {
        final Path epub = tempDir.resolve("in.epub");
        new EpubZipBuilder()
                .mimetype()
                .entry("META-INF/container.xml", CONTAINER_XML)
                .entry("OEBPS/content.opf", OPF)
                .binaryEntry("OEBPS/c01.xhtml", """
                        <?xml version="1.0" encoding="ISO-8859-1"?>
                        <html xmlns="http://www.w3.org/1999/xhtml"><head><title>t</title></head>
                        <body><p>Hello.</p></body></html>
                        """.getBytes(StandardCharsets.ISO_8859_1))
                .writeTo(epub);
        final OpenEpubRegistry registry = new OpenEpubRegistry();
        final Document document = TargetedDocuments.withTarget(new EpubReader(registry).read(epub), 0, "車");

        final Path output = new EpubWriter(registry).write(document, tempDir.resolve("out.epub"), "zh");

        final String written = new String(entryContent(output, "OEBPS/c01.xhtml"), StandardCharsets.ISO_8859_1);
        assertThat(written).contains("&#").doesNotContain("<p>?</p>");
        assertThat(Jsoup.parse(written).select("p").text()).isEqualTo("車");
    }

    private static byte[] entryContent(Path zip, String name) {
        try (ZipInputStream in = new ZipInputStream(Files.newInputStream(zip))) {
            ZipEntry entry = in.getNextEntry();
            while (entry != null) {
                if (entry.getName().equals(name)) {
                    return in.readAllBytes();
                }
                entry = in.getNextEntry();
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        throw new IllegalStateException("no entry " + name);
    }
}
