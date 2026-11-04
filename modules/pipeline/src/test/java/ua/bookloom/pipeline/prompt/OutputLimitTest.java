package ua.bookloom.pipeline.prompt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ua.bookloom.api.llm.SourceSize;

/** The expected length and the hard cap a call states for one masked source. */
class OutputLimitTest {

    @Test
    void forSource_shortSourceWithTwoTokens_expectsFifteenAndCapsAtTheShortSourceFloor() {
        final OutputLimit limit = OutputLimit.forSource("He opened the ⟦g0⟧old⟦g1⟧ door.", "en", "uk");

        assertThat(limit)
                .extracting(OutputLimit::expectedTokens, OutputLimit::capTokens)
                .containsExactly(15, 128);
    }

    // A one-word source under a 64-token cap came back as an empty target; the floor leaves the reply room.
    @Test
    void forSource_oneWord_capsAtTheShortSourceFloor() {
        assertThat(OutputLimit.forSource("Well", "en", "uk"))
                .extracting(OutputLimit::expectedTokens, OutputLimit::capTokens)
                .containsExactly(3, 128);
    }

    @Test
    void forSource_sixHundredCharactersWithFourTokens_capsAtSixHundredSix() {
        final String masked = "⟦g0⟧⟦g1⟧" + "a".repeat(600) + "⟦g2⟧⟦g3⟧";

        assertThat(OutputLimit.forSource(masked, "en", "uk"))
                .extracting(OutputLimit::expectedTokens, OutputLimit::capTokens)
                .containsExactly(377, 606);
    }

    @Test
    void forSource_emptyDisplayText_answersNull() {
        assertThat(OutputLimit.forSource("⟦g0⟧ ⟦g1⟧", "en", "uk")).isNull();
    }

    @Test
    void forReview_onePair_capsAtTheBaseAndOneEnvelopeAndTheCandidateItself() {
        // 128 base + 70 per pair + 120 for a possible edit + the candidate's own tokens (113), because a rewrite may
        // repeat it whole.
        assertThat(OutputLimit.forReview(List.of("a".repeat(400)), "en").capTokens())
                .isEqualTo(431);
    }

    @ParameterizedTest
    @CsvSource({"1,431", "4,1340", "8,2552"})
    void forReview_batchSizeTable_growsByOnePairAllowanceEach(final int pairs, final int cap) {
        assertThat(OutputLimit.forReview(Collections.nCopies(pairs, "a".repeat(400)), "en")
                        .capTokens())
                .isEqualTo(cap);
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

    // The source size sizes a thinking model's reasoning reserve: the source's own tokens and one item per source.
    @Test
    void forBatch_threeSources_statesTheirTokensAndCount() {
        final OutputLimit limit = OutputLimit.forBatch(List.of("a".repeat(400), "b".repeat(400), "Well"), "en", "uk");

        // 113 tokens for each 400-letter source, 2 for the one word.
        assertThat(limit.source()).isEqualTo(new SourceSize(228, 3));
    }

    @Test
    void forSource_oneSource_statesItsTokensAndOneItem() {
        assertThat(OutputLimit.forSource("a".repeat(400), "en", "uk"))
                .extracting(OutputLimit::source)
                .isEqualTo(new SourceSize(113, 1));
    }

    @Test
    void forSummary_capsAtTenTwentyFour() {
        assertThat(OutputLimit.forSummary()).isEqualTo(new OutputLimit(600, 1024));
    }

    @Test
    void forPrescan_fortyCandidates_capsAtNineteenTwentyFour() {
        assertThat(OutputLimit.forPrescan(40)).isEqualTo(new OutputLimit(992, 1984, new SourceSize(0, 40)));
    }

    @Test
    void forSuggestions_twentyTerms_capsAtNineHundredTwentyEight() {
        assertThat(OutputLimit.forSuggestions(20)).isEqualTo(new OutputLimit(464, 928, new SourceSize(0, 20)));
    }
}
