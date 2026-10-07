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
        final GlossaryEntry seeded = GivenNames.seeded(entry("John", TermType.CHARACTER, Gender.UNKNOWN), "en");

        assertThat(seeded.gender()).isEqualTo(Gender.MALE);
        assertThat(seeded.isGenderSuggested()).isTrue();
        assertThat(seeded.locked()).isFalse();
    }

    @Test
    void seeded_unisexName_isReturnedUnchanged() {
        final GlossaryEntry alex = entry("Alex", TermType.CHARACTER, Gender.UNKNOWN);

        assertThat(GivenNames.seeded(alex, "en")).isSameAs(alex);
    }

    @Test
    void seeded_genderAlreadySet_isNeverOverwritten() {
        final GlossaryEntry set = entry("John", TermType.CHARACTER, Gender.FEMALE);

        assertThat(GivenNames.seeded(set, "en")).isSameAs(set);
    }

    @Test
    void seeded_lockedEntry_isLeftToThePerson() {
        final GlossaryEntry locked =
                entry("John", TermType.CHARACTER, Gender.UNKNOWN).withLocked(true);

        assertThat(GivenNames.seeded(locked, "en")).isSameAs(locked);
    }

    @Test
    void seeded_placeNamedLikeAPerson_isLeftAlone() {
        final GlossaryEntry place = entry("Victoria", TermType.PLACE, Gender.UNKNOWN);

        assertThat(GivenNames.seeded(place, "en")).isSameAs(place);
    }

    @ParameterizedTest
    @ValueSource(strings = {"Florence", "Victoria Station", "George Street", "Austin", "Hermione"})
    void seeded_untypedEntryNamedLikeAPerson_isNeverRetyped(final String term) {
        final GlossaryEntry untyped = entry(term, TermType.OTHER, Gender.UNKNOWN);

        assertThat(GivenNames.seeded(untyped, "en")).isSameAs(untyped);
    }

    @Test
    void seeded_genderTheListAlreadyTriedOrThePersonResetToUnknown_isNotSeededAgain() {
        final GlossaryEntry reset =
                entry("Tom", TermType.CHARACTER, Gender.MALE).withGender(Gender.UNKNOWN);

        assertThat(reset.genderSeedTried()).isTrue();
        assertThat(GivenNames.seeded(reset, "en")).isSameAs(reset);
    }

    @Test
    void seeded_aSeededEntry_isMarkedAsTriedSoTheListNeverRunsTwice() {
        final GlossaryEntry seeded = GivenNames.seeded(entry("John", TermType.CHARACTER, Gender.UNKNOWN), "en");

        assertThat(seeded.genderSeedTried()).isTrue();
    }

    @Test
    void withInferredGender_sameGender_keepsASuggestionSuggested() {
        final GlossaryEntry seeded = GivenNames.seeded(entry("John", TermType.CHARACTER, Gender.UNKNOWN), "en");

        assertThat(seeded.withInferredGender(Gender.MALE)).isSameAs(seeded);
        assertThat(seeded.withInferredGender(Gender.MALE).isGenderSuggested()).isTrue();
        assertThat(seeded.withGender(Gender.MALE).isGenderSuggested()).isFalse();
    }

    @Test
    void seeded_aPersonsGenderEditOrLock_confirmsItButATargetEditDoesNot() {
        final GlossaryEntry seeded = GivenNames.seeded(entry("John", TermType.CHARACTER, Gender.UNKNOWN), "en");

        assertThat(seeded.withGender(Gender.MALE).isGenderSuggested()).isFalse();
        assertThat(seeded.withLocked(true).isGenderSuggested()).isFalse();
        assertThat(seeded.withTarget("Джон").isGenderSuggested()).isTrue();
    }
}
