package ua.bookloom.document.model;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Reads every entry of a zip container, in physical stream order, within explicit resource limits. Serves
 * <strong>both</strong> zip-backed formats — {@code .epub} and {@code .fb2.zip} — which is why it lives here
 * rather than in {@code document.epub}: a cap that guards one of two entry points guards neither, because an
 * attacker picks the extension (design.md D12).
 *
 * <p>Stream order is captured because it is unrecoverable once the archive is closed, and repackaging must
 * reproduce it exactly.
 *
 * <p><strong>Why the limits exist.</strong> A book file is untrusted input from the internet, and this reader
 * inflates every entry into memory. A small archive can be crafted to expand to hundreds of gigabytes, so without
 * a bound a malicious file takes the whole application down instead of producing an error message. The bound is
 * enforced <em>while</em> inflating, not after: a check that runs once the bytes are already in memory has
 * already lost.
 */
// Checkstyle's HideUtilityClassConstructor parses source text before Lombok's annotation processor runs,
// so it cannot see the private constructor @NoArgsConstructor generates below; suppressed per the escape
// hatch checkstyle.xml documents for exactly this case (java-coding-style.md, ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ZipEntryReader {

    /**
     * The zip format's historical default code page. Older tools store entry names in it without setting the
     * flag that says so, and the default UTF-8 decoder throws {@code ZipException: invalid LOC header (bad entry
     * name)} on those archives — measured directly on real {@code .zip} files, where a retry in this code page
     * reads every entry. This is a robustness measure for {@code .fb2.zip}, which arrives from exactly those
     * tools; it is <strong>not</strong> corpus-proven for EPUB — not one of 12,513 surveyed EPUB entry names
     * contains a non-ASCII byte.
     */
    private static final Charset LEGACY_ENTRY_NAME_CHARSET = Charset.forName("IBM437");

    /** Below this, a high ratio says nothing — a few bytes of header can dwarf a tiny payload. */
    private static final long RATIO_CHECK_FLOOR_BYTES = 4096;

    private static final int COPY_BUFFER_BYTES = 8192;

    /**
     * Reads every entry of {@code fileBytes} in physical stream order, under the production limits.
     *
     * @param fileBytes the whole archive's bytes
     * @return every entry, in the order encountered; never empty
     * @throws CorruptContainerException if the bytes are not readable as a zip stream even in the legacy
     *     entry-name code page, if the archive is empty, or if it exceeds a resource limit
     */
    public static List<RawEntry> readAll(byte[] fileBytes) {
        return readAll(fileBytes, Limits.production());
    }

    /**
     * Reads every entry under caller-supplied limits.
     *
     * <p>Package-private and used only by this class's own tests, for a reason worth stating: the total-size
     * limit cannot be proven at its production value without actually inflating half a gigabyte, which exhausts
     * the test JVM rather than the reader and so proves nothing about the guard. Tests drive the boundary with
     * small limits here, and separately prove the production limits are really wired by refusing an
     * implausibly-compressed archive through {@link #readAll(byte[])}.
     *
     * @param fileBytes the whole archive's bytes
     * @param limits the caps to enforce while inflating
     * @return every entry, in the order encountered; never empty
     */
    static List<RawEntry> readAll(byte[] fileBytes, Limits limits) {
        List<RawEntry> entries;
        try {
            entries = readWith(fileBytes, StandardCharsets.UTF_8, limits);
        } catch (IOException e) {
            entries = readWithLegacyEntryNames(fileBytes, limits, e);
        }
        if (entries.isEmpty()) {
            throw new CorruptContainerException("Archive contains no entries");
        }
        return entries;
    }

    /**
     * The Cp437 retry. Only a decoding failure gets a second chance — a limit violation is a
     * {@link CorruptContainerException} rather than an {@link IOException} and propagates immediately, so a
     * crafted archive cannot buy a second full inflation pass by also being undecodable.
     */
    private static List<RawEntry> readWithLegacyEntryNames(byte[] fileBytes, Limits limits, IOException first) {
        try {
            return readWith(fileBytes, LEGACY_ENTRY_NAME_CHARSET, limits);
        } catch (IOException retryFailure) {
            first.addSuppressed(retryFailure);
            throw new CorruptContainerException("Not a readable zip archive", first);
        }
    }

    private static List<RawEntry> readWith(byte[] fileBytes, Charset entryNameCharset, Limits limits)
            throws IOException {
        final List<RawEntry> entries = new ArrayList<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(fileBytes), entryNameCharset)) {
            long totalUncompressed = 0;
            ZipEntry entry = zip.getNextEntry();
            while (entry != null) {
                refuseIfTooManyEntries(entries.size(), limits);
                final byte[] content = readBounded(zip, limits.maxTotalUncompressedBytes() - totalUncompressed, limits);
                totalUncompressed += content.length;
                refuseIfRatioImplausible(entry, content.length, limits);
                entries.add(new RawEntry(entry.getName(), entries.size(), entry.getMethod(), content));
                entry = zip.getNextEntry();
            }
        }
        return entries;
    }

    private static void refuseIfTooManyEntries(int soFar, Limits limits) {
        if (soFar >= limits.maxEntries()) {
            throw new CorruptContainerException("Archive declares more than " + limits.maxEntries() + " entries");
        }
    }

    /**
     * Inflates at most {@code budget} bytes, failing as soon as the budget is exhausted rather than after — a
     * check that runs once the bytes are already in memory has already lost.
     */
    private static byte[] readBounded(InputStream source, long budget, Limits limits) throws IOException {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        final byte[] buffer = new byte[COPY_BUFFER_BYTES];
        long remaining = budget;
        int read = source.read(buffer);
        while (read > 0) {
            remaining -= read;
            if (remaining < 0) {
                throw new CorruptContainerException(
                        "Archive expands beyond the " + limits.maxTotalUncompressedBytes() + " byte limit");
            }
            out.write(buffer, 0, read);
            read = source.read(buffer);
        }
        return out.toByteArray();
    }

    /**
     * Checks one entry's compression ratio. {@link ZipInputStream} fills in the compressed size only once the
     * entry has been read to its end, and leaves it {@code -1} when the archive declares none — in that case the
     * total-size budget above is the guard, and this check abstains rather than guessing.
     */
    private static void refuseIfRatioImplausible(ZipEntry entry, int uncompressedBytes, Limits limits) {
        final long compressed = entry.getCompressedSize();
        if (compressed <= 0 || uncompressedBytes < RATIO_CHECK_FLOOR_BYTES) {
            return;
        }
        if (uncompressedBytes / compressed > limits.maxCompressionRatio()) {
            throw new CorruptContainerException(
                    "Archive entry compresses beyond a plausible ratio: " + entry.getName());
        }
    }

    /**
     * The caps enforced while inflating a container.
     *
     * @param maxEntries the largest plausible entry count
     * @param maxTotalUncompressedBytes the total inflated size the whole archive may reach
     * @param maxCompressionRatio the largest plausible inflated-to-stored ratio for any single entry
     */
    record Limits(int maxEntries, long maxTotalUncompressedBytes, long maxCompressionRatio) {

        /** Generous against a real book (the surveyed corpus averages ~64 entries), fatal to a crafted one. */
        private static final int PRODUCTION_MAX_ENTRIES = 10_000;

        /** 256 MiB of inflated content — an order of magnitude above any real book, far below a bomb. */
        private static final long PRODUCTION_MAX_TOTAL_BYTES = 256L * 1024 * 1024;

        /** Prose and XML compress around 5:1 and 10:1; a decompression bomb starts around 1000:1. */
        private static final long PRODUCTION_MAX_RATIO = 200;

        Limits {
            if (maxEntries <= 0 || maxTotalUncompressedBytes <= 0 || maxCompressionRatio <= 0) {
                throw new IllegalArgumentException("Every limit must be positive");
            }
        }

        /**
         * The limits every production read uses.
         *
         * @return the production limits
         */
        static Limits production() {
            return new Limits(PRODUCTION_MAX_ENTRIES, PRODUCTION_MAX_TOTAL_BYTES, PRODUCTION_MAX_RATIO);
        }
    }
}
