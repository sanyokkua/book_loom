package ua.bookloom.pipeline.checks;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import ua.bookloom.api.project.Gender;

/** The Ukrainian narrator-gender check on synthetic scenes; every expected word is written out. */
class UkrainianGenderCheckTest {

    private static final GenderCheck CHECK = GenderChecks.named("uk");

    @Test
    void find_maleNarratorWritesFeminineVerb_flagsTheVerbWithItsSpan() {
        final List<CheckFinding> found = CHECK.find("Я зачинила двері й пішов.", Gender.MALE);

        assertThat(found).hasSize(1);
        assertThat(found.getFirst().kind()).isEqualTo(FindingKind.GENDER);
        assertThat(found.getFirst().blocking()).isFalse();
        assertThat(found.getFirst().span().text()).isEqualTo("зачинила");
        assertThat(found.getFirst().span().start()).isEqualTo(2);
    }

    @Test
    void find_femaleNarratorWritesMasculineVerb_flagsTheVerb() {
        assertThat(CHECK.find("Я зачинив двері.", Gender.FEMALE))
                .extracting(finding -> finding.span().text())
                .containsExactly("зачинив");
    }

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                "Я тихо зачинила двері.|зачинила",
                "Я вже не бачила нікого.|бачила",
                "Тоді я засміялась.|засміялась",
                "Я сказало.|сказало"
            })
    void find_maleNarratorAfterParticlesOrWithNeuterEnding_flagsTheFirstWordAfterTheirPronoun(
            final String text, final String word) {
        assertThat(CHECK.find(text, Gender.MALE))
                .extracting(finding -> finding.span().text())
                .containsExactly(word);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "«Я зачинила двері», — сказала вона.",
                "— Я зачинила двері, — сказала вона.",
                "— Я зачинила двері. Іди спати, — додала вона.",
                "Вона шепнула: «Я не знала». Я вислухав.",
                "Вона зачинила двері.",
                "Я зачинив двері.",
                "Я, зачинивши двері, пішов.",
                "Я, як на зло, пішла геть."
            })
    void find_maleNarratorWhereTheFeminineIsSpeechOrNoWordOfTheNarrator_flagsNothing(final String text) {
        assertThat(CHECK.find(text, Gender.MALE)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(
            strings = {"Я мало знав нікого.", "Я в тебе вірив.", "Я любов не вважав слабкістю.", "Яблуко зачинила."})
    void find_wordsThatEndLikeAPastTenseButAreNot_areNeverFlaggedForAFemaleNarratorOrAfterAWordStartingWithYa(
            final String text) {
        assertThat(CHECK.find(text, Gender.FEMALE)).isEmpty();
    }

    @Test
    void find_narratorSpeaksAfterAQuote_isStillRead() {
        assertThat(CHECK.find("— Іди, — сказав він. Я зачинила двері.", Gender.MALE))
                .extracting(finding -> finding.span().text())
                .containsExactly("зачинила");
    }

    @Test
    void named_languageWithNoCheck_findsNothing() {
        assertThat(GenderChecks.named(null).find("Я зачинила двері.", Gender.MALE))
                .isEmpty();
        assertThat(GenderChecks.named("pl").find("Zamknęłam drzwi.", Gender.MALE))
                .isEmpty();
    }
}
