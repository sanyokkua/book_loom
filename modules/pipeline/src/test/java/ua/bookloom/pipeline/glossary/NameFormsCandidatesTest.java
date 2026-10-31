package ua.bookloom.pipeline.glossary;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ua.bookloom.api.project.Gender;

/**
 * A written form read back through the source language's name-ending table gives every nominative it could belong
 * to; a language with no table gives the form itself. All names are invented.
 */
class NameFormsCandidatesTest {

    // Each expected reading is lemma/gender/n (nominative) or lemma/gender/o (oblique), several joined by "|".
    @ParameterizedTest
    @CsvSource({
        "ru, Велемир, Велемир/MALE/n",
        "ru, Велемира, Велемир/MALE/o|Велемира/FEMALE/n",
        "ru, Велемиру, Велемир/MALE/o|Велемира/FEMALE/o|Велемиру/UNKNOWN/n",
        "ru, Рудаковой, Рудакова/FEMALE/o|Рудаковой/MALE/n",
        "ru, Андрей, Андрей/UNKNOWN/n",
        "uk, Алісою, Аліса/FEMALE/o",
        "en, Wrens, Wrens/UNKNOWN/n"
    })
    void candidates_invertedParadigm_returnsExpectedSets(
            final String language, final String form, final String expected) {
        assertThat(NameForms.candidates(form, language)).isEqualTo(readings(expected));
    }

    private static Set<NameForms.Reading> readings(final String expected) {
        return Arrays.stream(expected.split("\\|"))
                .map(reading -> reading.split("/"))
                .map(parts -> new NameForms.Reading(parts[0], Gender.valueOf(parts[1]), "n".equals(parts[2])))
                .collect(Collectors.toSet());
    }
}
