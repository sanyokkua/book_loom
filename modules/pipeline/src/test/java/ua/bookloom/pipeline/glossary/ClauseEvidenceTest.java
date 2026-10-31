package ua.bookloom.pipeline.glossary;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.TermType;

/**
 * A form that fits two invented names ({@code Велемира}: the man {@code Велемир}'s genitive, or the woman {@code
 * Велемира}) is decided mention by mention by the clause around it, or left unsure.
 */
class ClauseEvidenceTest {

    private static final String FORM = "Велемира";

    @Test
    void mention_pastFemaleAfterForm_votesFemaleOnly() {
        assertThat(decide("Велемира сказала тихо.")).contains(new NameForms.Reading("Велемира", Gender.FEMALE, true));
    }

    @ParameterizedTest
    @CsvSource(
            delimiter = ';',
            value = {"Мы пришли до Велемира.", "Ключ Велемира лежал на столе."})
    void mention_prepositionBeforeOrVerbOfTheOtherGender_decidesTheMasculineGenitive(final String text) {
        assertThat(decide(text)).contains(new NameForms.Reading("Велемир", Gender.MALE, false));
    }

    @ParameterizedTest
    @CsvSource(
            delimiter = ';',
            value = {"Это был портрет Велемира.", "— Позови Велемира, — сказала она."})
    void mention_noEvidence_isUnsureAndVotesNowhere(final String text) {
        assertThat(decide(text)).isEmpty();
        assertThat(PronounGender.of(velemir(), List.of(text + " Он ушёл."), "ru")
                        .total())
                .isZero();
    }

    @Test
    void mention_decidedForTheEntry_votesForIt() {
        assertThat(PronounGender.of(velemir(), List.of("Ключ Велемира лежал на столе. Он ушёл."), "ru")
                        .male())
                .isEqualTo(1);
    }

    private static Optional<NameForms.Reading> decide(final String text) {
        final int start = text.indexOf(FORM);
        return ClauseEvidence.of("ru").decide(text, start, start + FORM.length(), NameForms.candidates(FORM, "ru"));
    }

    private static GlossaryEntry velemir() {
        return GlossaryEntry.of("p1:велемир", "p1", "Велемир", null, TermType.CHARACTER, Gender.UNKNOWN, false)
                .withForms(List.of(FORM));
    }
}
