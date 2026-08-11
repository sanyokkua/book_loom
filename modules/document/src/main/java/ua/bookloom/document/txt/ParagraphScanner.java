package ua.bookloom.document.txt;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import ua.bookloom.api.document.ByteSpanAnchor;

/**
 * Finds a plain-text file's paragraphs by scanning the byte buffer for blank-line boundaries.
 *
 * <p><strong>Why this works on bytes and needs no decoding.</strong> A line feed is {@code 0x0A} in UTF-8 and in
 * every single-byte code page a book uses, and UTF-8's continuation bytes all have the high bit set, so a
 * {@code 0x0A} byte is always a real line feed and never part of another character. Scanning bytes therefore
 * keeps the spans in the same coordinate space as the buffer they index, with no offset conversion to get wrong.
 *
 * <p>A run of several blank lines yields one boundary, not several empty paragraphs — real files are full of them
 * and one surveyed file has 762. Carriage returns are treated as whitespace so a CRLF file scans identically to
 * an LF one, and they are never touched: reassembly copies them from the original.
 */
// Checkstyle's HideUtilityClassConstructor parses source text before Lombok's annotation processor runs,
// so it cannot see the private constructor @NoArgsConstructor generates below; suppressed per the escape
// hatch checkstyle.xml documents for exactly this case (java-coding-style.md, ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class ParagraphScanner {

    private static final byte LINE_FEED = (byte) '\n';

    /**
     * Scans {@code fileBytes} for blank-line-separated paragraphs.
     *
     * @param fileBytes the whole file's bytes
     * @param from the first byte to scan, so a byte-order mark stays outside every span and is never replaced
     * @return one span per paragraph, in file order, each trimmed of the whitespace around it so indentation and
     *     trailing whitespace stay outside the replaced range and survive translation
     */
    static List<ByteSpanAnchor> scan(byte[] fileBytes, int from) {
        Objects.requireNonNull(fileBytes, "fileBytes");
        final List<ByteSpanAnchor> paragraphs = new ArrayList<>();
        int paragraphStart = from;
        int at = from;
        while (at < fileBytes.length) {
            final int lineEnd = endOfLine(fileBytes, at);
            final int nextLine = Math.min(lineEnd + 1, fileBytes.length);
            if (isBlank(fileBytes, at, lineEnd)) {
                addIfNotEmpty(paragraphs, fileBytes, paragraphStart, at);
                paragraphStart = nextLine;
            }
            at = nextLine;
        }
        addIfNotEmpty(paragraphs, fileBytes, paragraphStart, fileBytes.length);
        return paragraphs;
    }

    private static int endOfLine(byte[] fileBytes, int from) {
        int at = from;
        while (at < fileBytes.length && fileBytes[at] != LINE_FEED) {
            at++;
        }
        return at;
    }

    private static boolean isBlank(byte[] fileBytes, int from, int toExclusive) {
        for (int at = from; at < toExclusive; at++) {
            if (!isAsciiWhitespace(fileBytes[at])) {
                return false;
            }
        }
        return true;
    }

    /**
     * Trims whitespace off both ends before recording the span, so four spaces of indentation and any trailing
     * whitespace stay outside the range a translation replaces and are copied through untouched.
     */
    private static void addIfNotEmpty(List<ByteSpanAnchor> paragraphs, byte[] fileBytes, int from, int toExclusive) {
        int start = from;
        int end = Math.min(toExclusive, fileBytes.length);
        while (start < end && isAsciiWhitespace(fileBytes[start])) {
            start++;
        }
        while (end > start && isAsciiWhitespace(fileBytes[end - 1])) {
            end--;
        }
        if (start < end) {
            paragraphs.add(new ByteSpanAnchor(start, end));
        }
    }

    /**
     * ASCII whitespace only. A byte with the high bit set is a non-ASCII character or a UTF-8 continuation byte
     * and is never whitespace, so treating it as content is both correct and what keeps the scan encoding-blind.
     */
    private static boolean isAsciiWhitespace(byte value) {
        return value == ' ' || value == '\t' || value == '\r' || value == '\n' || value == '\f';
    }
}
