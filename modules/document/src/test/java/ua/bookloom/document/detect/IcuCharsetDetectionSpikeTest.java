package ua.bookloom.document.detect;

import static org.assertj.core.api.Assertions.assertThat;

import com.ibm.icu.text.CharsetDetector;
import com.ibm.icu.text.CharsetMatch;
import java.nio.charset.Charset;
import org.junit.jupiter.api.Test;

/**
 * The spike design.md D5 required before the TXT reader was written, kept as a standing test because the question
 * it answers can regress with an ICU upgrade.
 *
 * <p><strong>Why it existed.</strong> TXT carries no declaration to cross-check against, so detection is the only
 * signal, and detection was unproven: against three real {@code windows-1251} books, {@code charset_normalizer}
 * returned {@code cp1125} and {@code chardet} returned {@code Windows-1252} — one of them at 0.637 confidence,
 * confidently wrong. If ICU4J were no better, "detect, record, proceed" would silently import mojibake, and the
 * decision had to be retaken before anything was built on it rather than after.
 *
 * <p><strong>Result: ICU4J is right.</strong> It identifies a hand-authored {@code windows-1251} CRLF sample as
 * {@code windows-1251}, so the ladder in {@link CharsetLadder} stands as designed.
 *
 * <p>The sample is built here from a Java string rather than committed as a file, for the reason in design.md
 * D8: a committed {@code windows-1251} byte sequence is one editor save or one {@code core.autocrlf} setting
 * away from being normalised into a sample that no longer tests what it was written to test — and the failure
 * mode is a <em>passing</em> test.
 */
class IcuCharsetDetectionSpikeTest {

    private static final Charset WINDOWS_1251 = Charset.forName("windows-1251");

    /**
     * Cyrillic prose with CRLF endings, long enough for a statistical detector to have something to work with —
     * the shape of a real Russian-language plain-text book.
     */
    private static final String CYRILLIC_PROSE = """
            Еней був парубок моторний і хлопець хоть куди козак.\r
            Удавсь на всеє зле проворний, завзятіший од всіх бурлак.\r
            \r
            Но греки, як спаливши Трою, зробили з неї скирту гною,\r
            він, взявши торбу, тягу дав; забравши деяких троянців,\r
            осмалених, як гиря, ланців, п'ятами з Трої накивав.\r
            \r
            Он бегал по улице, крича во весь голос, и никто не обращал внимания.\r
            Дождь шёл третий день подряд, и город казался серым насквозь.\r
            """;

    // Covers: FR-DOC-TXT-1 — WHEN a plain-text file carries no byte-order mark and no declaration, THEN the
    // encoding resolved from its bytes is the one it was actually written in rather than a Latin fallback.
    @Test
    void detect_windows1251CyrillicWithCrlf_isIdentifiedAsWindows1251() {
        final CharsetDetector detector = new CharsetDetector();
        detector.setText(CYRILLIC_PROSE.getBytes(WINDOWS_1251));

        final CharsetMatch match = detector.detect();

        assertThat(match).isNotNull();
        assertThat(Charset.forName(match.getName())).isEqualTo(WINDOWS_1251);
    }

    /**
     * The failure mode the Python detectors showed was not "unsure" but "confidently wrong", so confidence is
     * asserted alongside the name: a correct answer held with no confidence would not justify the ladder either.
     */
    @Test
    void detect_windows1251CyrillicWithCrlf_isHeldWithUsableConfidence() {
        final CharsetDetector detector = new CharsetDetector();
        detector.setText(CYRILLIC_PROSE.getBytes(WINDOWS_1251));

        assertThat(detector.detect().getConfidence()).isGreaterThan(10);
    }

    @Test
    void detect_utf8Cyrillic_isNotMistakenForASingleByteCodePage() {
        final CharsetDetector detector = new CharsetDetector();
        detector.setText(CYRILLIC_PROSE.getBytes(java.nio.charset.StandardCharsets.UTF_8));

        assertThat(detector.detect().getName()).isEqualTo("UTF-8");
    }
}
