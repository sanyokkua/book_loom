package ua.bookloom.pipeline;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.jspecify.annotations.Nullable;

/** Small real book files used by pipeline tests. */
final class TestBooks {

    private TestBooks() {}

    static Path markdown(final Path destination, final String content) {
        return write(destination, content);
    }

    static Path markdown(final Path destination, final String content, @Nullable final String language) {
        final String frontmatter = language == null ? "" : "---\nlang: " + language + "\n---\n\n";
        return write(destination, frontmatter + content);
    }

    static Path txt(final Path destination, final String content) {
        return write(destination, content);
    }

    static Path fb2(final Path destination, final List<String> paragraphs, @Nullable final String language) {
        return write(destination, fb2Xml(paragraphs, language));
    }

    static Path zippedFb2(final Path destination, final List<String> paragraphs, @Nullable final String language) {
        try (OutputStream output = Files.newOutputStream(destination);
                ZipOutputStream zip = new ZipOutputStream(output)) {
            put(zip, "book.fb2", fb2Xml(paragraphs, language), ZipEntry.DEFLATED);
            return destination;
        } catch (IOException cause) {
            throw new UncheckedIOException(cause);
        }
    }

    static Path epub(
            final Path destination, final List<List<String>> spineParagraphs, @Nullable final String language) {
        try (OutputStream output = Files.newOutputStream(destination);
                ZipOutputStream zip = new ZipOutputStream(output)) {
            put(zip, "mimetype", "application/epub+zip", ZipEntry.STORED);
            put(zip, "META-INF/container.xml", containerXml(), ZipEntry.DEFLATED);
            put(zip, "OEBPS/content.opf", opf(spineParagraphs.size(), language), ZipEntry.DEFLATED);
            for (int index = 0; index < spineParagraphs.size(); index++) {
                put(zip, "OEBPS/ch" + index + ".xhtml", xhtml(spineParagraphs.get(index)), ZipEntry.DEFLATED);
            }
            return destination;
        } catch (IOException cause) {
            throw new UncheckedIOException(cause);
        }
    }

    private static Path write(final Path destination, final String content) {
        try {
            return Files.writeString(destination, content, StandardCharsets.UTF_8);
        } catch (IOException cause) {
            throw new UncheckedIOException(cause);
        }
    }

    private static String fb2Xml(final List<String> paragraphs, @Nullable final String language) {
        final String lang = language == null ? "" : "<lang>" + language + "</lang>";
        final String body =
                paragraphs.stream().map(text -> "<p>" + text + "</p>").reduce("", String::concat);
        return "<?xml version=\"1.0\" encoding=\"utf-8\"?>"
                + "<FictionBook xmlns=\"http://www.gribuser.ru/xml/fictionbook/2.0\">"
                + "<description><title-info><genre>prose</genre><book-title>Test</book-title>"
                + lang
                + "</title-info></description><body><section>"
                + body
                + "</section></body></FictionBook>";
    }

    private static String containerXml() {
        return "<?xml version=\"1.0\"?>"
                + "<container xmlns=\"urn:oasis:names:tc:opendocument:xmlns:container\" version=\"1.0\">"
                + "<rootfiles><rootfile full-path=\"OEBPS/content.opf\" media-type=\"application/oebps-package+xml\"/>"
                + "</rootfiles></container>";
    }

    private static String opf(final int spineCount, @Nullable final String language) {
        final String lang = language == null ? "" : "<dc:language>" + language + "</dc:language>";
        final StringBuilder manifest = new StringBuilder();
        final StringBuilder spine = new StringBuilder();
        for (int index = 0; index < spineCount; index++) {
            manifest.append("<item id=\"ch")
                    .append(index)
                    .append("\" href=\"ch")
                    .append(index)
                    .append(".xhtml\" media-type=\"application/xhtml+xml\"/>");
            spine.append("<itemref idref=\"ch").append(index).append("\"/>");
        }
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<package xmlns=\"http://www.idpf.org/2007/opf\" version=\"3.0\" unique-identifier=\"id\">"
                + "<metadata xmlns:dc=\"http://purl.org/dc/elements/1.1/\"><dc:identifier id=\"id\">test</dc:identifier>"
                + "<dc:title>Test</dc:title>"
                + lang
                + "</metadata><manifest>"
                + manifest
                + "</manifest><spine>"
                + spine
                + "</spine></package>";
    }

    private static String xhtml(final List<String> paragraphs) {
        final String body =
                paragraphs.stream().map(text -> "<p>" + text + "</p>").reduce("", String::concat);
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<html xmlns=\"http://www.w3.org/1999/xhtml\"><head><title>Test</title></head><body>"
                + body
                + "</body></html>";
    }

    private static void put(final ZipOutputStream zip, final String name, final String content, final int method)
            throws IOException {
        final byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        final ZipEntry entry = new ZipEntry(name);
        entry.setMethod(method);
        if (method == ZipEntry.STORED) {
            final CRC32 crc = new CRC32();
            crc.update(bytes);
            entry.setSize(bytes.length);
            entry.setCompressedSize(bytes.length);
            entry.setCrc(crc.getValue());
        }
        zip.putNextEntry(entry);
        zip.write(bytes);
        zip.closeEntry();
    }
}
