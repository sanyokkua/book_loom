package ua.bookloom.pipeline.chunk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

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

    @ParameterizedTest
    @CsvSource({"16,0,64", "41,2,90", "100,0,166", "414,4,661", "2880,0,4336"})
    void outputCap_allowanceAndTokens_matchesTheFormula(final int allowance, final int tokenCount, final int expected) {
        assertThat(TokenEstimator.outputCap(allowance, tokenCount)).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({"en,uk,2.4", "uk,en,1.875", "en,en,1.7", "en,xx,3.3333333333333335"})
    void outputRatio_languagePair_isBandUpperTimesCharsPerTokenRatio(
            final String from, final String to, final double expected) {
        assertThat(TokenEstimator.outputRatio(from, to)).isCloseTo(expected, within(1e-9));
    }
}
