package ua.bookloom.document.epub;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.jdom2.JDOMException;
import org.jdom2.input.SAXBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.api.document.Document;

/** How {@code EpubWriter} keeps a line feed inside an XHTML attribute value (task 5.2). */
class EpubWriterAttributeTest {

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

    private static final String BOOKMARK = "data-pdf-bookmark=\"Communication: &#10;So Many Choices\"";

    // WHEN an attribute value holds a line-feed reference and the book is written with zero edits, THEN the output
    // still holds a reference and an XML reader still sees a line feed, not a space.
    @Test
    void write_attributeWithLineFeedReference_keepsTheReference() throws Exception {
        final byte[] written = writeChapter(chapter("<p " + BOOKMARK + ">Hello.</p>"));

        assertThat(new String(written, StandardCharsets.UTF_8)).contains("Communication: &#10;So Many Choices");
        assertThat(attributeValueSeenByXmlReader(written)).isEqualTo("Communication: \nSo Many Choices");
    }

    // WHEN an attribute value holds a raw line feed, which an XML reader reads as a space, THEN the output is still
    // read as a space: writing it as a line-feed reference would change the value (corpus: an O'Reilly chapter).
    @Test
    void write_attributeWithRawLineFeed_isStillReadAsASpace() throws Exception {
        final byte[] written =
                writeChapter(chapter("<p data-pdf-bookmark=\"Measuring and Governing \nArchitecture\">Hello.</p>"));

        assertThat(attributeValueSeenByXmlReader(written)).isEqualTo("Measuring and Governing  Architecture");
    }

    // WHEN the document already holds the sentinel code point U+E00A, THEN the post-pass is skipped and the
    // attribute's line feed is written raw, as jsoup writes it.
    @Test
    void write_documentAlreadyHoldingASentinel_skipsThePostPass() {
        final byte[] written = writeChapter(chapter("<p " + BOOKMARK + ">Hello .</p>"));

        final String text = new String(written, StandardCharsets.UTF_8);
        assertThat(text).contains("Communication: \nSo Many Choices").doesNotContain("&#10;");
    }

    private static String chapter(String paragraph) {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <html xmlns="http://www.w3.org/1999/xhtml"><head><title>t</title></head>
                <body>%s</body></html>
                """.formatted(paragraph);
    }

    private byte[] writeChapter(String chapterXhtml) {
        final Path epub = tempDir.resolve("in.epub");
        new EpubZipBuilder()
                .mimetype()
                .entry("META-INF/container.xml", CONTAINER_XML)
                .entry("OEBPS/content.opf", OPF)
                .entry("OEBPS/c01.xhtml", chapterXhtml)
                .writeTo(epub);
        final OpenEpubRegistry registry = new OpenEpubRegistry();
        final Document document = new EpubReader(registry).read(epub);
        final Path output = new EpubWriter(registry).write(document, tempDir.resolve("out.epub"), "uk");
        return entryContent(output, "OEBPS/c01.xhtml");
    }

    private static String attributeValueSeenByXmlReader(byte[] xhtml) throws JDOMException, IOException {
        final org.jdom2.Document parsed = new SAXBuilder().build(new ByteArrayInputStream(xhtml));
        return parsed.getRootElement().getChildren().get(1).getChildren().get(0).getAttributeValue("data-pdf-bookmark");
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
