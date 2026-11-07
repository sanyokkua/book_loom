package ua.bookloom.api.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ua.bookloom.api.project.ProofreadOutcome;

/**
 * The Proofread stage's events and its tally reject what no run could send, and a window's texts never reach a log
 * line through {@code toString}.
 */
class ProofreadEventsTest {

    private static final ProofreadItem ITEM = new ProofreadItem(
            "ch7.xhtml:42",
            "ch7 · p42",
            "Ліна сказав.",
            "Ліна сказала.",
            ProofreadOutcome.KEPT,
            "",
            List.of("gender ending against: Ліна — female"));

    @Test
    void forecast_negativeWindows_isRefused() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new ProofreadForecast(ProofreadCoverage.MARKED_AND_DOUBTFUL, -1));
    }

    @ParameterizedTest(name = "round {0} of {1}, {3} of {2} to read")
    @CsvSource({"0, 1, 4, 4", "2, 1, 4, 4", "3, 3, 4, 4", "1, 1, -1, 0", "1, 1, 4, -1", "1, 1, 4, 5"})
    void roundStarted_impossibleRoundOrCount_isRefused(
            final int round, final int rounds, final int windows, final int toRead) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new ProofreadRoundStarted(round, rounds, windows, toRead));
    }

    @ParameterizedTest(name = "round {0} is whole book: {1}")
    @CsvSource({"1, false", "2, true"})
    void roundStarted_round_isWholeBookOnlyForTheSecond(final int round, final boolean wholeBook) {
        assertThat(new ProofreadRoundStarted(round, 2, 6, 6).isWholeBook()).isEqualTo(wholeBook);
    }

    @Test
    void item_toString_leavesTheTextsOut() {
        assertThat(ITEM.toString()).contains("ch7 · p42", "KEPT").doesNotContain("Ліна");
        assertThat(ITEM.marks()).containsExactly("gender ending against: Ліна — female");
    }

    @ParameterizedTest(name = "round {0}")
    @CsvSource({"0", "3"})
    void windowRead_roundOutsideOneAndTwo_isRefused(final int round) {
        assertThatIllegalArgumentException().isThrownBy(() -> new ProofreadWindowRead(round, List.of(ITEM)));
    }

    @Test
    void windowRead_noItem_isRefused() {
        assertThatIllegalArgumentException().isThrownBy(() -> new ProofreadWindowRead(1, List.of()));
    }

    @Test
    void tally_window41Of120_holdsItsCounts() {
        final ProofreadTally tally = new ProofreadTally(1, 120, 40, 41);

        assertThat(tally.next()).isEqualTo(41);
        assertThat(tally.windows()).isEqualTo(120);
    }

    @ParameterizedTest(name = "round {0}, {2} read of {1}, next {3}")
    @CsvSource({"0, 6, 1, 2", "3, 6, 1, 2", "1, 0, 0, 1", "1, 6, -1, 1", "1, 6, 6, 1", "1, 6, 1, 0", "1, 6, 1, 7"})
    void tally_countsThatCannotHold_areRefused(final int round, final int windows, final int read, final int next) {
        assertThatIllegalArgumentException().isThrownBy(() -> new ProofreadTally(round, windows, read, next));
    }
}
