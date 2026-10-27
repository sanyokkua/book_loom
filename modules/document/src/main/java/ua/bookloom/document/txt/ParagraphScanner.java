package ua.bookloom.document.txt;

import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.document.ByteSpanAnchor;
import ua.bookloom.document.model.CorruptContainerException;

/**
 * Finds a plain-text file's paragraphs at blank-line boundaries and returns them as byte spans of the file.
 *
 * <p><strong>Why an ASCII-compatible file is scanned as bytes.</strong> A line feed is {@code 0x0A} in UTF-8 and in
 * every single-byte code page a book uses, and UTF-8's continuation bytes all have the high bit set, so a
 * {@code 0x0A} byte is always a real line feed. Scanning bytes keeps the spans in the buffer's own coordinates
 * with no offset conversion to get wrong.
 *
 * <p><strong>Why a UTF-16 file is scanned as decoded text.</strong> There a line feed is two bytes, one of them
 * zero, so a byte scan never sees a blank line and the whole book became one segment. The same scan runs over the
 * decoded characters, and each boundary is turned back into a byte offset by encoding the text before it.
 *
 * <p>A run of several blank lines yields one boundary, not several empty paragraphs — real files are full of them
 * and one surveyed file has 762. Carriage returns are treated as whitespace so a CRLF file scans identically to
 * an LF one, and they are never touched: reassembly copies them from the original.
 */
// Checkstyle's HideUtilityClassConstructor parses source text before Lombok's annotation processor runs,
// so it cannot see the private constructor @NoArgsConstructor generates below; suppressed per the escape
// hatch checkstyle.xml documents for exactly this case (java-coding-style.md, ADR-0024).
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class ParagraphScanner {

    private static final char LINE_FEED = '\n';
    private static final String ASCII_WHITESPACE = " \t\r\n\f";
    private static final int BYTE_MASK = 0xFF;

    /**
     * Scans {@code fileBytes} for blank-line-separated paragraphs.
     *
     * @param fileBytes the whole file's bytes
     * @param from the first byte to scan, so a byte-order mark stays outside every span and is never replaced
     * @param charset the file's resolved charset
     * @return one byte span per paragraph, in file order, each trimmed of the whitespace around it so indentation
     *     and trailing whitespace stay outside the replaced range and survive translation
     */
    static List<ByteSpanAnchor> scan(byte[] fileBytes, int from, Charset charset) {
        Objects.requireNonNull(fileBytes, "fileBytes");
        Objects.requireNonNull(charset, "charset");
        if (isAsciiCompatible(charset)) {
            return scan(new ByteChars(fileBytes), from);
        }
        log.debug("charset {} is not ASCII-compatible; scanning the decoded text from byte {}", charset, from);
        final String text = new String(fileBytes, from, fileBytes.length - from, charset);
        return toByteSpans(scan(text, 0), text, from, charset);
    }

    private static boolean isAsciiCompatible(Charset charset) {
        return Arrays.equals(ASCII_WHITESPACE.getBytes(charset), ASCII_WHITESPACE.getBytes(StandardCharsets.US_ASCII));
    }

    private static List<ByteSpanAnchor> scan(CharSequence text, int from) {
        final List<ByteSpanAnchor> paragraphs = new ArrayList<>();
        int paragraphStart = from;
        int at = from;
        while (at < text.length()) {
            final int lineEnd = endOfLine(text, at);
            final int nextLine = Math.min(lineEnd + 1, text.length());
            if (isBlank(text, at, lineEnd)) {
                addIfNotEmpty(paragraphs, text, paragraphStart, at);
                paragraphStart = nextLine;
            }
            at = nextLine;
        }
        addIfNotEmpty(paragraphs, text, paragraphStart, text.length());
        return paragraphs;
    }

    /** Turns character spans of {@code text} into byte spans of the file, whose text starts at byte {@code from}. */
    private static List<ByteSpanAnchor> toByteSpans(
            List<ByteSpanAnchor> charSpans, String text, int from, Charset charset) {
        final CharsetEncoder encoder = charset.newEncoder();
        final List<ByteSpanAnchor> byteSpans = new ArrayList<>(charSpans.size());
        int charAt = 0;
        int byteAt = from;
        for (final ByteSpanAnchor span : charSpans) {
            final int start = byteAt + encodedLength(encoder, text, charAt, span.startInclusive());
            final int end = start + encodedLength(encoder, text, span.startInclusive(), span.endExclusive());
            byteSpans.add(new ByteSpanAnchor(start, end));
            charAt = span.endExclusive();
            byteAt = end;
        }
        return byteSpans;
    }

    private static int encodedLength(CharsetEncoder encoder, String text, int from, int toExclusive) {
        try {
            return encoder.reset()
                    .encode(CharBuffer.wrap(text, from, toExclusive))
                    .remaining();
        } catch (CharacterCodingException e) {
            throw new CorruptContainerException("Plain-text file does not re-encode in its own charset", e);
        }
    }

    private static int endOfLine(CharSequence text, int from) {
        int at = from;
        while (at < text.length() && text.charAt(at) != LINE_FEED) {
            at++;
        }
        return at;
    }

    private static boolean isBlank(CharSequence text, int from, int toExclusive) {
        for (int at = from; at < toExclusive; at++) {
            if (!isAsciiWhitespace(text.charAt(at))) {
                return false;
            }
        }
        return true;
    }

    /**
     * Trims whitespace off both ends before recording the span, so four spaces of indentation and any trailing
     * whitespace stay outside the range a translation replaces and are copied through untouched.
     */
    private static void addIfNotEmpty(List<ByteSpanAnchor> paragraphs, CharSequence text, int from, int toExclusive) {
        int start = from;
        int end = Math.min(toExclusive, text.length());
        while (start < end && isAsciiWhitespace(text.charAt(start))) {
            start++;
        }
        while (end > start && isAsciiWhitespace(text.charAt(end - 1))) {
            end--;
        }
        if (start < end) {
            paragraphs.add(new ByteSpanAnchor(start, end));
        }
    }

    /**
     * ASCII whitespace only. A byte with the high bit set is a non-ASCII character or a UTF-8 continuation byte
     * and is never whitespace, so treating it as content is both correct and what keeps the byte scan
     * encoding-blind.
     */
    private static boolean isAsciiWhitespace(char value) {
        return ASCII_WHITESPACE.indexOf(value) >= 0;
    }

    /** The file's bytes read as characters {@code 0-255}, so the byte scan and the text scan are one scan. */
    @RequiredArgsConstructor
    private static final class ByteChars implements CharSequence {

        private final byte[] bytes;

        @Override
        public int length() {
            return bytes.length;
        }

        @Override
        public char charAt(int index) {
            return (char) (bytes[index] & BYTE_MASK);
        }

        @Override
        public CharSequence subSequence(int start, int end) {
            return new ByteChars(Arrays.copyOfRange(bytes, start, end));
        }
    }
}
