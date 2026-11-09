package ua.bookloom.pipeline.prompt;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
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
    void forChapterSummary_capsAtTwoHundred() {
        assertThat(OutputLimit.forChapterSummary()).isEqualTo(new OutputLimit(120, 200));
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
