package ua.bookloom.document.model;

import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.document.BookFormat;
import ua.bookloom.api.document.ByteSpanAnchor;

/**
 * Writes a buffer-shaped book (TXT, Markdown) whose target text is offered as characters. When every target fits
 * the encoding the source was read with, the untouched bytes are copied and only the spans are replaced; when one
 * does not, the whole document is written as UTF-8 instead — every character of the source kept, none replaced by
 * {@code ?}, and the byte-order mark present exactly when the source had one (ADR-0029). Such a file cannot declare
 * its new encoding, so the application's own re-open reads it as UTF-8.
 */
@Slf4j
// Checkstyle's HideUtilityClassConstructor parses source text before Lombok's annotation processor runs (ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class BufferTextWriter {

    private static final byte[] UTF_8_BYTE_ORDER_MARK = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
    private static final int UTF_8_MARK_LENGTH = 3;
    private static final int UTF_16_MARK_LENGTH = 2;

    /**
     * One span of the original buffer and the text to put in its place.
     *
     * @param span where the segment's source text sits in the original buffer
     * @param target the target text, as characters
     */
    public record TextReplacement(ByteSpanAnchor span, String target) {

        /** Rejects a missing span or text. */
        public TextReplacement {
            Objects.requireNonNull(span, "span");
            Objects.requireNonNull(target, "target");
        }
    }

    /**
     * Produces the output bytes for {@code original} with each replacement applied.
     *
     * @param original the source file's bytes, never mutated
     * @param charset the encoding the source was read with
     * @param hasBom whether {@code original} begins with a byte-order mark
     * @param replacements the spans to substitute, in any order
     * @param documentId the open document's id, for the log line only
     * @param format the document's format, for the log line only
     * @return the bytes to write: in {@code charset} when every target fits it, otherwise the whole document as UTF-8
     */
    public static byte[] assemble(
            byte[] original,
            Charset charset,
            boolean hasBom,
            List<TextReplacement> replacements,
            String documentId,
            BookFormat format) {
        Objects.requireNonNull(original, "original");
        Objects.requireNonNull(charset, "charset");
        Objects.requireNonNull(replacements, "replacements");
        final boolean fits = allEncodable(charset, replacements);
        log.debug("encoder over {} targets in {}: fits={}", replacements.size(), charset.name(), fits);
        if (fits) {
            return BufferReassembler.splice(original, encoded(replacements, charset));
        }
        final byte[] switched = asUtf8(original, charset, hasBom, replacements);
        log.info(
                "document {} ({}) switched from {} to UTF-8, byte-order mark written: {}",
                documentId,
                format,
                charset.name(),
                hasBom);
        return switched;
    }

    private static boolean allEncodable(Charset charset, List<TextReplacement> replacements) {
        if (!charset.canEncode()) {
            return false;
        }
        final CharsetEncoder encoder = charset.newEncoder();
        return replacements.stream().allMatch(replacement -> encoder.canEncode(replacement.target()));
    }

    private static List<BufferReassembler.Replacement> encoded(List<TextReplacement> replacements, Charset charset) {
        final List<BufferReassembler.Replacement> encoded = new ArrayList<>(replacements.size());
        for (final TextReplacement replacement : replacements) {
            encoded.add(new BufferReassembler.Replacement(
                    replacement.span(), replacement.target().getBytes(charset)));
        }
        return encoded;
    }

    /** Decodes the untouched gaps between the spans, splices the targets in as text, and encodes the whole as UTF-8. */
    private static byte[] asUtf8(byte[] original, Charset charset, boolean hasBom, List<TextReplacement> replacements) {
        final List<TextReplacement> ordered = new ArrayList<>(replacements);
        ordered.sort(Comparator.comparingInt(r -> r.span().startInclusive()));
        final StringBuilder text = new StringBuilder();
        int cursor = hasBom ? markLength(original) : 0;
        for (final TextReplacement replacement : ordered) {
            final ByteSpanAnchor span = replacement.span();
            if (span.startInclusive() < cursor) {
                throw new IllegalArgumentException("Overlapping replacement spans: " + span.startInclusive());
            }
            text.append(new String(original, cursor, span.startInclusive() - cursor, charset))
                    .append(replacement.target());
            cursor = span.endExclusive();
        }
        text.append(new String(original, cursor, original.length - cursor, charset));
        final ByteArrayOutputStream out = new ByteArrayOutputStream(original.length);
        if (hasBom) {
            out.write(UTF_8_BYTE_ORDER_MARK, 0, UTF_8_BYTE_ORDER_MARK.length);
        }
        final byte[] body = text.toString().getBytes(StandardCharsets.UTF_8);
        out.write(body, 0, body.length);
        return out.toByteArray();
    }

    private static int markLength(byte[] original) {
        final boolean utf8 = original.length >= UTF_8_MARK_LENGTH
                && original[0] == UTF_8_BYTE_ORDER_MARK[0]
                && original[1] == UTF_8_BYTE_ORDER_MARK[1]
                && original[2] == UTF_8_BYTE_ORDER_MARK[2];
        return utf8 ? UTF_8_MARK_LENGTH : UTF_16_MARK_LENGTH;
    }
}
