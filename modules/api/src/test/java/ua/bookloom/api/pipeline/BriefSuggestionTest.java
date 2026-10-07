package ua.bookloom.api.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

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
}
