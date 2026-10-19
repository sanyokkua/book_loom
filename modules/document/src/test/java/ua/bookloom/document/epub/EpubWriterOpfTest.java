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

/** How {@code EpubWriter} writes the package document's declaration and line ends (task 5.1). */
class EpubWriterOpfTest {

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

    private static final String OPF_BODY = """
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
            <html xmlns="http://www.w3.org/1999/xhtml"><head><title>t</title></head>
            <body><p>Hello.</p></body></html>
            """;

    // WHEN a package document without an XML declaration and with line-feed line ends is written unchanged, THEN
    // the output has no declaration and no carriage-return byte.
    @Test
    void write_opfWithoutDeclaration_addsNeitherDeclarationNorCarriageReturn() {
        final byte[] written = writeOpfUnchanged(OPF_BODY);

        final String text = new String(written, StandardCharsets.UTF_8);
        assertThat(text).doesNotStartWith("<?xml");
        assertThat(written).doesNotContain((byte) 0x0D);
    }

    // WHEN the package document begins with an XML declaration, THEN the output begins with one naming the same
    // encoding.
    @Test
    void write_opfWithDeclaration_keepsDeclarationAndItsEncoding() {
        final byte[] written = writeOpfUnchanged("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" + OPF_BODY);

        assertThat(new String(written, StandardCharsets.UTF_8))
                .startsWith("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
        assertThat(written).doesNotContain((byte) 0x0D);
    }

    private byte[] writeOpfUnchanged(String opfXml) {
        final Path epub = tempDir.resolve("opf.epub");
        new EpubZipBuilder()
                .mimetype()
                .entry("META-INF/container.xml", CONTAINER_XML)
                .entry("OEBPS/content.opf", opfXml)
                .entry("OEBPS/c01.xhtml", CHAPTER)
                .writeTo(epub);
        final OpenEpubRegistry registry = new OpenEpubRegistry();
        final Document document = new EpubReader(registry).read(epub);
        final Path output = new EpubWriter(registry).write(document, tempDir.resolve("opf-out.epub"), "uk");
        return entryContent(output, "OEBPS/content.opf");
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
