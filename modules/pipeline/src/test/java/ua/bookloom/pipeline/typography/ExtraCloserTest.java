package ua.bookloom.pipeline.typography;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import ua.bookloom.pipeline.checks.QuotePair;

/** The extra first closer of split speech, and the shapes that must stay untouched. */
class ExtraCloserTest {

    private static final QuotePair GUILLEMETS = new QuotePair('«', '»');

    @Test
    void apply_oneOpenerTwoClosersAroundDashNarration_dropsTheFirstCloser() {
        final Edit edit = ExtraCloser.apply("«Ходімо», — сказав Гейл, — ми запізнюємось».", GUILLEMETS);

        assertThat(edit.text()).isEqualTo("«Ходімо, — сказав Гейл, — ми запізнюємось».");
        assertThat(edit.count()).isEqualTo(1);
    }

    @Test
    void apply_ownOutput_changesNothingFurther() {
        assertThat(ExtraCloser.apply("«Ходімо, — сказав Гейл, — ми запізнюємось».", GUILLEMETS)
                        .count())
                .isZero();
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "«Ходімо», — сказав Гейл.",
                "«Ходімо», — сказав Гейл, — «ми запізнюємось».",
                "«Ходімо» — сказав Гейл, — ми запізнюємось».",
                "«Ходімо», сказав Гейл, ми запізнюємось».",
                "«Ходімо», — сказав «Гейл», — ми запізнюємось».",
                "«Ходімо», — сказав Гейл, ми запізнюємось».",
                "Ходімо», — сказав Гейл, — ми запізнюємось».",
                "«Ходімо, — сказав Гейл, — ми запізнюємось».",
            })
    void apply_otherShapes_staysUntouched(final String text) {
        assertThat(ExtraCloser.apply(text, GUILLEMETS).text()).isEqualTo(text);
    }

    @Test
    void normalise_ukrainianSplitSpeechWithExtraCloser_dropsItAndReportsTheRule() {
        final Normalisation result = TypographyNormalizer.normalise(
                "“Come on,” said Hale, “we are late.”", "«Ходімо», — сказав Гейл, — ми запізнюємось».", "uk");

        assertThat(result.text()).isEqualTo("«Ходімо, — сказав Гейл, — ми запізнюємось».");
    }
}
