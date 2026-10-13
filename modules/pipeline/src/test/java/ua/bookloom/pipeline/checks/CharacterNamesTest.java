package ua.bookloom.pipeline.checks;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import ua.bookloom.api.project.Gender;

/** The characters a language check holds the target to are the sheet's confirmed genders, never a "likely" one. */
class CharacterNamesTest {

    private static final List<String> PAIRS = List.of("Sam → Сем");

    // IF a "likely" line were read as a gender, THEN a guess would be enforced by the agreement checks (15i.T1).
    @Test
    void of_likelyLine_isNoCharacter() {
        assertThat(CharacterNames.of(List.of("Sam — likely female"), PAIRS)).isEmpty();
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @ValueSource(strings = {"Sam — female", "Sam — female (she ×2)"})
    void of_confirmedLine_isAFemaleCharacterWithItsRendering(final String line) {
        assertThat(CharacterNames.of(List.of(line), PAIRS)).containsExactly(new CharacterName("Сем", Gender.FEMALE));
    }
}
