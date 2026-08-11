package ua.bookloom.api.document;

/**
 * Addresses a segment inside a byte-buffer skeleton — Markdown or TXT, neither of which has a tree to point into
 * — as a half-open byte range into the source file's original bytes (ADR-0025, FR-DOC-TXT-3).
 *
 * <p><strong>What keeps these spans valid.</strong> Reassembly copies from the <em>unmodified</em> original buffer
 * into fresh output in one ascending pass and never edits that buffer in place. The moment anything wrote into the
 * buffer these index, every later span would be wrong — which is why the copy-from-original rule is part of the
 * requirement rather than an implementation detail, and why it is asserted by its own two-write test.
 *
 * @param startInclusive the first byte of the segment's source text; never negative
 * @param endExclusive one past the last byte of the segment's source text; never below {@code startInclusive}
 */
public record ByteSpanAnchor(int startInclusive, int endExclusive) implements SkeletonAnchor {

    /** Validates that the span is a well-formed half-open range; an empty span ({@code start == end}) is legal. */
    public ByteSpanAnchor {
        if (startInclusive < 0) {
            throw new IllegalArgumentException("startInclusive must be >= 0, but was " + startInclusive);
        }
        if (endExclusive < startInclusive) {
            throw new IllegalArgumentException(
                    "endExclusive must be >= startInclusive, but was " + endExclusive + " < " + startInclusive);
        }
    }

    /**
     * This span's length in bytes.
     *
     * @return the number of bytes this span covers; {@code 0} for an empty span
     */
    public int length() {
        return endExclusive - startInclusive;
    }
}
