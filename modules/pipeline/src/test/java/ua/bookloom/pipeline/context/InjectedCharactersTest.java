package ua.bookloom.pipeline.context;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.Origin;
import ua.bookloom.api.project.TermType;

/**
 * A character whose gender is only likely is listed by name alone (design D5): arm 4b (task 7.8) measured the hint
 * line {@code Pell — male (likely)} against it and the hint brought no fewer gender-agreement errors, so it was removed.
 */
class InjectedCharactersTest {

    // IF the likely gender were shown, THEN a model guess would reach every prompt that names the character.
    @Test
    void line_likelyGender_showsNameOnly() {
        final List<GlossaryEntry> glossary =
                List.of(ContextFixtures.entry("Pell", null, TermType.CHARACTER, Gender.UNKNOWN, false)
                        .withGenderFrom(Gender.MALE, Origin.LIKELY));

        assertThat(InjectedCharacters.select(List.of(ContextFixtures.segment(0, "Pell laughed.")), glossary, "en"))
                .containsExactly("Pell");
    }
}
