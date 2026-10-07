package ua.bookloom.pipeline.reviewer;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** {@link CriterionFit}: an edit that re-inflects a glossary name is the reviewer's guess, not evidence. */
class CriterionFitTest {

    private static final List<String> NAMES = List.of("Джек", "Хром", "Боббі");

    private static boolean fits(final ReviewCriterion criterion, final String quote, final String replacement) {
        return CriterionFit.fits(new ReviewEdit(criterion, quote, replacement), quote, NAMES);
    }

    // IF a reviewer could change the form of a name under agreement, THEN a correct vocative became a nominative.
    @ParameterizedTest
    @CsvSource({
        "AGREEMENT,Тримайся міцніше Джеку,Тримайся міцніше Джек",
        "AGREEMENT,льоду Хрому,льоду Хромів",
        "MEANING,Джек пішов,Хром пішов"
    })
    void fits_meaningGenderOrAgreementEditThatChangesANameForm_doesNotFit(
            final ReviewCriterion criterion, final String quote, final String replacement) {
        assertThat(fits(criterion, quote, replacement)).isFalse();
    }

    @ParameterizedTest
    @CsvSource({
        "GENDER,Вона сказав,Вона сказала",
        "GENDER,Боббі сказав їй,Боббі сказала їй",
        "AGREEMENT,Боббі втомилась,Боббі втомився",
        "AGREEMENT,старі двері відчинилась,старі двері відчинилися"
    })
    void fits_editThatLeavesEveryNameAsItWas_stillFits(
            final ReviewCriterion criterion, final String quote, final String replacement) {
        assertThat(fits(criterion, quote, replacement)).isTrue();
    }

    // IF a common noun's rendering or a short name's stem held other words, THEN agreement and gender edits would be
    // refused.
    @ParameterizedTest
    @CsvSource({
        "AGREEMENT,старий чарівник прийшов,старого чарівника прийшов",
        "GENDER,наш господар пішов,наша господиня пішла",
        "MEANING,біля джерело води,біля джерела води"
    })
    void fits_editNearACommonNounOrAWordSharingANamesStart_stillFits(
            final ReviewCriterion criterion, final String quote, final String replacement) {
        assertThat(CriterionFit.fits(
                        new ReviewEdit(criterion, quote, replacement), quote, List.of("Джек", "господар", "чарівник")))
                .isTrue();
    }

    @ParameterizedTest
    @CsvSource({"TERMINOLOGY,Джак сказали,Джек сказали"})
    void fits_terminologyEdit_mayStillTouchANameForm(
            final ReviewCriterion criterion, final String quote, final String replacement) {
        assertThat(CriterionFit.fits(new ReviewEdit(criterion, quote, replacement), quote, NAMES))
                .isTrue();
    }
}
