package ua.bookloom.api.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.Narrator;
import ua.bookloom.api.project.NarratorPerson;
import ua.bookloom.api.project.Register;

/** A suggestion never overrides a narrator that is already set, and never keeps a voice note that contradicts it. */
class BriefSuggestionTest {

    private static BriefSuggestion guess(final String voice, final NarratorPerson person) {
        return new BriefSuggestion("noir", Register.CASUAL, voice, "adults", person, Gender.UNKNOWN);
    }

    @Test
    void alignedTo_noNarratorSet_returnsTheSuggestionAsItIs() {
        final BriefSuggestion guess = guess("third-person limited", NarratorPerson.THIRD);

        assertThat(guess.alignedTo(Narrator.unspecified())).isEqualTo(guess);
    }

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                "FIRST|third-person limited, terse",
                "FIRST|Third person, dry",
                "FIRST|від третьої особи, стримано",
                "THIRD|first-person confessional",
                "THIRD|від першої особи"
            })
    void alignedTo_voiceNamesTheOtherPerson_dropsTheVoiceAndKeepsTheSetNarrator(
            final NarratorPerson set, final String voice) {
        final BriefSuggestion aligned = guess(voice, NarratorPerson.UNSPECIFIED)
                .alignedTo(new Narrator(set, set == NarratorPerson.FIRST ? Gender.FEMALE : Gender.UNKNOWN));

        assertThat(aligned.voiceEra()).isNull();
        assertThat(aligned.narrator()).isEqualTo(set);
        assertThat(aligned.genre()).isEqualTo("noir");
    }

    @Test
    void alignedTo_voiceAgreesWithTheSetNarrator_keepsTheVoice() {
        final BriefSuggestion aligned = guess("first-person, short sentences", NarratorPerson.THIRD)
                .alignedTo(new Narrator(NarratorPerson.FIRST, Gender.MALE));

        assertThat(aligned.voiceEra()).isEqualTo("first-person, short sentences");
        assertThat(aligned.narrator()).isEqualTo(NarratorPerson.FIRST);
        assertThat(aligned.narratorGender()).isEqualTo(Gender.MALE);
    }

    private static final FieldEvidence AGREED = new FieldEvidence(List.of("I lit a cigarette"), 2, 2);

    private static BriefSuggestion withEvidence(final String voice) {
        return new BriefSuggestion(
                "noir",
                Register.CASUAL,
                voice,
                "adults",
                NarratorPerson.THIRD,
                Gender.UNKNOWN,
                Map.of(
                        BriefField.GENRE, AGREED,
                        BriefField.VOICE, AGREED,
                        BriefField.NARRATOR, AGREED,
                        BriefField.NARRATOR_GENDER, AGREED));
    }

    // IF the model's narrator evidence stayed beside the person's own narrator, THEN the brief would show a quote
    // for a choice the model did not make.
    @Test
    void alignedTo_narratorSet_dropsTheNarratorEvidenceAndKeepsTheRest() {
        final BriefSuggestion aligned =
                withEvidence("terse").alignedTo(new Narrator(NarratorPerson.FIRST, Gender.MALE));

        assertThat(aligned.evidence()).containsOnlyKeys(BriefField.GENRE, BriefField.VOICE);
        assertThat(aligned.evidenceOf(BriefField.GENRE)).contains(AGREED);
    }

    @Test
    void alignedTo_voiceDropped_dropsTheVoiceEvidence() {
        final BriefSuggestion aligned =
                withEvidence("third-person limited").alignedTo(new Narrator(NarratorPerson.FIRST, Gender.MALE));

        assertThat(aligned.evidenceOf(BriefField.VOICE)).isEmpty();
        assertThat(aligned.evidenceOf(BriefField.GENRE)).contains(AGREED);
    }

    @Test
    void alignedTo_noNarratorSet_keepsEveryEvidence() {
        assertThat(withEvidence("terse").alignedTo(Narrator.unspecified()).evidence())
                .hasSize(4);
    }

    private static BriefSuggestion provenGender(final Gender gender) {
        return new BriefSuggestion(
                "noir",
                Register.CASUAL,
                "terse",
                "adults",
                NarratorPerson.FIRST,
                gender,
                Map.of(
                        BriefField.NARRATOR,
                        AGREED,
                        BriefField.NARRATOR_GENDER,
                        new FieldEvidence(List.of("Hang on to your ass, Jack"), 1, 2)));
    }

    // IF a first-person narrator whose gender nobody chose discarded the model's verified answer, THEN the Start
    // question
    // would ask what the book already showed.
    @Test
    void alignedTo_firstPersonWithUnknownGender_fillsTheSuggestedGenderWithItsEvidence() {
        final BriefSuggestion aligned =
                provenGender(Gender.MALE).alignedTo(new Narrator(NarratorPerson.FIRST, Gender.UNKNOWN));

        assertThat(aligned.narrator()).isEqualTo(NarratorPerson.FIRST);
        assertThat(aligned.narratorGender()).isEqualTo(Gender.MALE);
        assertThat(aligned.evidenceOf(BriefField.NARRATOR_GENDER))
                .contains(new FieldEvidence(List.of("Hang on to your ass, Jack"), 1, 2));
        assertThat(aligned.evidence()).doesNotContainKey(BriefField.NARRATOR);
    }

    // IF a suggestion could replace a gender the person chose, THEN a guess would overrule the person.
    @ParameterizedTest
    @CsvSource({"MALE,FEMALE", "FEMALE,MALE", "FEMALE,FEMALE"})
    void alignedTo_genderAlreadySet_neverOverridesIt(final Gender set, final Gender suggested) {
        final BriefSuggestion aligned = provenGender(suggested).alignedTo(new Narrator(NarratorPerson.FIRST, set));

        assertThat(aligned.narratorGender()).isEqualTo(set);
        assertThat(aligned.evidence()).doesNotContainKey(BriefField.NARRATOR_GENDER);
    }

    @Test
    void alignedTo_thirdPersonSet_fillsNoNarratorGender() {
        final BriefSuggestion aligned =
                provenGender(Gender.FEMALE).alignedTo(new Narrator(NarratorPerson.THIRD, Gender.UNKNOWN));

        assertThat(aligned.narratorGender()).isEqualTo(Gender.UNKNOWN);
        assertThat(aligned.evidence()).doesNotContainKey(BriefField.NARRATOR_GENDER);
    }

    @Test
    void alignedTo_suggestionWithUnknownGender_leavesTheGenderUnknownWithNoEvidence() {
        final BriefSuggestion aligned =
                provenGender(Gender.UNKNOWN).alignedTo(new Narrator(NarratorPerson.FIRST, Gender.UNKNOWN));

        assertThat(aligned.narratorGender()).isEqualTo(Gender.UNKNOWN);
        assertThat(aligned.evidence()).doesNotContainKey(BriefField.NARRATOR_GENDER);
    }

    // A field the code proved from one sample's quote is kept although the other sample showed nothing.
    @ParameterizedTest
    @CsvSource({"2,2,'',true", "1,2,'',false", "1,2,'Hang on, Jack',true", "0,2,'',false"})
    void isKept_agreementOrAVerifiedQuote_keepsTheField(
            final int agreeing, final int samples, final String quote, final boolean expected) {
        final List<String> quotes = quote.isEmpty() ? List.of() : List.of(quote);

        assertThat(new FieldEvidence(quotes, agreeing, samples).isKept()).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({"2,2,true", "1,2,false", "0,2,false"})
    void isAgreed_agreeingOfSamples_isTrueOnlyWhenAllAgree(
            final int agreeing, final int samples, final boolean expected) {
        assertThat(new FieldEvidence(List.of(), agreeing, samples).isAgreed()).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({"3,2", "-1,2", "0,0"})
    void fieldEvidence_countsThatCannotHold_areRejected(final int agreeing, final int samples) {
        org.assertj.core.api.Assertions.assertThatIllegalArgumentException()
                .isThrownBy(() -> new FieldEvidence(List.of(), agreeing, samples));
    }
}
