package ua.bookloom.document.epub;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Builds a minimal EPUB zip archive entry by entry, in the order added — so a test can make the physical zip
 * order diverge from the OPF's declared spine order, or force a specific entry's compression method. Originally
 * this group's own ad-hoc fixture; made {@code public} (task 5.1) so the {@code golden} package's adversarial
 * fixture family can reuse it instead of a second zip-writing implementation.
 */
public final class EpubZipBuilder {

    /** Sentinel meaning "let {@link ZipOutputStream} pick the method" (its default is {@link ZipEntry#DEFLATED}). */
    private static final int DEFAULT_METHOD = -1;

    private final List<Entry> entries = new ArrayList<>();

    public EpubZipBuilder mimetype() {
        return entry("mimetype", "application/epub+zip");
    }

    public EpubZipBuilder entry(String name, String content) {
        return entry(name, content.getBytes(StandardCharsets.UTF_8), DEFAULT_METHOD);
    }

    /** Adds a text entry, forcing {@code method} ({@link ZipEntry#STORED} or {@link ZipEntry#DEFLATED}). */
    public EpubZipBuilder entry(String name, String content, int method) {
        return entry(name, content.getBytes(StandardCharsets.UTF_8), method);
    }

    /** Adds a binary entry — a stand-in for an image or embedded font, to prove byte-for-byte preservation. */
    public EpubZipBuilder binaryEntry(String name, byte[] content) {
        return entry(name, content, DEFAULT_METHOD);
    }

    private EpubZipBuilder entry(String name, byte[] content, int method) {
        entries.add(new Entry(name, content, method));
        return this;
    }

    public Path writeTo(Path file) {
        try (OutputStream out = Files.newOutputStream(file);
                ZipOutputStream zip = new ZipOutputStream(out)) {
            for (final Entry entry : entries) {
                writeEntry(zip, entry);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return file;
    }

    private static void writeEntry(ZipOutputStream zip, Entry entry) throws IOException {
        final ZipEntry zipEntry = new ZipEntry(entry.name());
        if (entry.method() != DEFAULT_METHOD) {
            applyMethod(zipEntry, entry.method(), entry.content());
        }
        zip.putNextEntry(zipEntry);
        zip.write(entry.content());
        zip.closeEntry();
    }

    private static void applyMethod(ZipEntry zipEntry, int method, byte[] content) {
        zipEntry.setMethod(method);
        if (method == ZipEntry.STORED) {
            zipEntry.setSize(content.length);
            zipEntry.setCompressedSize(content.length);
            final CRC32 crc = new CRC32();
            crc.update(content);
            zipEntry.setCrc(crc.getValue());
        }
    }

    @SuppressWarnings("ArrayRecordComponent") // deliberate: raw binary content, defensively copied both ways,
    // same as RawEntry — equals()/hashCode() are never used for this internal, test-only comparison.
    private record Entry(String name, byte[] content, int method) {
        private Entry {
            content = content.clone();
        }

        @Override
        public byte[] content() {
            return content.clone();
        }
    }
}
