package ua.bookloom.document.detect;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * The charset ladder — byte-order mark, then declaration, then detection, then UTF-8.
 */
class CharsetLadderTest {

    private static final Charset WINDOWS_1251 = Charset.forName("windows-1251");
    private static final Charset WINDOWS_1252 = Charset.forName("windows-1252");

    private static final String CYRILLIC_PROSE = """
            Он бегал по улице, крича во весь голос, и никто не обращал на него внимания.
            Дождь шёл третий день подряд, и город казался серым насквозь и навсегда.
            Вечером она вышла на балкон и долго смотрела, как гаснут окна напротив.
            """;

    /** A single-line Cyrillic "foreign-word" region, the shape a real corpus book carries as site boilerplate. */
    private static final String CYRILLIC_LINE =
            "Спасибо, что скачали книгу в бесплатной электронной библиотеке Royallib.com";

    private static byte[] withUtf8Bom(String text) {
        final byte[] body = text.getBytes(StandardCharsets.UTF_8);
        final byte[] withBom = new byte[body.length + 3];
        withBom[0] = (byte) 0xEF;
        withBom[1] = (byte) 0xBB;
        withBom[2] = (byte) 0xBF;
        System.arraycopy(body, 0, withBom, 3, body.length);
        return withBom;
    }

    // Covers: FR-DOC-TXT-1 — WHEN a file begins with a byte-order mark, THEN that mark fixes the charset and its
    // presence is recorded so export can re-emit it exactly as found.
    @Test
    void resolve_utf8ByteOrderMark_fixesTheCharsetAndIsRecorded() {
        final CharsetLadder.Resolution resolution = CharsetLadder.resolve(withUtf8Bom("Alpha.\n"));

        assertThat(resolution.charset()).isEqualTo(StandardCharsets.UTF_8);
        assertThat(resolution.hasBom()).isTrue();
        assertThat(resolution.bomLength()).isEqualTo(3);
    }

    // Covers: FR-DOC-TXT-1 — a byte-order mark outranks a contradicting in-band declaration, because a mark is a
    // fact about the bytes and a declaration is only a claim about them.
    @Test
    void resolve_bomAndContradictingDeclaration_takesTheBom() {
        final CharsetLadder.Resolution resolution = CharsetLadder.resolve(withUtf8Bom("Alpha.\n"), "windows-1251");

        assertThat(resolution.charset()).isEqualTo(StandardCharsets.UTF_8);
    }

    // Covers: FR-DOC-FB2-3 — WHEN a file declares its own encoding and carries no byte-order mark, THEN the
    // declaration outranks detection.
    @Test
    void resolve_declarationWithoutBom_outranksDetection() {
        final CharsetLadder.Resolution resolution =
                CharsetLadder.resolve(CYRILLIC_PROSE.getBytes(WINDOWS_1251), "windows-1251");

        assertThat(resolution.charset()).isEqualTo(WINDOWS_1251);
        assertThat(resolution.hasBom()).isFalse();
        assertThat(resolution.bomLength()).isZero();
    }

    // Covers: FR-DOC-TXT-1 — an undeclared, unmarked file falls through to detection and records the detected
    // charset rather than defaulting to UTF-8.
    @Test
    void resolve_undeclaredCyrillicBytes_fallsThroughToDetection() {
        final CharsetLadder.Resolution resolution = CharsetLadder.resolve(CYRILLIC_PROSE.getBytes(WINDOWS_1251));

        assertThat(resolution.charset()).isEqualTo(WINDOWS_1251);
    }

    // Covers: FR-DOC-TXT-1 — WHERE detection is reached and the bytes decode without error under more than one
    // single-byte encoding, THEN the system prefers the encoding whose decoded text is coherent in a known
    // script: 2,000 characters of ASCII French prose followed by a small windows-1251 Cyrillic region resolves
    // to windows-1251, not the Western European encoding a whole-file statistical score would otherwise pick.
    @Test
    void resolve_smallCyrillicRegionInMostlyAsciiFrenchFile_resolvesToWindows1251() {
        final byte[] fileBytes =
                concatBytes(asciiFrenchProse().getBytes(StandardCharsets.US_ASCII), cyrillicLineBytes());

        final CharsetLadder.Resolution resolution = CharsetLadder.resolve(fileBytes);

        assertThat(resolution.charset()).isEqualTo(WINDOWS_1251);
    }

    // Covers: FR-DOC-TXT-1 — the coherence preference must not drag a file with no foreign-word run away from
    // its correct Western European encoding: French prose whose only non-ASCII bytes are accented letters
    // sitting inside otherwise-ASCII words, plus windows-1252-only smart-punctuation bytes, still resolves to
    // windows-1252.
    @Test
    void resolve_frenchProseWithSmartPunctuationAndNoCyrillicBytes_resolvesToWindows1252() {
        final byte[] fileBytes = frenchProseWithSmartPunctuation().getBytes(WINDOWS_1252);

        final CharsetLadder.Resolution resolution = CharsetLadder.resolve(fileBytes);

        assertThat(resolution.charset()).isEqualTo(WINDOWS_1252);
    }

    /**
     * Pins the region-detection correction over a whole-file candidate re-rank. A real corpus book mixes
     * windows-1252 smart punctuation (curly quotes, an em dash, an ellipsis) that decodes to the identical code
     * point under every Western European single-byte candidate — so it votes for none of them and cannot be
     * distinguished by re-ranking ICU's whole-file candidate list, which does not even list windows-1251 for
     * such a file — with a small windows-1251 Cyrillic region. Detecting over the foreign-word runs themselves,
     * rather than re-ranking the whole-file list, is what still resolves this to windows-1251.
     */
    // Covers: FR-DOC-TXT-1 — WHERE detection is reached and the bytes decode without error under more than one
    // single-byte encoding, THEN the system prefers the encoding whose decoded text is coherent in a known
    // script, even when the file also carries non-distinguishing windows-1252 smart-punctuation bytes.
    @Test
    void resolve_frenchProseWithSmartPunctuationAndCyrillicLine_resolvesToWindows1251() {
        final byte[] fileBytes =
                concatBytes(frenchProseWithSmartPunctuation().getBytes(WINDOWS_1252), cyrillicLineBytes());

        final CharsetLadder.Resolution resolution = CharsetLadder.resolve(fileBytes);

        assertThat(resolution.charset()).isEqualTo(WINDOWS_1251);
    }

    // Covers: FR-DOC-TXT-1 — WHEN detection is inconclusive, THEN the resolution falls back to UTF-8.
    @Test
    void resolve_bytesDetectionCannotJudge_fallsBackToUtf8() {
        assertThat(CharsetLadder.resolve(new byte[0]).charset()).isEqualTo(StandardCharsets.UTF_8);
    }

    /** At least 2,000 characters of French prose with no accented letters, so every byte is plain ASCII. */
    private static String asciiFrenchProse() {
        final String sentence = "Le chat noir traverse la rue sans regarder ni a gauche ni a droite, "
                + "pendant que les enfants jouent tranquillement dans le jardin voisin. ";
        final StringBuilder prose = new StringBuilder();
        while (prose.length() < 2000) {
            prose.append(sentence);
        }
        return prose.toString();
    }

    /**
     * At least 2,000 characters of French prose whose accented letters always sit inside otherwise-ASCII
     * words, plus windows-1252-only bytes: curly quotes ({@code “ ”}, 0x93/0x94), an em dash ({@code —}, 0x97)
     * and an ellipsis ({@code …}, 0x85) — bytes ISO-8859-1 does not share, so the fixture actually distinguishes
     * the two candidates rather than passing for a reason unrelated to this change.
     */
    private static String frenchProseWithSmartPunctuation() {
        final String sentence = "Il était une fois un petit village où les habitants vivaient tranquillement. "
                + "Le maire annonça : “Nous devons agir maintenant” — personne n'osa protester… ";
        final StringBuilder prose = new StringBuilder();
        while (prose.length() < 2000) {
            prose.append(sentence);
        }
        return prose.toString();
    }

    /** {@link #CYRILLIC_LINE}, windows-1251-encoded, with a leading newline separating it from prior prose. */
    private static byte[] cyrillicLineBytes() {
        return ("\n" + CYRILLIC_LINE).getBytes(WINDOWS_1251);
    }

    private static byte[] concatBytes(byte[] first, byte[] second) {
        final byte[] combined = new byte[first.length + second.length];
        System.arraycopy(first, 0, combined, 0, first.length);
        System.arraycopy(second, 0, combined, first.length, second.length);
        return combined;
    }

    /**
     * A charset name comes out of a book file, which is untrusted input. An unknown or malformed one has to read
     * as absence — the next rung down — rather than crash the import.
     */
    @ParameterizedTest
    @CsvSource({"'not-a-charset'", "'   '", "'utf/8'"})
    void resolve_unusableDeclaredCharsetName_fallsThroughRatherThanFailing(String declared) {
        assertThat(CharsetLadder.resolve("Plain ASCII prose.\n".getBytes(StandardCharsets.US_ASCII), declared)
                        .charset())
                .isNotNull();
    }

    @Test
    void detect_cyrillicWindows1251_reportsThatCharsetWithConfidence() {
        final CharsetLadder.Detection detection = CharsetLadder.detect(CYRILLIC_PROSE.getBytes(WINDOWS_1251));

        assertThat(detection).isNotNull();
        assertThat(detection.charset()).isEqualTo(WINDOWS_1251);
        assertThat(detection.confidence()).isPositive();
    }

    @Test
    void resolution_contradictoryBomFlagAndLength_isRejected() {
        assertThatThrownBy(() -> new CharsetLadder.Resolution(StandardCharsets.UTF_8, true, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CharsetLadder.Resolution(StandardCharsets.UTF_8, false, 3))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void resolve_utf16ByteOrderMark_isRecognisedInBothByteOrders() {
        final byte[] littleEndian = {(byte) 0xFF, (byte) 0xFE, 'A', 0};
        final byte[] bigEndian = {(byte) 0xFE, (byte) 0xFF, 0, 'A'};

        assertThat(CharsetLadder.resolve(littleEndian).charset()).isEqualTo(StandardCharsets.UTF_16LE);
        assertThat(CharsetLadder.resolve(bigEndian).charset()).isEqualTo(StandardCharsets.UTF_16BE);
    }

    // Covers: FR-DOC-TXT-1 — IF a file with no byte-order mark is detected as UTF-8, THEN the single-byte
    // coherence preference does not fire and the resolved charset stays UTF-8.
    //
    // The regression this pins: a UTF-8 multi-byte sequence IS, byte for byte, a run of two or more consecutive
    // bytes >= 0x80 — the exact foreign-word-run signature the preference keys on — and under windows-1251 those
    // bytes decode to all-Cyrillic letters and score a perfect 1.0. A UTF-8 Markdown file in the local corpus,
    // which ICU identifies at confidence 100, was resolved as windows-1251 before this guard, which would encode
    // every unrepresentable character of a translation as a question mark. It is data-dependent — a second,
    // equally Cyrillic UTF-8 file did not flip, because some of its bytes land on windows-1251 punctuation and
    // fail the all-letters test — so the fixture below must be one whose bytes DO decode to letters throughout.
    @Test
    void resolve_utf8CyrillicProseWithNoByteOrderMark_staysUtf8() {
        final byte[] utf8Cyrillic = CYRILLIC_PROSE.getBytes(StandardCharsets.UTF_8);

        assertThat(CharsetLadder.resolve(utf8Cyrillic).charset()).isEqualTo(StandardCharsets.UTF_8);
    }
}
