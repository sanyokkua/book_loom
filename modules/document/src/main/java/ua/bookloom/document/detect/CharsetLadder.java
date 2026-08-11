package ua.bookloom.document.detect;

import com.ibm.icu.text.CharsetDetector;
import com.ibm.icu.text.CharsetMatch;
import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.StandardCharsets;
import java.nio.charset.UnsupportedCharsetException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;

/**
 * Resolves a book's character encoding once, on a fixed ladder: a byte-order mark, then an in-band declaration
 * where the format carries one, then ICU charset detection, then UTF-8
 * ({@code 01_Product/03_DOCUMENT_FORMATS.md#encoding-and-bom}).
 *
 * <p><strong>The order encodes evidence strength.</strong> A byte-order mark is a fact, a declaration is a claim,
 * and detection is a guess — so each rung is consulted only when every stronger one above it is absent. The mark
 * is recorded separately from the charset because re-emitting a file that had none with one added is itself a
 * change to the file.
 *
 * <p>Detection is ICU's rather than a decode attempt, because a decode attempt cannot detect anything:
 * {@code windows-1252}, {@code iso-8859-1} and {@code koi8-r} accept <em>every</em> byte, so decoding always
 * succeeds and silently produces mojibake. ICU's statistical detector was verified against a hand-authored
 * {@code windows-1251} sample before this class was written, because two common Python detectors get that case
 * confidently wrong — see {@code IcuCharsetDetectionSpikeTest}.
 */
// Checkstyle's HideUtilityClassConstructor parses source text before Lombok's annotation processor runs,
// so it cannot see the private constructor @NoArgsConstructor generates below; suppressed per the escape
// hatch checkstyle.xml documents for exactly this case (java-coding-style.md, ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class CharsetLadder {

    /** ICU reports confidence in [0,100]; below this a match is a coin toss and UTF-8 is the safer default. */
    private static final int MINIMUM_USABLE_CONFIDENCE = 10;

    private static final byte[] UTF_8_BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
    private static final byte[] UTF_16BE_BOM = {(byte) 0xFE, (byte) 0xFF};
    private static final byte[] UTF_16LE_BOM = {(byte) 0xFF, (byte) 0xFE};

    /**
     * Resolves the encoding of a file that carries no in-band declaration — TXT and Markdown.
     *
     * @param fileBytes the file's raw bytes
     * @return the resolved encoding and whether a byte-order mark was present
     */
    public static Resolution resolve(byte[] fileBytes) {
        return resolve(fileBytes, null);
    }

    /**
     * Resolves the encoding of a file, honouring an in-band declaration where the format carries one.
     *
     * @param fileBytes the file's raw bytes
     * @param declaredCharsetName the encoding the file itself declares — an FB2 XML declaration's — or
     *     {@code null} for a format that declares none
     * @return the resolved encoding and whether a byte-order mark was present
     */
    public static Resolution resolve(byte[] fileBytes, @Nullable String declaredCharsetName) {
        Objects.requireNonNull(fileBytes, "fileBytes");
        final Charset fromBom = charsetFromBom(fileBytes);
        if (fromBom != null) {
            return new Resolution(fromBom, true, bomLengthOf(fileBytes));
        }
        final Charset declared = charsetOrNull(declaredCharsetName);
        if (declared != null) {
            return new Resolution(declared, false, 0);
        }
        return new Resolution(detectOrDefault(fileBytes), false, 0);
    }

    /**
     * ICU's best guess about a file's encoding, independent of what the file claims — the input to the
     * declaration-versus-content check an FB2 needs (EC-FB2-2).
     *
     * @param fileBytes the file's raw bytes
     * @return the detected encoding and ICU's confidence in it, or {@code null} when ICU offers no match at all
     */
    public static @Nullable Detection detect(byte[] fileBytes) {
        final List<Detection> candidates = detectAll(fileBytes);
        return candidates.isEmpty() ? null : candidates.get(0);
    }

    /**
     * Every charset ICU considers possible for these bytes, best first.
     *
     * <p>The whole list rather than the winner alone, because comparing a file's declaration against a detector
     * is not a question of "what is the best guess" but of "is the declaration credible at all". A declared
     * charset ICU ranks second by a point or two is not contradicted; one ICU does not list at all is.
     *
     * @param fileBytes the bytes to judge
     * @return the candidates ICU offers, best first, skipping any this JVM has no charset for; never null,
     *     possibly empty
     */
    public static List<Detection> detectAll(byte[] fileBytes) {
        Objects.requireNonNull(fileBytes, "fileBytes");
        final CharsetDetector detector = new CharsetDetector();
        detector.setText(fileBytes);
        final List<Detection> candidates = new ArrayList<>();
        for (final CharsetMatch match : detector.detectAll()) {
            addIfKnown(candidates, match);
        }
        return candidates;
    }

    private static void addIfKnown(List<Detection> candidates, CharsetMatch match) {
        final Charset charset = charsetOrNull(match.getName());
        if (charset != null) {
            candidates.add(new Detection(charset, match.getConfidence()));
        }
    }

    /**
     * Detection, with one case decided ahead of it: <strong>pure ASCII resolves to UTF-8</strong>.
     *
     * <p>ICU will happily name a Latin-1 code page for an all-ASCII file, and it is not wrong — every ASCII file
     * is also an ISO-8859-1 file, and both decode it identically. But the resolved charset is not only used to
     * <em>decode</em> the source; it is what a translation is <em>encoded</em> with on the way out, and Latin-1
     * cannot represent Cyrillic. Accepting that answer turns an ASCII source book into one whose every translated
     * non-Latin character is written as a question mark, silently. UTF-8 decodes the same bytes to the same text
     * and can represent anything, so it is the honest resolution for a file that gives no other evidence.
     *
     * <p>Once a match clears {@link #MINIMUM_USABLE_CONFIDENCE}, {@link ForeignWordCoherence#prefer} may still
     * override the winner with a more coherent single-byte candidate; see that method.
     */
    private static Charset detectOrDefault(byte[] fileBytes) {
        if (isPureAscii(fileBytes)) {
            return StandardCharsets.UTF_8;
        }
        final List<Detection> candidates = detectAll(fileBytes);
        if (candidates.isEmpty()) {
            return StandardCharsets.UTF_8;
        }
        final Detection top = candidates.get(0);
        if (top.confidence() < MINIMUM_USABLE_CONFIDENCE) {
            return StandardCharsets.UTF_8;
        }
        final Charset preferred = ForeignWordCoherence.prefer(fileBytes, candidates);
        return preferred != null ? preferred : top.charset();
    }

    private static boolean isPureAscii(byte[] fileBytes) {
        for (final byte b : fileBytes) {
            if (b < 0) {
                return false;
            }
        }
        return true;
    }

    private static @Nullable Charset charsetFromBom(byte[] fileBytes) {
        if (startsWith(fileBytes, UTF_8_BOM)) {
            return StandardCharsets.UTF_8;
        }
        if (startsWith(fileBytes, UTF_16BE_BOM)) {
            return StandardCharsets.UTF_16BE;
        }
        if (startsWith(fileBytes, UTF_16LE_BOM)) {
            return StandardCharsets.UTF_16LE;
        }
        return null;
    }

    private static int bomLengthOf(byte[] fileBytes) {
        return startsWith(fileBytes, UTF_8_BOM) ? UTF_8_BOM.length : UTF_16BE_BOM.length;
    }

    private static boolean startsWith(byte[] fileBytes, byte[] prefix) {
        if (fileBytes.length < prefix.length) {
            return false;
        }
        return java.util.Arrays.equals(fileBytes, 0, prefix.length, prefix, 0, prefix.length);
    }

    /** A charset name from a book is untrusted input: an unknown or malformed one is absence, not a crash. */
    private static @Nullable Charset charsetOrNull(@Nullable String charsetName) {
        if (charsetName == null || charsetName.isBlank()) {
            return null;
        }
        try {
            return Charset.forName(charsetName.trim());
        } catch (IllegalCharsetNameException | UnsupportedCharsetException unknownToThisJvm) {
            return null;
        }
    }

    /**
     * What the ladder concluded about one file.
     *
     * @param charset the encoding the file's text is decoded with and re-encoded in
     * @param hasBom whether the file began with a byte-order mark, so export re-emits it exactly as found
     * @param bomLength how many leading bytes the mark occupies, so a reader can skip them without re-deriving
     *     the answer; {@code 0} when there is no mark
     */
    public record Resolution(Charset charset, boolean hasBom, int bomLength) {

        /** Validates that the mark's presence and its length agree; they are two views of one fact. */
        public Resolution {
            Objects.requireNonNull(charset, "charset");
            if (hasBom == (bomLength == 0)) {
                throw new IllegalArgumentException("hasBom=" + hasBom + " contradicts bomLength=" + bomLength);
            }
        }
    }

    /**
     * ICU's guess about a file's encoding.
     *
     * @param charset the encoding ICU believes the bytes are in
     * @param confidence ICU's confidence in [0,100]; a <em>high</em> value is what makes a disagreement with a
     *     file's own declaration worth refusing over
     */
    public record Detection(Charset charset, int confidence) {

        public Detection {
            Objects.requireNonNull(charset, "charset");
        }
    }
}
