package ua.bookloom.pipeline.reviewer;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
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

    private static final Set<String> UK_FUNCTION_WORDS = Set.of("би", "не", "ні", "ніколи", "та");

    private static boolean fitsWithFunctionWords(
            final ReviewCriterion criterion, final String quote, final String replacement, final Set<String> words) {
        return CriterionFit.fits(new ReviewEdit(criterion, quote, replacement), quote, List.of(), words);
    }

    // IF an agreement edit could drop "би", THEN the sentence lost its conditional mood: a real run's regression.
    @ParameterizedTest
    @CsvSource({
        "AGREEMENT,міг би пройти їхню охорону.,міг пройти їхню охорону.",
        "GENDER,вона не пішла,вона пішла",
        "AGREEMENT,він пішов,він ніколи пішов",
        "AGREEMENT,він пішов,вона зникла"
    })
    void fits_agreementOrGenderEditThatAddsOrDropsAFunctionWord_doesNotFit(
            final ReviewCriterion criterion, final String quote, final String replacement) {
        assertThat(fitsWithFunctionWords(criterion, quote, replacement, UK_FUNCTION_WORDS))
                .isFalse();
    }

    // IF the language lists no function words, THEN only the ending rule applies: a dropped word still changes more
    // than an ending.
    @ParameterizedTest
    @CsvSource({"AGREEMENT,міг би пройти їхню охорону.,міг пройти їхню охорону.", "AGREEMENT,він пішов,вона зникла"})
    void fits_languageWithoutFunctionWords_stillRefusesAChangeBeyondEndings(
            final ReviewCriterion criterion, final String quote, final String replacement) {
        assertThat(fitsWithFunctionWords(criterion, quote, replacement, Set.of()))
                .isFalse();
    }

    @ParameterizedTest
    @CsvSource({
        "AGREEMENT,міг би пройти їхню охорону,міг би пройшла їхню охорону",
        "GENDER,вона не сказав,вона не сказала",
        "AGREEMENT,старі двері відчинилась,старі двері відчинилися"
    })
    void fits_endingOnlyEditKeepingFunctionWords_fits(
            final ReviewCriterion criterion, final String quote, final String replacement) {
        assertThat(fitsWithFunctionWords(criterion, quote, replacement, UK_FUNCTION_WORDS))
                .isTrue();
    }

    // IF a reflexive verb's -ся counted against its stem, THEN дивився → дивилася read as a new word and every gender
    // fix of a reflexive verb was thrown away; a dropped function word is still refused.
    @ParameterizedTest
    @CsvSource({
        "GENDER,дивився на море,дивилася на море,true",
        "GENDER,сміявся тихо,сміялася тихо,true",
        "GENDER,звівся на ноги,звелася на ноги,true",
        "AGREEMENT,вмився холодною водою,вмилася холодною водою,true",
        "AGREEMENT,міг би пройти їхню охорону.,міг пройти їхню охорону.,false"
    })
    void fits_genderOrAgreementEditOfAReflexiveVerb_fitsUnlessAFunctionWordGoes(
            final ReviewCriterion criterion, final String quote, final String replacement, final boolean expected) {
        assertThat(fitsWithFunctionWords(criterion, quote, replacement, UK_FUNCTION_WORDS))
                .isEqualTo(expected);
    }

    // IF a meaning edit could bring in words from nowhere, THEN a model's invention replaced the translator's word.
    @ParameterizedTest
    @CsvSource({
        "протидії забуттю,senile amnesia,false",
        "протидії забуттю,протидії склерозу,true",
        "протидії забуттю,забуттю протидії,true",
        "протидії забуттю,протидії склерозу і амнезії,true"
    })
    void fits_meaningEdit_refusesWordsOfAnotherScriptButNotACorrectionInTheTargetScript(
            final String quote, final String replacement, final boolean expected) {
        final String candidate = "Це ліки для протидії забуттю та забуттю протидії.";

        assertThat(CriterionFit.fits(
                        new ReviewEdit(ReviewCriterion.MEANING, quote, replacement), candidate, List.of(), Set.of()))
                .isEqualTo(expected);
    }
}
