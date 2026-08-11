package ua.bookloom.document.model;

import java.util.Objects;

/**
 * One zip entry captured at read time — name, physical stream order, compression method, and its decompressed
 * bytes (task 2.3). Every entry is captured, not only the ones this change parses: an image, a font, or a
 * stylesheet is never touched here, but its bytes and position are what a later change's repackaging carries
 * through unchanged.
 *
 * @param name the entry's path within the archive, exactly as it appears in the zip
 * @param order this entry's position in the physical zip stream, 0-based
 * @param method the entry's {@link java.util.zip.ZipEntry} compression method ({@code STORED} or {@code DEFLATED})
 * @param content this entry's decompressed bytes
 */
@SuppressWarnings("ArrayRecordComponent") // deliberate: raw binary content, defensively copied both ways below;
// equals()/hashCode() are never used for this internal, non-record-shaped comparison.
public record RawEntry(String name, int order, int method, byte[] content) {

    /**
     * Defensively copies {@code content} so neither this record nor a caller can mutate the other's array after
     * construction (a byte array is not itself immutable the way a record's other components are).
     */
    public RawEntry {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(content, "content");
        content = content.clone();
    }

    /**
     * Returns a defensive copy of this entry's decompressed bytes — never the record's own backing array.
     *
     * @return a defensive copy of this entry's decompressed bytes
     */
    @Override
    public byte[] content() {
        return content.clone();
    }
}
