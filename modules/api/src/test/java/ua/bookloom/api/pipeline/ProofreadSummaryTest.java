package ua.bookloom.api.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** The proofread summary an export keeps: counts that are never negative and a list nobody can change afterwards. */
class ProofreadSummaryTest {

    @ParameterizedTest
    @CsvSource({"-1, 0, 0, 0", "0, -1, 0, 0", "0, 0, -1, 0", "0, 0, 0, -1"})
    void constructor_negativeCount_isRejected(
            final int reRead, final int waiting, final int suggested, final int unknown) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new ProofreadSummary(reRead, waiting, List.of(), suggested, unknown));
    }

    @Test
    void constructor_twoCounts_waitsOnNoGender() {
        final ProofreadSummary summary = new ProofreadSummary(12, 3);

        assertThat(summary.awaitingGender()).isEmpty();
        assertThat(summary.suggestedGenderSegments()).isZero();
        assertThat(summary.unknownGenderSegments()).isZero();
    }

    @Test
    void constructor_listChangedAfterwards_summaryKeepsItsCopy() {
        final List<GenderWait> waits = new ArrayList<>(List.of(new GenderWait("Orrin", 2)));
        final ProofreadSummary summary = new ProofreadSummary(4, 2, waits, 0, 2);

        waits.add(new GenderWait("Veska", 1));

        assertThat(summary.awaitingGender()).containsExactly(new GenderWait("Orrin", 2));
    }

    @Test
    void none_readNothingAndWaitsOnNothing() {
        assertThat(new ProofreadSummary(0, 0, List.of(), 0, 0)).isEqualTo(ProofreadSummary.NONE);
    }
}
