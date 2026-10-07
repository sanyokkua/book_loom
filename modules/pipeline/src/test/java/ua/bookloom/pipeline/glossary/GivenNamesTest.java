package ua.bookloom.pipeline.glossary;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.TermType;

/** The bundled first-name lists suggest a gender, never decide one, and say nothing about a name both genders use. */
class GivenNamesTest {

    private static GlossaryEntry entry(final String term, final TermType type, final Gender gender) {
        return new GlossaryEntry("e", "p", term, null, type, gender, false);
    }

    @ParameterizedTest
    @CsvSource({
        "John,en,MALE",
        "Hermione,en,FEMALE",
        "Hermione Granger,en,FEMALE",
        "Nathaniel,en,MALE",
        "Іван,uk,MALE",
        "Оксана,uk,FEMALE",
        "Сергей,ru,MALE",
        "Анна,ru,FEMALE",
        "Mary,en-GB,FEMALE"
    })
    void genderOf_listedFirstName_isTheListedGender(final String term, final String language, final Gender expected) {
        assertThat(GivenNames.genderOf(term, language)).contains(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {"Alex", "Sam", "Chris", "Jamie", "Taylor", "Mark", "Will", "Rose", "Hogwarts", "john"})
    void genderOf_unisexCommonWordOrUnlistedOrLowerCase_isEmpty(final String term) {
        assertThat(GivenNames.genderOf(term, "en")).isEmpty();
    }

    @Test
    void genderOf_languageWithoutAList_isEmpty() {
        assertThat(GivenNames.genderOf("John", "de")).isEmpty();
        assertThat(GivenNames.genderOf("John", null)).isEmpty();
    }

    @Test
    void genderOf_termOfMoreThanThreeWords_isEmpty() {
        assertThat(GivenNames.genderOf("John the Elder of Wales", "en")).isEmpty();
    }

    @Test
    void seeded_characterWithUnknownGender_getsASuggestedGender() {
        final GlossaryEntry seeded = GivenNames.seeded(entry("John", TermType.CHARACTER, Gender.UNKNOWN), "en", false);

        assertThat(seeded.gender()).isEqualTo(Gender.MALE);
        assertThat(seeded.isGenderSuggested()).isTrue();
        assertThat(seeded.locked()).isFalse();
    }

    @Test
    void seeded_unisexName_isReturnedUnchanged() {
        final GlossaryEntry alex = entry("Alex", TermType.CHARACTER, Gender.UNKNOWN);

        assertThat(GivenNames.seeded(alex, "en", true)).isSameAs(alex);
    }

    @Test
    void seeded_genderAlreadySet_isNeverOverwritten() {
        final GlossaryEntry set = entry("John", TermType.CHARACTER, Gender.FEMALE);

        assertThat(GivenNames.seeded(set, "en", true)).isSameAs(set);
    }

    @Test
    void seeded_lockedEntry_isLeftToThePerson() {
        final GlossaryEntry locked =
                entry("John", TermType.CHARACTER, Gender.UNKNOWN).withLocked(true);

        assertThat(GivenNames.seeded(locked, "en", true)).isSameAs(locked);
    }

    @Test
    void seeded_placeNamedLikeAPerson_isLeftAlone() {
        final GlossaryEntry place = entry("Victoria", TermType.PLACE, Gender.UNKNOWN);

        assertThat(GivenNames.seeded(place, "en", true)).isSameAs(place);
    }

    @Test
    void seeded_untypedEntryOfAFirstScan_becomesACharacterOnlyWhenRetypingIsAsked() {
        final GlossaryEntry untyped = entry("Hermione", TermType.OTHER, Gender.UNKNOWN);

        assertThat(GivenNames.seeded(untyped, "en", false)).isSameAs(untyped);
        assertThat(GivenNames.seeded(untyped, "en", true))
                .extracting(GlossaryEntry::type, GlossaryEntry::gender, GlossaryEntry::isGenderSuggested)
                .containsExactly(TermType.CHARACTER, Gender.FEMALE, true);
    }

    @Test
    void seeded_aPersonsGenderEditOrLock_confirmsItButATargetEditDoesNot() {
        final GlossaryEntry seeded = GivenNames.seeded(entry("John", TermType.CHARACTER, Gender.UNKNOWN), "en", false);

        assertThat(seeded.withGender(Gender.MALE).isGenderSuggested()).isFalse();
        assertThat(seeded.withLocked(true).isGenderSuggested()).isFalse();
        assertThat(seeded.withTarget("Джон").isGenderSuggested()).isTrue();
    }
}
