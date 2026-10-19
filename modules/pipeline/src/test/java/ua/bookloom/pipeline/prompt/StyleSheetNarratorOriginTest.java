package ua.bookloom.pipeline.prompt;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.Narrator;
import ua.bookloom.api.project.Narrator.GenderOrigin;
import ua.bookloom.api.project.NarratorPerson;

/**
 * The style sheet tells every call who narrates, and what it says follows the gender's origin (15i.T5): a gender the
 * person, the book's evidence or the run's own count settled is stated; a suggested one reads "likely" and does not
 * order the masculine or feminine form, because the checks do not hold the text to a guess either.
 */
class StyleSheetNarratorOriginTest {

    private static String sheet(final Gender gender, final GenderOrigin origin) {
        return StyleSheet.from(BookBrief.defaults("en")
                        .withNarrator(new Narrator(NarratorPerson.FIRST, gender, origin, List.of())))
                .text();
    }

    @ParameterizedTest
    @CsvSource({
        "MALE, PERSON, male", "MALE, VERIFIED, male", "MALE, INFERRED, male",
        "FEMALE, PERSON, female", "FEMALE, VERIFIED, female", "FEMALE, INFERRED, female"
    })
    void from_settledNarratorGender_isStatedAndOrdered(
            final Gender gender, final GenderOrigin origin, final String word) {
        assertThat(sheet(gender, origin))
                .contains("Narrator: first person, " + word + ".")
                .contains("in the " + (word.equals("male") ? "masculine" : "feminine") + " form");
    }

    // IF a "(suggested)" gender were ordered like a fact, THEN a wrong guess would be written into every paragraph.
    @ParameterizedTest
    @CsvSource({"MALE, male, masculine", "FEMALE, female, feminine"})
    void from_suggestedNarratorGender_readsLikelyAndOrdersNoForm(
            final Gender gender, final String word, final String form) {
        final String text = sheet(gender, GenderOrigin.SUGGESTED);

        assertThat(text).contains("likely " + word).doesNotContain("in the " + form + " form");
        assertThat(text).doesNotContain("Narrator: first person, " + word + ".");
    }

    @Test
    void from_unknownNarratorGender_keepsTheOneGenderThroughoutLine() {
        assertThat(sheet(Gender.UNKNOWN, GenderOrigin.NONE))
                .contains("Narrator: first person. Where the target language shows gender");
    }
}
