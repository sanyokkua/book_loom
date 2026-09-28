package ua.bookloom.document.epub;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.api.document.Document;

/** How {@code EpubWriter} keeps an XHTML document's prolog verbatim (task 5.3). */
class EpubWriterPrologTest {

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

    private static final String PROLOG = """
            <?xml version='1.0' encoding='utf-8'?>
            <!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.1//EN"
              'http://www.w3.org/TR/xhtml11/DTD/xhtml11.dtd'>
            """;

    // WHEN a content document begins with an XML declaration and a two-line DOCTYPE with single-quoted
    // identifiers, THEN it is written with exactly that prolog and no declaration comment.
    @Test
    void write_prologWithDeclarationAndDoctype_isKeptVerbatim() {
        final byte[] written = writeChapter(PROLOG + ROOT);

        final String text = new String(written, StandardCharsets.UTF_8);
        assertThat(text).startsWith(PROLOG + "<html").doesNotContain("<!--?xml");
    }

    // WHEN a content document has no prolog, THEN none is added.
    @Test
    void write_documentWithoutProlog_gainsNoProlog() {
        final byte[] written = writeChapter(ROOT);

        assertThat(new String(written, StandardCharsets.UTF_8)).startsWith("<html");
    }

    private static final String ROOT = """
            <html xmlns="http://www.w3.org/1999/xhtml"><head><title>t</title></head>
            <body><p>Hello.</p></body></html>
            """;

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
