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

/** Language values that already name the target language are not rewritten (task 4.6 follow-up). */
class EpubWriterLanguageKeptTest {

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
              <metadata xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:title>T</dc:title><dc:language>en-US</dc:language></metadata>
              <manifest><item id="c01" href="c01.xhtml" media-type="application/xhtml+xml"/></manifest>
              <spine><itemref idref="c01"/></spine>
            </package>
            """;

    private static final String CHAPTER = """
            <?xml version="1.0" encoding="UTF-8"?>
            <html xmlns="http://www.w3.org/1999/xhtml" xml:lang="en-US" lang="en-US">
            <head><title>Chapter</title></head>
            <body lang="en-US"><p>Paragraph 0.</p></body>
            </html>
            """;

    // A regional tag that already names the target language is kept: writing en-US as English changes nothing, so
    // a zero-edit write of the book still round-trips (corpus: 15 Standard Ebooks and Calibre books).
    @Test
    void write_languageValuesAlreadyNamingTheTarget_areKeptWithTheirRegion() {
        final Path epub = tempDir.resolve("book.epub");
        new EpubZipBuilder()
                .mimetype()
                .entry("META-INF/container.xml", CONTAINER_XML)
                .entry("OEBPS/content.opf", OPF)
                .entry("OEBPS/c01.xhtml", CHAPTER)
                .writeTo(epub);
        final OpenEpubRegistry registry = new OpenEpubRegistry();
        final Document document = new EpubReader(registry).read(epub);

        final Path output = new EpubWriter(registry).write(document, tempDir.resolve("out.epub"), "en");

        assertThat(textOf(output, "OEBPS/content.opf")).contains("<dc:language>en-US</dc:language>");
        assertThat(textOf(output, "OEBPS/c01.xhtml"))
                .contains("xml:lang=\"en-US\"", " lang=\"en-US\"")
                .doesNotContain("lang=\"en\"");
    }

    private static String textOf(Path zip, String name) {
        try (ZipInputStream in = new ZipInputStream(Files.newInputStream(zip))) {
            ZipEntry entry = in.getNextEntry();
            while (entry != null) {
                if (entry.getName().equals(name)) {
                    return new String(in.readAllBytes(), StandardCharsets.UTF_8);
                }
                entry = in.getNextEntry();
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        throw new IllegalStateException("no entry " + name);
    }
}
