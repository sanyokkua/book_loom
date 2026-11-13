package ua.bookloom.document.md;

import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Objects;

/**
 * The open Markdown document's state: the original bytes, never mutated, plus what it was read with.
 *
 * @param originalBytes the source file's bytes exactly as read — the buffer every {@code ByteSpanAnchor} indexes
 *     and the one reassembly copies from
 * @param charset the encoding the text was decoded with, and the one a target string is encoded back into
 * @param slots how the auxiliary segments are written back
 */
@SuppressWarnings("ArrayRecordComponent") // copied on entry and exit; equality reads the content, toString the length.
record ParsedMarkdown(byte[] originalBytes, Charset charset, MarkdownSlots slots) {
    @Override
    public boolean equals(final Object other) {
        return other instanceof ParsedMarkdown(var oOriginalBytes, var oCharset, var oSlots)
                && Arrays.equals(originalBytes, oOriginalBytes)
                && Objects.equals(charset, oCharset)
                && Objects.equals(slots, oSlots);
    }

    @Override
    public int hashCode() {
        return Objects.hash(Arrays.hashCode(originalBytes), charset, slots);
    }

    @Override
    public String toString() {
        return "ParsedMarkdown[originalBytes=" + originalBytes.length + ", charset=" + charset + ", slots=" + slots
                + "]";
    }

    /** Defensively copies so neither this record nor a caller can mutate the buffer the anchors index. */
    ParsedMarkdown {
        Objects.requireNonNull(originalBytes, "originalBytes");
        Objects.requireNonNull(charset, "charset");
        Objects.requireNonNull(slots, "slots");
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
