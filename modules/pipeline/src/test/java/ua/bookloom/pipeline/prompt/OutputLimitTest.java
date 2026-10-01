package ua.bookloom.pipeline.prompt;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** The expected length and the hard cap a call states for one masked source. */
class OutputLimitTest {

    @Test
    void forSource_shortSourceWithTwoTokens_expectsSixteenAndCapsAtTheFloor() {
        final OutputLimit limit = OutputLimit.forSource("He opened the ⟦g0⟧old⟦g1⟧ door.", "en", "uk");

        assertThat(limit).isEqualTo(new OutputLimit(16, 64));
    }

    @Test
    void forSource_sixHundredCharactersWithFourTokens_capsAtSixHundredSixtyOne() {
        final String masked = "⟦g0⟧⟦g1⟧" + "a".repeat(600) + "⟦g2⟧⟦g3⟧";

        assertThat(OutputLimit.forSource(masked, "en", "uk")).isEqualTo(new OutputLimit(414, 661));
    }

    @Test
    void forSource_emptyDisplayText_answersNull() {
        assertThat(OutputLimit.forSource("⟦g0⟧ ⟦g1⟧", "en", "uk")).isNull();
    }

    @Test
    void forJudge_onePair_expectsOneHundredSixtyAndCapsAtThreeHundredTwenty() {
        assertThat(OutputLimit.forJudge(1)).isEqualTo(new OutputLimit(160, 320));
    }

    @Test
    void forJudge_threePairs_expectsThreeHundredFiftyTwoAndCapsAtSevenHundredFour() {
        assertThat(OutputLimit.forJudge(3)).isEqualTo(new OutputLimit(352, 704));
    }

    @Test
    void forJudge_tenPairs_capsAtTenTwentyFour() {
        assertThat(OutputLimit.forJudge(10)).isEqualTo(new OutputLimit(512, 1024));
    }
}
