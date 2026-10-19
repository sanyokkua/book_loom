package ua.bookloom.pipeline.typography;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** The 26b ch4 p112 shape, invented (15h.E1): split speech with one opening and two closing guillemets. */
class SplitSpeechExtraCloserTest {

    private static final String SOURCE = "“Come on,” said Hale, “we are late.”";

    // IF the first closer stayed, THEN the quote reads as closed before the narration and the pair never balances.
    @Test
    void repair_splitSpeechWithAnExtraFirstCloser_losesThatCloser() {
        final QuoteRepair.Repaired repaired =
                QuoteRepair.repair(SOURCE, "«Ходімо», — сказав Гейл, — ми запізнюємось».", "en", "uk");

        assertThat(repaired.text()).isEqualTo("«Ходімо, — сказав Гейл, — ми запізнюємось».");
    }
}
