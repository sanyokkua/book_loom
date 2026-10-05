package ua.bookloom.pipeline.prompt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

/** The expected length and the hard cap a call states for one masked source. */
class OutputLimitTest {

    @Test
    void forSource_shortSourceWithTwoTokens_expectsSixteenAndCapsAtTheShortSourceFloor() {
        final OutputLimit limit = OutputLimit.forSource("He opened the ⟦g0⟧old⟦g1⟧ door.", "en", "uk");

        assertThat(limit).isEqualTo(new OutputLimit(16, 128));
    }

    // A one-word source under a 64-token cap came back as an empty target; the floor leaves the reply room.
    @Test
    void forSource_oneWord_capsAtTheShortSourceFloor() {
        assertThat(OutputLimit.forSource("Well", "en", "uk")).isEqualTo(new OutputLimit(3, 128));
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
    void forReview_onePair_capsAtTheBaseAndOneEnvelopeAndTheCandidateItself() {
        // 64 base + 96 per pair + the candidate's own tokens, because a rewrite may repeat it whole.
        assertThat(OutputLimit.forReview(List.of("a".repeat(400)), "en").capTokens())
                .isEqualTo(275);
    }

    @Test
    void forReview_threePairs_growsWithEachPairAndExpectsHalfTheCap() {
        final OutputLimit one = OutputLimit.forReview(List.of("Він відчинив двері."), "uk");
        final OutputLimit three =
                OutputLimit.forReview(List.of("Він відчинив двері.", "Вона усміхнулася.", "Було тихо."), "uk");

        assertThat(three.capTokens()).isGreaterThan(one.capTokens() * 2);
        assertThat(three.expectedTokens()).isEqualTo(three.capTokens() / 2);
    }

    @Test
    void forReview_noCandidate_isRejected() {
        assertThatThrownBy(() -> OutputLimit.forReview(List.of(), "uk")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void forSummary_capsAtTenTwentyFour() {
        assertThat(OutputLimit.forSummary()).isEqualTo(new OutputLimit(600, 1024));
    }

    @Test
    void forPrescan_fortyCandidates_capsAtNineteenTwentyFour() {
        assertThat(OutputLimit.forPrescan(40)).isEqualTo(new OutputLimit(992, 1984));
    }

    @Test
    void forSuggestions_twentyTerms_capsAtNineHundredTwentyEight() {
        assertThat(OutputLimit.forSuggestions(20)).isEqualTo(new OutputLimit(464, 928));
    }
}
