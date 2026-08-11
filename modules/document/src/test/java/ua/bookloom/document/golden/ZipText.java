package ua.bookloom.document.golden;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Reads text out of a zip container for assertion purposes, independently of the production reader.
 *
 * <p>Independence is the point: an assertion that unpacked the archive through the same code the importer uses
 * would agree with it about anything that code got wrong.
 *
 * @param name the entry's name
 * @param text the entry's decoded content
 */
record ZipText(String name, String text) {

    private static final Pattern ENCODING_DECLARATION = Pattern.compile("encoding\\s*=\\s*[\"']([^\"']+)[\"']");
    private static final String CONTENT_DOCUMENT_SUFFIX = ".xhtml";

    /**
     * Every XHTML content document in an EPUB, decoded with the encoding each declares.
     *
     * @param archive the {@code .epub} file
     * @return the content documents, in physical zip order
     */
    static List<ZipText> contentDocumentsOf(Path archive) {
        final List<ZipText> documents = new ArrayList<>();
        for (final RawZipEntry entry : entriesOf(archive)) {
            addIfContentDocument(documents, entry);
        }
        return documents;
    }

    private static void addIfContentDocument(List<ZipText> documents, RawZipEntry entry) {
        if (entry.name().toLowerCase(Locale.ROOT).endsWith(CONTENT_DOCUMENT_SUFFIX)) {
            documents.add(new ZipText(entry.name(), decode(entry.content())));
        }
    }

    /**
     * The first member whose name ends with {@code suffix}, decoded with the encoding it declares.
     *
     * @param archive the zip file
     * @param suffix the member-name suffix to look for
     * @return the member's decoded content
     */
    static String firstMemberEndingWith(Path archive, String suffix) {
        for (final RawZipEntry entry : entriesOf(archive)) {
            if (entry.name().toLowerCase(Locale.ROOT).endsWith(suffix)) {
                return decode(entry.content());
            }
        }
        throw new AssertionError("no member ending with " + suffix + " in " + archive.getFileName());
    }

    /**
     * Every entry of the archive, name and bytes, in physical stream order.
     *
     * @param archive the zip file
     * @return the entries, in the order encountered
     */
    static List<RawZipEntry> entriesOf(Path archive) {
        final List<RawZipEntry> entries = new ArrayList<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(Files.readAllBytes(archive)))) {
            ZipEntry entry = zip.getNextEntry();
            while (entry != null) {
                entries.add(new RawZipEntry(entry.getName(), entry.getMethod(), zip.readAllBytes()));
                entry = zip.getNextEntry();
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return entries;
    }

    /** Decodes with whatever the content declares, defaulting to UTF-8 — the same ladder, read independently. */
    private static String decode(byte[] content) {
        final String prolog = new String(content, 0, Math.min(content.length, 256), StandardCharsets.ISO_8859_1);
        final Matcher matcher = ENCODING_DECLARATION.matcher(prolog);
        final Charset charset = matcher.find() ? charsetOrUtf8(matcher.group(1)) : StandardCharsets.UTF_8;
        return new String(content, charset);
    }

    private static Charset charsetOrUtf8(String name) {
        try {
            return Charset.forName(name);
        } catch (RuntimeException unknown) {
            return StandardCharsets.UTF_8;
        }
    }

    /**
     * One raw zip entry read for assertions.
     *
     * @param name the entry's name
     * @param method its compression method
     * @param content its decompressed bytes
     */
    @SuppressWarnings("ArrayRecordComponent") // deliberate: raw content read purely for assertions.
    record RawZipEntry(String name, int method, byte[] content) {}
}
