package ua.bookloom.document.md;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.commonmark.node.Node;
import org.commonmark.node.SourceSpan;
import org.jspecify.annotations.Nullable;

/**
 * Turns a CommonMark block into the byte span of the text a translation may replace.
 *
 * <p><strong>The span is the union of the block's inline spans, not the block's own.</strong> A block's own span
 * includes its marker — a heading's span starts at the {@code #}, a table cell's includes the padding spaces
 * around the text, a setext heading's includes the underline. Replacing that range would delete the marker from
 * every heading in a translated book. The inline children are the block's <em>content</em>, which is exactly what
 * {@code sourceInner} and {@code targetInner} have always meant.
 *
 * <p>The span is then trimmed of whitespace at both ends, so a hard line break — two trailing spaces, of which the
 * surveyed corpus has nine genuine instances — stays outside the replaced range and survives translation.
 */
// Checkstyle's HideUtilityClassConstructor parses source text before Lombok's annotation processor runs,
// so it cannot see the private constructor @NoArgsConstructor generates below; suppressed per the escape
// hatch checkstyle.xml documents for exactly this case (java-coding-style.md, ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class MarkdownSpans {

    /**
     * The character range of {@code block}'s content within the body text.
     *
     * @param block the leaf block to measure
     * @return the half-open character range of its content, or {@code null} when it carries no positioned inline
     *     content at all — an empty block, which yields no segment anyway
     */
    static int @Nullable [] contentCharRange(Node block) {
        Objects.requireNonNull(block, "block");
        final int[] range = {Integer.MAX_VALUE, Integer.MIN_VALUE};
        widenToInlineSpans(block, range);
        return range[0] > range[1] ? null : range;
    }

    private static void widenToInlineSpans(Node parent, int[] range) {
        for (Node child = parent.getFirstChild(); child != null; child = child.getNext()) {
            widenTo(child.getSourceSpans(), range);
            widenToInlineSpans(child, range);
        }
    }

    private static void widenTo(List<SourceSpan> spans, int[] range) {
        for (final SourceSpan span : spans) {
            range[0] = Math.min(range[0], span.getInputIndex());
            range[1] = Math.max(range[1], span.getInputIndex() + span.getLength());
        }
    }

    /**
     * Trims whitespace off both ends of a character range.
     *
     * @param text the text the range indexes
     * @param range the half-open character range to trim
     * @return the trimmed range, or {@code null} when nothing but whitespace remains
     */
    static int @Nullable [] trimmed(String text, int[] range) {
        int start = range[0];
        int end = Math.min(range[1], text.length());
        while (start < end && Character.isWhitespace(text.charAt(start))) {
            start++;
        }
        while (end > start && Character.isWhitespace(text.charAt(end - 1))) {
            end--;
        }
        return start >= end ? null : new int[] {start, end};
    }

    /**
     * Maps every character offset in {@code text} to its byte offset under {@code charset}.
     *
     * <p>Built as one array rather than computed per query because a book has thousands of segments and encoding
     * a prefix per segment would be quadratic. UTF-8 and single-byte code pages — which is every encoding a real
     * Markdown file uses — are computed arithmetically; anything else falls back to encoding one code point at a
     * time, which is slow but correct and never reached in practice.
     *
     * @param text the decoded text
     * @param charset the encoding the text was decoded with
     * @return an array of length {@code text.length() + 1} mapping each character index to its byte offset
     */
    static int[] byteOffsets(String text, Charset charset) {
        final int[] offsets = new int[text.length() + 1];
        if (StandardCharsets.UTF_8.equals(charset)) {
            fillUtf8(text, offsets);
        } else if (charset.newEncoder().maxBytesPerChar() == 1.0f) {
            fillSingleByte(offsets);
        } else {
            fillByEncoding(text, charset, offsets);
        }
        return offsets;
    }

    private static void fillUtf8(String text, int[] offsets) {
        int byteAt = 0;
        int i = 0;
        while (i < text.length()) {
            final int codePoint = text.codePointAt(i);
            final int chars = Character.charCount(codePoint);
            recordSpanStart(offsets, i, chars, byteAt);
            byteAt += utf8Length(codePoint);
            i += chars;
        }
        offsets[text.length()] = byteAt;
    }

    private static int utf8Length(int codePoint) {
        final int oneByteMax = 0x7F;
        final int twoByteMax = 0x7FF;
        final int threeByteMax = 0xFFFF;
        if (codePoint <= oneByteMax) {
            return 1;
        }
        if (codePoint <= twoByteMax) {
            return 2;
        }
        return codePoint <= threeByteMax ? 3 : 4;
    }

    private static void fillSingleByte(int[] offsets) {
        for (int i = 0; i < offsets.length; i++) {
            offsets[i] = i;
        }
    }

    private static void fillByEncoding(String text, Charset charset, int[] offsets) {
        int byteAt = 0;
        int i = 0;
        while (i < text.length()) {
            final int codePoint = text.codePointAt(i);
            final int chars = Character.charCount(codePoint);
            recordSpanStart(offsets, i, chars, byteAt);
            byteAt += new String(Character.toChars(codePoint)).getBytes(charset).length;
            i += chars;
        }
        offsets[text.length()] = byteAt;
    }

    /** Both halves of a surrogate pair map to the code point's start, so no offset can land mid-character. */
    private static void recordSpanStart(int[] offsets, int charIndex, int chars, int byteAt) {
        for (int k = 0; k < chars; k++) {
            offsets[charIndex + k] = byteAt;
        }
    }
}
