package ua.bookloom.api.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import ua.bookloom.api.pipeline.QualityDial;

/**
 * {@code BookBrief}'s defaults and balance validation.
 */
class BookBriefTest {

    @Test
    void defaults_noArguments_matchesTheBriefDefaults() {
        final BookBrief brief = BookBrief.defaults("en");

        assertThat(brief.sourceLanguage()).isEqualTo("en");
        assertThat(brief.targetLanguage()).isNull();
        assertThat(brief.genre()).isNull();
        assertThat(brief.register()).isEqualTo(Register.NEUTRAL);
        assertThat(brief.voiceEra()).isNull();
        assertThat(brief.audience()).isNull();
        assertThat(brief.names()).isEqualTo(NamePolicy.TRANSLITERATE);
        assertThat(brief.foreignPassages()).isEqualTo(ForeignPassagePolicy.KEEP);
        assertThat(brief.footnotes()).isEqualTo(FootnotePolicy.TRANSLATE);
        assertThat(brief.units()).isEqualTo(UnitPolicy.KEEP);
        assertThat(brief.balance()).isEqualTo(55);
        assertThat(brief.dial()).isEqualTo(QualityDial.BALANCED);
        assertThat(brief.alsoTranslate()).isEqualTo(new AlsoTranslate(true, true, true, false));
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 101})
    void constructor_balanceOutsideRange_throws(final int balance) {
        assertThatThrownBy(() -> new BookBrief(
                        "en",
                        null,
                        null,
                        Register.NEUTRAL,
                        null,
                        null,
                        NamePolicy.TRANSLITERATE,
                        ForeignPassagePolicy.KEEP,
                        FootnotePolicy.TRANSLATE,
                        UnitPolicy.KEEP,
                        balance,
                        AlsoTranslate.defaults(),
                        QualityDial.BALANCED))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 100})
    void constructor_balanceAtBounds_isAccepted(final int balance) {
        final BookBrief brief = new BookBrief(
                "en",
                null,
                null,
                Register.NEUTRAL,
                null,
                null,
                NamePolicy.TRANSLITERATE,
                ForeignPassagePolicy.KEEP,
                FootnotePolicy.TRANSLATE,
                UnitPolicy.KEEP,
                balance,
                AlsoTranslate.defaults(),
                QualityDial.BALANCED);

        assertThat(brief.balance()).isEqualTo(balance);
    }

    @Test
    void defaults_noNarratorChosen_narratorIsUnspecified() {
        final Narrator narrator = BookBrief.defaults("en").narrator();

        assertThat(narrator).isEqualTo(Narrator.unspecified());
        assertThat(narrator.isSpecified()).isFalse();
        assertThat(narrator.hasCheckableGender()).isFalse();
    }

    @Test
    void withNarrator_firstPersonFemale_keepsEveryOtherChoiceAndSurvivesLanguageChange() {
        final BookBrief base = BookBrief.defaults("en").withLanguages("en", "uk");
        final Narrator chosen = new Narrator(NarratorPerson.FIRST, Gender.FEMALE);

        final BookBrief brief = base.withNarrator(chosen).withLanguages("en", "pl");

        assertThat(brief.narrator()).isEqualTo(chosen);
        assertThat(brief.targetLanguage()).isEqualTo("pl");
        assertThat(brief.withNarrator(Narrator.unspecified())).isEqualTo(base.withLanguages("en", "pl"));
    }

    @Test
    void hasCheckableGender_thirdPersonOrNeuter_isFalse() {
        assertThat(new Narrator(NarratorPerson.THIRD, Gender.FEMALE).hasCheckableGender())
                .isFalse();
        assertThat(new Narrator(NarratorPerson.FIRST, Gender.NEUTER).hasCheckableGender())
                .isFalse();
        assertThat(new Narrator(NarratorPerson.FIRST, Gender.MALE).hasCheckableGender())
                .isTrue();
    }
}
