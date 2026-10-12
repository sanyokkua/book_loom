package ua.bookloom.api.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import ua.bookloom.api.pipeline.FieldEvidence.Basis;

/**
 * What a suggested brief field stands on (15i.B4): a field stands on passages, on what the model knows of the book, or
 * on the code's own count, and says so in words; an uncertain field stands on nothing, however many quotes it has.
 */
class FieldEvidenceTest {

    // IF three of four passages were not enough to keep a field, THEN a majority would still read as uncertain.
    @ParameterizedTest
    @CsvSource({
        "PASSAGES, 3, 4, true",
        "PASSAGES, 1, 1, true",
        "KNOWLEDGE, 0, 4, true",
        "MEASURED, 0, 4, true",
        "UNCERTAIN, 2, 4, false",
        "UNCERTAIN, 4, 4, false"
    })
    void isKept_basis_isTrueUnlessUncertain(
            final Basis basis, final int agreeing, final int samples, final boolean expected) {
        assertThat(new FieldEvidence(basis, List.of(), agreeing, samples).isKept())
                .isEqualTo(expected);
    }

    // IF a quote alone kept a field, THEN a window's reading the other windows outvoted would still be offered.
    @Test
    void isKept_uncertainWithAQuote_isFalse() {
        assertThat(new FieldEvidence(Basis.UNCERTAIN, List.of("The lamps were lit early"), 1, 4).isKept())
                .isFalse();
    }

    @ParameterizedTest
    @CsvSource({"3, 4, from 3 of 4 passages", "2, 5, from 2 of 5 passages", "1, 1, from 1 of 1 passages"})
    void describe_passages_saysHowManyOfThePassagesReadStandBehindIt(
            final int agreeing, final int samples, final String expected) {
        assertThat(new FieldEvidence(Basis.PASSAGES, List.of("q"), agreeing, samples).describe())
                .isEqualTo(expected);
    }

    @Test
    void describe_knowledge_saysItIsWhatTheModelKnowsOfThisBook() {
        assertThat(new FieldEvidence(Basis.KNOWLEDGE, List.of(), 0, 4).describe())
                .isEqualTo("from what the model knows of this book");
    }

    @ParameterizedTest
    @EnumSource(Basis.class)
    void describe_everyBasis_saysSomething(final Basis basis) {
        assertThat(new FieldEvidence(basis, List.of(), 1, 4).describe()).isNotBlank();
    }

    @ParameterizedTest
    @CsvSource({"3,2", "-1,2", "0,0"})
    void fieldEvidence_countsThatCannotHold_areRejected(final int agreeing, final int samples) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new FieldEvidence(Basis.PASSAGES, List.of(), agreeing, samples));
    }

    @Test
    @SuppressWarnings("NullAway")
    void fieldEvidence_missingBasis_isRejected() {
        assertThatNullPointerException().isThrownBy(() -> new FieldEvidence(null, List.of(), 1, 2));
    }
}
