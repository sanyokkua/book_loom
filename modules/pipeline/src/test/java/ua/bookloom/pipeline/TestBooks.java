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
public final class TestBooks {

    private static final String DEFAULT_PAGE_TITLE = "Test";

    private TestBooks() {}

    public static Path markdown(final Path destination, final String content) {
        return write(destination, content);
    }

    public static Path markdown(final Path destination, final String content, @Nullable final String language) {
        final String frontmatter = language == null ? "" : "---\nlang: " + language + "\n---\n\n";
        return write(destination, frontmatter + content);
    }

    public static Path txt(final Path destination, final String content) {
        return write(destination, content);
    }

    public static Path fb2(final Path destination, final List<String> paragraphs, @Nullable final String language) {
        return write(destination, fb2Xml(paragraphs, language));
    }

    public static Path zippedFb2(
            final Path destination, final List<String> paragraphs, @Nullable final String language) {
        try (OutputStream output = Files.newOutputStream(destination);
                ZipOutputStream zip = new ZipOutputStream(output)) {
            put(zip, "book.fb2", fb2Xml(paragraphs, language), ZipEntry.DEFLATED);
            return destination;
        } catch (IOException cause) {
            throw new UncheckedIOException(cause);
        }
    }

    public static Path epub(
            final Path destination, final List<List<String>> spineParagraphs, @Nullable final String language) {
        return epub(destination, spineParagraphs, language, null);
    }

    /** An EPUB whose content documents each declare {@code contentLanguage} as {@code xml:lang} on their root. */
    public static Path epub(
            final Path destination,
            final List<List<String>> spineParagraphs,
            @Nullable final String language,
            @Nullable final String contentLanguage) {
        try (OutputStream output = Files.newOutputStream(destination);
                ZipOutputStream zip = new ZipOutputStream(output)) {
            writeEpub(zip, spineParagraphs, language, contentLanguage, DEFAULT_PAGE_TITLE);
            return destination;
        } catch (IOException cause) {
            throw new UncheckedIOException(cause);
        }
    }

    /** An EPUB whose content documents each carry {@code pageTitle} as their {@code <title>}. */
    public static Path epubTitled(
            final Path destination, final List<List<String>> spineParagraphs, final String pageTitle) {
        try (OutputStream output = Files.newOutputStream(destination);
                ZipOutputStream zip = new ZipOutputStream(output)) {
            writeEpub(zip, spineParagraphs, "en", null, pageTitle);
            return destination;
        } catch (IOException cause) {
            throw new UncheckedIOException(cause);
        }
    }

    /** An EPUB whose navigation document, outside the spine, lists {@code labels}, each linking the first chapter. */
    public static Path epubWithNavigation(
            final Path destination, final List<List<String>> spineParagraphs, final List<String> labels) {
        try (OutputStream output = Files.newOutputStream(destination);
                ZipOutputStream zip = new ZipOutputStream(output)) {
            put(zip, "mimetype", "application/epub+zip", ZipEntry.STORED);
            put(zip, "META-INF/container.xml", containerXml(), ZipEntry.DEFLATED);
            final String navItem =
                    "<item id=\"nav\" href=\"nav.xhtml\" media-type=\"application/xhtml+xml\" properties=\"nav\"/>";
            put(zip, "OEBPS/content.opf", opf(spineParagraphs.size(), "en", navItem), ZipEntry.DEFLATED);
            putChapters(zip, spineParagraphs, null, DEFAULT_PAGE_TITLE);
            put(zip, "OEBPS/nav.xhtml", navigation(labels), ZipEntry.DEFLATED);
            return destination;
        } catch (IOException cause) {
            throw new UncheckedIOException(cause);
        }
    }

    /**
     * An EPUB whose navigation document lists {@code navLabel} and whose NCX lists {@code ncxLabel}, both linking the
     * first chapter — two auxiliary segments while the labels differ.
     */
    public static Path epubWithNcx(
            final Path destination,
            final List<List<String>> spineParagraphs,
            final String navLabel,
            final String ncxLabel) {
        try (OutputStream output = Files.newOutputStream(destination);
                ZipOutputStream zip = new ZipOutputStream(output)) {
            put(zip, "mimetype", "application/epub+zip", ZipEntry.STORED);
            put(zip, "META-INF/container.xml", containerXml(), ZipEntry.DEFLATED);
            final String items =
                    "<item id=\"nav\" href=\"nav.xhtml\" media-type=\"application/xhtml+xml\" properties=\"nav\"/>"
                            + "<item id=\"ncx\" href=\"toc.ncx\" media-type=\"application/x-dtbncx+xml\"/>";
            put(zip, "OEBPS/content.opf", opf(spineParagraphs.size(), "en", items), ZipEntry.DEFLATED);
            putChapters(zip, spineParagraphs, null, DEFAULT_PAGE_TITLE);
            put(zip, "OEBPS/nav.xhtml", navigation(List.of(navLabel)), ZipEntry.DEFLATED);
            put(zip, "OEBPS/toc.ncx", ncx(ncxLabel), ZipEntry.DEFLATED);
            return destination;
        } catch (IOException cause) {
            throw new UncheckedIOException(cause);
        }
    }

    private static String ncx(final String label) {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<ncx xmlns=\"http://www.daisy.org/z3986/2005/ncx/\" version=\"2005-1\">"
                + "<head/><docTitle><text>Test</text></docTitle><navMap>"
                + "<navPoint id=\"np0\" playOrder=\"1\"><navLabel><text>" + label + "</text></navLabel>"
                + "<content src=\"ch0.xhtml\"/></navPoint>"
                + "</navMap></ncx>";
    }

    /**
     * An EPUB whose package document sits at the container root beside chapters of the given file names, so a
     * segment id reads {@code <name>:<n>} — {@code ch07.xhtml:41} — as the specification's examples name them.
     */
    public static Path epubAtRoot(
            final Path destination, final List<String> chapterNames, final List<List<String>> spineParagraphs) {
        try (OutputStream output = Files.newOutputStream(destination);
                ZipOutputStream zip = new ZipOutputStream(output)) {
            put(zip, "mimetype", "application/epub+zip", ZipEntry.STORED);
            put(zip, "META-INF/container.xml", containerXml().replace("OEBPS/", ""), ZipEntry.DEFLATED);
            put(zip, "content.opf", rootOpf(chapterNames), ZipEntry.DEFLATED);
            for (int index = 0; index < chapterNames.size(); index++) {
                put(
                        zip,
                        chapterNames.get(index),
                        xhtml(spineParagraphs.get(index), null, DEFAULT_PAGE_TITLE),
                        ZipEntry.DEFLATED);
            }
            return destination;
        } catch (IOException cause) {
            throw new UncheckedIOException(cause);
        }
    }

    private static String rootOpf(final List<String> chapterNames) {
        final StringBuilder manifest = new StringBuilder();
        final StringBuilder spine = new StringBuilder();
        for (int index = 0; index < chapterNames.size(); index++) {
            manifest.append("<item id=\"c")
                    .append(index)
                    .append("\" href=\"")
                    .append(chapterNames.get(index))
                    .append("\" media-type=\"application/xhtml+xml\"/>");
            spine.append("<itemref idref=\"c").append(index).append("\"/>");
        }
        return opf(0, "en", manifest.toString()).replace("<spine></spine>", "<spine>" + spine + "</spine>");
    }

    private static String navigation(final List<String> labels) {
        final String items = labels.stream()
                .map(label -> "<li><a href=\"ch0.xhtml\">" + label + "</a></li>")
                .reduce("", String::concat);
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<html xmlns=\"http://www.w3.org/1999/xhtml\" xmlns:epub=\"http://www.idpf.org/2007/ops\">"
                + "<head><title>Contents</title></head><body><nav epub:type=\"toc\"><ol>"
                + items
                + "</ol></nav></body></html>";
    }

    /** An EPUB whose first chapter is encrypted under an Adobe ADEPT manifest. */
    public static Path encryptedEpub(final Path destination, final List<List<String>> spineParagraphs) {
        try (OutputStream output = Files.newOutputStream(destination);
                ZipOutputStream zip = new ZipOutputStream(output)) {
            writeEpub(zip, spineParagraphs, "en", null, DEFAULT_PAGE_TITLE);
            put(zip, "META-INF/encryption.xml", encryptionXml(), ZipEntry.DEFLATED);
            return destination;
        } catch (IOException cause) {
            throw new UncheckedIOException(cause);
        }
    }

    private static void writeEpub(
            final ZipOutputStream zip,
            final List<List<String>> spineParagraphs,
            @Nullable final String language,
            @Nullable final String contentLanguage,
            final String pageTitle)
            throws IOException {
        put(zip, "mimetype", "application/epub+zip", ZipEntry.STORED);
        put(zip, "META-INF/container.xml", containerXml(), ZipEntry.DEFLATED);
        put(zip, "OEBPS/content.opf", opf(spineParagraphs.size(), language, ""), ZipEntry.DEFLATED);
        putChapters(zip, spineParagraphs, contentLanguage, pageTitle);
    }

    private static void putChapters(
            final ZipOutputStream zip,
            final List<List<String>> spineParagraphs,
            @Nullable final String contentLanguage,
            final String pageTitle)
            throws IOException {
        for (int index = 0; index < spineParagraphs.size(); index++) {
            put(
                    zip,
                    "OEBPS/ch" + index + ".xhtml",
                    xhtml(spineParagraphs.get(index), contentLanguage, pageTitle),
                    ZipEntry.DEFLATED);
        }
    }

    private static String encryptionXml() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<encryption xmlns=\"urn:oasis:names:tc:opendocument:xmlns:container\""
                + " xmlns:enc=\"http://www.w3.org/2001/04/xmlenc#\" xmlns:adept=\"http://ns.adobe.com/adept\">"
                + "<enc:EncryptedData><enc:EncryptionMethod Algorithm=\"http://ns.adobe.com/pdf/enc#RC\"/>"
                + "<adept:KeyInfo/>"
                + "<enc:CipherData><enc:CipherReference URI=\"OEBPS/ch0.xhtml\"/></enc:CipherData>"
                + "</enc:EncryptedData></encryption>";
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

    private static String opf(final int spineCount, @Nullable final String language, final String extraManifest) {
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
                + extraManifest
                + "</manifest><spine>"
                + spine
                + "</spine></package>";
    }

    private static String xhtml(
            final List<String> paragraphs, @Nullable final String contentLanguage, final String pageTitle) {
        final String lang = contentLanguage == null ? "" : " xml:lang=\"" + contentLanguage + "\"";
        final String body =
                paragraphs.stream().map(text -> "<p>" + text + "</p>").reduce("", String::concat);
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<html xmlns=\"http://www.w3.org/1999/xhtml\"" + lang + "><head><title>" + pageTitle
                + "</title></head><body>"
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
