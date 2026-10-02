package ua.bookloom.pipeline.qa;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** The length band of each language pair and its widening for short sources. */
class LengthBandTest {

    @ParameterizedTest
    @CsvSource({
        "en,uk,0.7,1.8",
        "en,pl,0.6,1.7",
        "uk,ru,0.6,1.7",
        "en,zh-Hans,0.2,1.0",
        "ja,en,1.0,5.0",
        "uk,el,0.5,2.5",
        ",uk,0.5,2.5",
        "xx,uk,0.5,2.5"
    })
    void forPair_pairClass_selectsItsBand(
            final String source, final String target, final double lower, final double upper) {
        assertThat(LengthBand.forPair(source, target)).isEqualTo(new LengthBand(lower, upper));
    }

    @Test
    void widenedFor_threeCharacters_halvesLowerAndDoublesUpper() {
        assertThat(LengthBand.forPair("en", "uk").widenedFor(3)).isEqualTo(new LengthBand(0.35, 3.6));
    }

    // A short line Ukrainian says compactly — "it has been falling night and day" → "вона падає ніч і день" (0.64),
    // "Distance between the centres of mass" → "Відстань між центрами мас" (0.69) — is not an omission.
    @ParameterizedTest
    @CsvSource({"25", "36", "59"})
    void widenedFor_underSixtyCharacters_lowersTheLowerBoundAndRaisesTheUpper(final int sourceChars) {
        final LengthBand band = LengthBand.forPair("en", "uk").widenedFor(sourceChars);

        assertThat(band.lower()).isCloseTo(0.595, within(1e-9));
        assertThat(band.upper()).isCloseTo(2.25, within(1e-9));
    }

    @Test
    void widenedFor_sixtyCharacters_isUnchanged() {
        assertThat(LengthBand.forPair("en", "uk").widenedFor(60)).isEqualTo(new LengthBand(0.7, 1.8));
    }
}
