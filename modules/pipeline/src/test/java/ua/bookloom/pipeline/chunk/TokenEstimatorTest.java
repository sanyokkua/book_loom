package ua.bookloom.pipeline.chunk;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Character-based token estimates and the output allowance of one segment. */
class TokenEstimatorTest {

    @ParameterizedTest
    @CsvSource({"6000,en,1725", "26306,en,7563", "300,uk,115", "300,,115", "300,xx,115", "6000,en-US,1725", "0,en,0"})
    void estimate_repeatedCharacters_matchesTheFormula(final int length, final String tag, final int expected) {
        assertThat(TokenEstimator.estimate("a".repeat(length), tag)).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({"He opened the old door.,en,uk,16", "He opened the old door.,en,pl,12"})
    void outputAllowance_shortSource_scalesByTheBandUpperBound(
            final String source, final String from, final String to, final int expected) {
        assertThat(TokenEstimator.outputAllowance(source, from, to)).isEqualTo(expected);
    }

    @Test
    void outputAllowance_longLatinSource_isTwoThousandEightHundredEighty() {
        assertThat(TokenEstimator.outputAllowance("a".repeat(4173), "en", "uk")).isEqualTo(2880);
    }
}
