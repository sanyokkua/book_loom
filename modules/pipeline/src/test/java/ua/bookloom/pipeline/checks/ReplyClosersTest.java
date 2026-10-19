package ua.bookloom.pipeline.checks;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** The closers a model writes into the last string of its JSON are cut off; the book's own braces are not. */
class ReplyClosersTest {

    private static final String PLAIN = "He opened the old door.";

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                "Він відчинив двері.\"}|Він відчинив двері.",
                "Він відчинив двері.\"}]}|Він відчинив двері.",
                "Він відчинив двері.}]}|Він відчинив двері.",
                "Він відчинив двері. \"}|Він відчинив двері.",
                "Він відчинив двері.”}|Він відчинив двері.",
                "Він сказав: «Іди.»}|Він сказав: «Іди.»"
            })
    void strip_sourceWithoutBraces_cutsTheTrailingClosers(final String target, final String expected) {
        assertThat(ReplyClosers.strip(PLAIN, target)).contains(expected);
    }

    @Test
    void strip_closingGuillemetOfAnOpenQuote_isKept() {
        assertThat(ReplyClosers.strip(PLAIN, "Він сказав: «Іди.»}")).contains("Він сказав: «Іди.»");
    }

    @Test
    void strip_surplusClosingGuillemet_isCutWithTheCloser() {
        assertThat(ReplyClosers.strip(PLAIN, "Він пішов.»}")).contains("Він пішов.");
    }

    // IF a book that prints braces were stripped, THEN its own text would be cut.
    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {"Set {a, b} and [c]|Задайте {a, b} і [c]}", "Note [3].|Примітка [3]}"})
    void strip_sourceWithBraces_changesNothing(final String source, final String target) {
        assertThat(ReplyClosers.strip(source, target)).isEmpty();
    }

    @Test
    void strip_cleanTarget_changesNothing() {
        assertThat(ReplyClosers.strip(PLAIN, "Він відчинив двері.")).isEmpty();
    }

    @Test
    void strip_closersInTheMiddle_areLeftForTheResidueCheck() {
        assertThat(ReplyClosers.strip(PLAIN, "Він }відчинив двері.")).isEmpty();
    }

    @Test
    void strip_onlyClosers_leavesNothingToKeep() {
        assertThat(ReplyClosers.strip(PLAIN, "\"}]}")).contains("");
    }
}
