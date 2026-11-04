package ua.bookloom.pipeline.chunk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Character-based token estimates and the output allowance of one segment. */
class TokenEstimatorTest {

    @ParameterizedTest
    @CsvSource({"6000,en,1683", "26306,en,7379", "300,uk,105", "300,,115", "300,xx,115", "6000,en-US,1683", "0,en,0"})
    void estimate_repeatedCharacters_matchesTheFormula(final int length, final String tag, final int expected) {
        assertThat(TokenEstimator.estimate("a".repeat(length), tag)).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({"He opened the old door.,en,uk,15", "He opened the old door.,en,pl,11"})
    void outputAllowance_shortSource_scalesByTheBandUpperBound(
            final String source, final String from, final String to, final int expected) {
        assertThat(TokenEstimator.outputAllowance(source, from, to)).isEqualTo(expected);
    }

    @Test
    void outputAllowance_longLatinSource_isTwoThousandSixHundredEighteen() {
        assertThat(TokenEstimator.outputAllowance("a".repeat(4173), "en", "uk")).isEqualTo(2618);
    }

    @ParameterizedTest
    @CsvSource({"16,0,64", "41,2,90", "100,0,166", "414,4,661", "2880,0,4336"})
    void outputCap_allowanceAndTokens_matchesTheFormula(final int allowance, final int tokenCount, final int expected) {
        assertThat(TokenEstimator.outputCap(allowance, tokenCount)).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({"en,uk,2.2363636363636363", "uk,en,2.0121951219512195", "en,en,1.7", "en,xx,3.4166666666666665"})
    void outputRatio_languagePair_isBandUpperTimesCharsPerTokenRatio(
            final String from, final String to, final double expected) {
        assertThat(TokenEstimator.outputRatio(from, to)).isCloseTo(expected, within(1e-9));
    }

    // The spec's ru→uk system part: 990 Cyrillic characters at 3.3 per token, × 1.15, is 345 — not the Latin rate's
    // 278.
    @Test
    void estimate_cyrillicMessage_usesCyrillicRate() {
        final int tokens = TokenEstimator.estimateCall(List.of(new TokenEstimator.Message("щ".repeat(990), "uk")), 0);

        assertThat(tokens).isEqualTo(345);
    }

    // A template's overhead is added once per call, not once per message.
    @Test
    void estimate_templateOverheadAdded() {
        final List<TokenEstimator.Message> messages = List.of(
                new TokenEstimator.Message("щ".repeat(990), "uk"), new TokenEstimator.Message("a".repeat(410), "en"));

        assertThat(TokenEstimator.estimateCall(messages, 135)).isEqualTo(345 + 115 + 135);
    }
}
