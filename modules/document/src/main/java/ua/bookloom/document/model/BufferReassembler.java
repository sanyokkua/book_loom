package ua.bookloom.document.model;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import ua.bookloom.api.document.ByteSpanAnchor;

/**
 * Reassembles a buffer-shaped skeleton — Markdown or TXT — by copying the <strong>unmodified</strong> original
 * bytes into fresh output in one ascending pass and substituting only the spans of segments that carry target
 * text (ADR-0025, FR-DOC-TXT-3, FR-DOC-MD-4).
 *
 * <p><strong>This one rule is what the whole model rests on.</strong> Every {@link ByteSpanAnchor} indexes the
 * original buffer, so the spans stay valid for exactly one reason: nothing ever writes into that buffer. Editing
 * it in place — or writing spans out of order into a mutable builder — invalidates every later span, and does so
 * only once <em>two or more</em> segments actually carry target text, which no no-edit golden round trip
 * exercises. That is why the invariant is asserted directly by a two-write test rather than left to review.
 *
 * <p>It also makes an untranslated round trip byte-identical by construction rather than by careful
 * re-serialization: the encoding, the line endings and the byte-order mark survive because nothing re-encodes
 * them.
 */
// Checkstyle's HideUtilityClassConstructor parses source text before Lombok's annotation processor runs,
// so it cannot see the private constructor @NoArgsConstructor generates below; suppressed per the escape
// hatch checkstyle.xml documents for exactly this case (java-coding-style.md, ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class BufferReassembler {

    /**
     * Produces fresh output bytes from {@code original} with each replacement's span substituted.
     *
     * @param original the source file's bytes, never mutated by this call
     * @param replacements the spans to substitute, in any order; overlapping spans are rejected because they
     *     could only come from a walker that emitted two segments over the same text
     * @return freshly allocated output bytes; equal to {@code original} when {@code replacements} is empty
     */
    public static byte[] splice(byte[] original, List<Replacement> replacements) {
        Objects.requireNonNull(original, "original");
        Objects.requireNonNull(replacements, "replacements");
        final List<Replacement> ordered = new ArrayList<>(replacements);
        ordered.sort(Comparator.comparingInt(r -> r.span().startInclusive()));
        final ByteArrayOutputStream out = new ByteArrayOutputStream(original.length);
        int cursor = 0;
        for (final Replacement replacement : ordered) {
            cursor = appendOne(original, out, cursor, replacement);
        }
        out.write(original, cursor, original.length - cursor);
        return out.toByteArray();
    }

    private static int appendOne(byte[] original, ByteArrayOutputStream out, int cursor, Replacement replacement) {
        final ByteSpanAnchor span = replacement.span();
        if (span.startInclusive() < cursor) {
            throw new IllegalArgumentException(
                    "Overlapping replacement spans: " + span.startInclusive() + " precedes cursor " + cursor);
        }
        if (span.endExclusive() > original.length) {
            throw new IllegalArgumentException(
                    "Replacement span " + span.endExclusive() + " runs past the buffer's " + original.length);
        }
        out.write(original, cursor, span.startInclusive() - cursor);
        out.write(replacement.target(), 0, replacement.target().length);
        return span.endExclusive();
    }

    /**
     * One span of the original buffer and the bytes to put in its place.
     *
     * @param span where the segment's source text sits in the original buffer
     * @param target the encoded target text; the caller encodes it, because only the caller knows the document's
     *     charset and whether every character is representable in it
     */
    @SuppressWarnings("ArrayRecordComponent") // deliberate: raw encoded content, defensively copied both ways;
    // equals()/hashCode() are never used for this internal, non-record-shaped comparison.
    public record Replacement(ByteSpanAnchor span, byte[] target) {

        /** Defensively copies {@code target} so neither this record nor a caller can mutate the other's array. */
        public Replacement {
            Objects.requireNonNull(span, "span");
            Objects.requireNonNull(target, "target");
            target = target.clone();
        }

        /**
         * Returns a defensive copy of the encoded target bytes — never the record's own backing array.
         *
         * @return a defensive copy of the encoded target bytes
         */
        @Override
        public byte[] target() {
            return target.clone();
        }
    }
}
