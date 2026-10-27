package ua.bookloom.app;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** The smallest EPUB 3 the importer reads, for the window tests: the stored mimetype first, the container, a package. */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class TestEpub {

    private static final String CONTAINER = """
            <?xml version="1.0" encoding="UTF-8"?>
            <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
              <rootfiles><rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/></rootfiles>
            </container>
            """;

    private static final String NAV = """
            <?xml version="1.0" encoding="UTF-8"?>
            <html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops"><head><title>Nav</title></head>
            <body><nav epub:type="toc"><ol><li><a href="chapter.xhtml">Chapter</a></li></ol></nav></body></html>
            """;

    /**
     * Writes an EPUB.
     *
     * @param file where it goes
     * @param title the book's title
     * @param manifest the manifest's item lines after the navigation document's
     * @param spine the spine's itemref elements
     * @param documents each content document's text by its name under {@code OEBPS/}
     * @return {@code file}
     */
    static Path write(
            final Path file,
            final String title,
            final String manifest,
            final String spine,
            final Map<String, String> documents)
            throws IOException {
        try (OutputStream out = Files.newOutputStream(file);
                ZipOutputStream zip = new ZipOutputStream(out)) {
            stored(zip, "mimetype", "application/epub+zip");
            deflated(zip, "META-INF/container.xml", CONTAINER);
            deflated(zip, "OEBPS/content.opf", packageOf(title, manifest, spine));
            deflated(zip, "OEBPS/nav.xhtml", NAV);
            for (final Map.Entry<String, String> document : documents.entrySet()) {
                deflated(zip, "OEBPS/" + document.getKey(), document.getValue());
            }
        }
        return file;
    }

    private static String packageOf(final String title, final String manifest, final String spine) {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="id">
                  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                    <dc:identifier id="id">%s-test</dc:identifier>
                    <dc:title>%s</dc:title>
                    <dc:language>en</dc:language>
                    <meta property="dcterms:modified">2026-01-01T00:00:00Z</meta>
                  </metadata>
                  <manifest>
                    <item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>
                %s  </manifest>
                  <spine>%s</spine>
                </package>
                """.formatted(title, title, manifest, spine);
    }

    /**
     * A content document.
     *
     * @param title its head title
     * @param body its body markup
     * @return the XHTML text
     */
    static String chapter(final String title, final String body) {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <html xmlns="http://www.w3.org/1999/xhtml" xml:lang="en"><head><title>%s</title></head>
                <body>%s</body></html>
                """.formatted(title, body);
    }

    private static void stored(final ZipOutputStream zip, final String name, final String text) throws IOException {
        final byte[] bytes = text.getBytes(StandardCharsets.US_ASCII);
        final CRC32 crc = new CRC32();
        crc.update(bytes);
        final ZipEntry entry = new ZipEntry(name);
        entry.setMethod(ZipEntry.STORED);
        entry.setSize(bytes.length);
        entry.setCompressedSize(bytes.length);
        entry.setCrc(crc.getValue());
        zip.putNextEntry(entry);
        zip.write(bytes);
        zip.closeEntry();
    }

    private static void deflated(final ZipOutputStream zip, final String name, final String text) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(text.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }
}
