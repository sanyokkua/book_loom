package ua.bookloom.pipeline.memory;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GenderOrigin;
import ua.bookloom.api.project.TermType;

/** Only a character has a gender line, and a guessed gender is called likely (15i.T1). */
class TermFactsTest {

    @ParameterizedTest(name = "[{index}] {0} {1} {2} -> {3}")
    @CsvSource(
            nullValues = "null",
            value = {
                "CHARACTER, FEMALE, PERSON, 'character, female'",
                "CHARACTER, MALE, VERIFIED, 'character, male'",
                "CHARACTER, FEMALE, SUGGESTED, 'character, likely female'",
                "CHARACTER, UNKNOWN, null, 'character, unknown'",
                "PLACE, NEUTER, PERSON, place",
                "TERM, MALE, SUGGESTED, term",
                "TITLE, UNKNOWN, null, title"
            })
    void of_typeGenderAndOrigin_wordsTheFacts(
            final TermType type, final Gender gender, final GenderOrigin origin, final String facts) {
        assertThat(TermFacts.of(type, gender, origin)).isEqualTo(facts);
    }
}
