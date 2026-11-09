package ua.bookloom.pipeline.export;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.pipeline.GenderWait;
import ua.bookloom.api.pipeline.ProofreadSummary;

/** The report's proofread section says what the Proofread stage re-read and what still waits, each gender by name. */
class ConsistencySectionTest {

    @Test
    void of_nothingReadNothingWaiting_saysBothCounts() {
        assertThat(ConsistencySection.of(ProofreadSummary.NONE))
                .isEqualTo("- Re-read by the Proofread stage: 0\n- Still waiting on you: 0\n");
    }

    @Test
    void of_readAndWaiting_countsBoth() {
        assertThat(ConsistencySection.of(new ProofreadSummary(12, 3)))
                .isEqualTo("- Re-read by the Proofread stage: 12\n- Still waiting on you: 3\n");
    }

    @Test
    void of_genderWaits_namesEachCharacterAndWhetherItIsOnlySuggested() {
        final ProofreadSummary summary =
                new ProofreadSummary(4, 3, List.of(new GenderWait("Orrin", 2), new GenderWait("Veska", 1, true)), 1, 2);

        assertThat(ConsistencySection.of(summary)).isEqualTo("""
                        - Re-read by the Proofread stage: 4
                        - Still waiting on you: 3
                        - Waiting on a gender: Orrin 2
                        - Waiting on a suggested gender to confirm: Veska 1
                        """);
    }
}
