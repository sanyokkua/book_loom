package ua.bookloom.document.md;

import java.nio.charset.Charset;
import java.util.Objects;

/**
 * The open Markdown document's state: the original bytes, never mutated, plus what it was read with.
 *
 * @param originalBytes the source file's bytes exactly as read — the buffer every {@code ByteSpanAnchor} indexes
 *     and the one reassembly copies from
 * @param charset the encoding the text was decoded with, and the one a target string is encoded back into
 */
@SuppressWarnings("ArrayRecordComponent") // deliberate: the immutable source buffer, defensively copied both ways;
// equals()/hashCode() are never used for this internal carrier.
record ParsedMarkdown(byte[] originalBytes, Charset charset) {

    /** Defensively copies so neither this record nor a caller can mutate the buffer the anchors index. */
    ParsedMarkdown {
        Objects.requireNonNull(originalBytes, "originalBytes");
        Objects.requireNonNull(charset, "charset");
        originalBytes = originalBytes.clone();
    }

    /**
     * Returns a defensive copy of the source bytes — never the record's own backing array.
     *
     * @return a defensive copy of the source bytes
     */
    @Override
    public byte[] originalBytes() {
        return originalBytes.clone();
    }
}
