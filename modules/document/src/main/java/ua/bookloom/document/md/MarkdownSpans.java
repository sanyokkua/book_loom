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
 * Turns a CommonMark block into the byte span of the text a translation may replace, and holds the package's
 * shared source-span arithmetic — the span accessors, the span-carrying-child lookups and the hard-line-break
 * trailing-space derivation that {@link MarkdownMasker}, {@link MarkdownEscaper} and {@link HardLineBreakDeletion}
 * all need. They live here in one copy because they existed as three hand-copied ones that had already drifted
 * apart: two of them asked a span-less node for its first span and threw {@code ArrayIndexOutOfBoundsException},
 * the third guarded against it.
 *
 * <p><strong>The span is the union of the block's inline spans, not the block's own.</strong> A block's own span
 * includes its marker — a heading's span starts at the {@code #}, a table cell's includes the padding spaces
 * around the text, a setext heading's includes the underline. Replacing that range would delete the marker from
 * every heading in a translated book. The inline children are the block's <em>content</em>, which is exactly what
 * {@code sourceInner} and {@code targetInner} have always meant.
 *
 * <p>The span is then trimmed of whitespace at both ends, so a hard line break — two trailing spaces, of which the
 * surveyed corpus has nine genuine instances — stays outside the replaced range and survives translation.
 *
 * <p><strong>Which nodes can report no span at all.</strong> Measured over 400,000 parses on this project's pinned
 * commonmark 0.24.0, the only node types that ever report an empty source-span list are {@code HardLineBreak} (the
 * two-trailing-spaces spelling only), {@code SoftLineBreak}, {@code TableCell} and {@code Paragraph}. Every
 * span-less guard in this package exists for one of those four; a guard anywhere else would be dead code.
 */
// Checkstyle's HideUtilityClassConstructor parses source text before Lombok's annotation processor runs,
// so it cannot see the private constructor @NoArgsConstructor generates below; suppressed per the escape
// hatch checkstyle.xml documents for exactly this case (java-coding-style.md, ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class MarkdownSpans {

    /** The only character CommonMark accepts two or more of to spell a hard line break as trailing whitespace. */
    private static final char TRAILING_SPACE = ' ';

    /**
     * The input index {@code node}'s first source span starts at.
     *
     * @param node a node the caller has already established carries at least one source span — see this class's
     *     Javadoc for the four types that can report none
     * @return the character offset the node begins at
     */
    static int firstSpanStart(Node node) {
        return node.getSourceSpans().get(0).getInputIndex();
    }

    /**
     * The input index one past the end of {@code node}'s last source span.
     *
     * @param node a node the caller has already established carries at least one source span
     * @return the exclusive character offset the node ends at
     */
    static int lastSpanEnd(Node node) {
        final List<SourceSpan> spans = node.getSourceSpans();
        final SourceSpan last = spans.get(spans.size() - 1);
        return last.getInputIndex() + last.getLength();
    }

    /**
     * The first child carrying a source span, or {@code null} when no child carries one.
     *
     * <p>Not simply {@link Node#getFirstChild()}: measured on commonmark 0.24.0, a {@code SoftLineBreak} always
     * reports an <strong>empty</strong> span list, and so does the two-trailing-spaces spelling of a hard line
     * break. Either can sit at the edge of a paired construct — an ordinary Markdown link whose label is wrapped
     * across a line, {@code [label\n](url)}, ends in one — and asking it for a span index threw
     * {@code ArrayIndexOutOfBoundsException} out of {@link #firstSpanStart(Node)}, which the port reported as
     * {@code ErrorCode.internal} and which made the whole book impossible to open.
     *
     * <p>The delimiter derivation needs the first and last child that actually occupy source, so a span-less child
     * at either edge is stepped over and its characters fall inside the delimiter fragment instead — lossless,
     * because the fragment is restored verbatim.
     *
     * @param node the paired construct whose children are searched
     * @return the first child with at least one source span, or {@code null} if none has one
     */
    static @Nullable Node firstSpannedChild(Node node) {
        for (Node child = node.getFirstChild(); child != null; child = child.getNext()) {
            if (!child.getSourceSpans().isEmpty()) {
                return child;
            }
        }
        return null;
    }

    /**
     * The last child carrying a source span, or {@code null} when no child carries one — the closing-delimiter
     * counterpart of {@link #firstSpannedChild(Node)}, and span-less for the same measured reasons.
     *
     * @param node the paired construct whose children are searched
     * @return the last child with at least one source span, or {@code null} if none has one
     */
    static @Nullable Node lastSpannedChild(Node node) {
        Node found = null;
        for (Node child = node.getFirstChild(); child != null; child = child.getNext()) {
            if (!child.getSourceSpans().isEmpty()) {
                found = child;
            }
        }
        return found;
    }

    /**
     * The range of trailing spaces at the end of {@code precedingText}'s last source span — the characters that
     * spell a hard line break the {@code HardLineBreak} node itself carries no span for.
     *
     * <p><strong>An earlier draft specified "that node's span end minus its literal length", and that formula is
     * wrong — do not restore it.</strong> Measured against this project's pinned commonmark 0.24.0, a {@code Text}
     * node's literal has already been un-escaped and un-referenced, so its length differs from the span's whenever
     * the text contains a backslash escape or a character reference — and the arithmetic is wrong a second way
     * besides, because the literal occupies the span's head, not its tail: for source {@code A \* B  } the span is
     * {@code [0,8)} and the literal is {@code A * B} (length 5), so {@code span-end - literal-length} yields
     * {@code [3,8)} = {@code "* B  "}, not the two trailing spaces. The correct derivation reads the raw substring
     * the preceding node's <em>last</em> source span covers and strips its trailing spaces — a tab never appears
     * here, because CommonMark requires two or more literal spaces for this spelling; a single space plus a tab
     * parses as a soft line break instead.
     *
     * @param precedingText the node immediately before the span-less hard line break
     * @param text the text the spans index
     * @return the half-open range of trailing spaces, empty when the span ends in none, or {@code null} when
     *     {@code precedingText} carries no source span to derive it from
     */
    static int @Nullable [] trailingSpacesRange(Node precedingText, String text) {
        final List<SourceSpan> spans = precedingText.getSourceSpans();
        if (spans.isEmpty()) {
            return null;
        }
        final SourceSpan lastSpan = spans.get(spans.size() - 1);
        final int start = lastSpan.getInputIndex();
        final int end = start + lastSpan.getLength();
        final int spaces = trailingSpaceCount(text, start, end);
        return new int[] {end - spaces, end};
    }

    private static int trailingSpaceCount(String text, int start, int end) {
        int count = 0;
        while (end - count - 1 >= start && text.charAt(end - count - 1) == TRAILING_SPACE) {
            count++;
        }
        return count;
    }

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
