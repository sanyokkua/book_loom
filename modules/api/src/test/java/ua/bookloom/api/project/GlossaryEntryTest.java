package ua.bookloom.api.project;

import static org.assertj.core.api.Assertions.assertThat;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Who chose a target follows the edit: the person's edits confirm it, a type or gender change keeps it. */
class GlossaryEntryTest {

    private static GlossaryEntry suggested() {
        return new GlossaryEntry("e1", "p1", "Wales", "Уельс", TermType.PLACE, Gender.UNKNOWN, false)
                .withSuggestedTarget("Уельс");
    }

    @Test
    void withSuggestedTarget_unlockedEntry_isSuggested() {
        assertThat(suggested().isSuggested()).isTrue();
    }

    @ParameterizedTest
    @CsvSource(
            value = {"true,Уельс", "false,NULL", "false,'  '"},
            nullValues = "NULL")
    void new_lockedOrWithoutTarget_isAlwaysThePersons(final boolean locked, final @Nullable String target) {
        final GlossaryEntry entry = new GlossaryEntry(
                "e1", "p1", "Wales", target, TermType.PLACE, Gender.UNKNOWN, locked, TargetOrigin.SUGGESTED);

        assertThat(entry.origin()).isEqualTo(TargetOrigin.PERSON);
    }

    @Test
    void withTarget_typedOverASuggestion_isThePersons() {
        assertThat(suggested().withTarget("Вейлз").origin()).isEqualTo(TargetOrigin.PERSON);
    }

    @Test
    void withLocked_turnedOn_confirmsTheSuggestion() {
        assertThat(suggested().withLocked(true).origin()).isEqualTo(TargetOrigin.PERSON);
    }

    @Test
    void withTypeAndGender_onASuggestion_keepItSuggested() {
        final GlossaryEntry edited = suggested().withType(TermType.TERM).withGender(Gender.MALE);

        assertThat(edited.origin()).isEqualTo(TargetOrigin.SUGGESTED);
    }

    @Test
    void accepted_suggestion_keepsTheTargetAsThePersons() {
        final GlossaryEntry accepted = suggested().accepted();

        assertThat(accepted.target()).isEqualTo("Уельс");
        assertThat(accepted.origin()).isEqualTo(TargetOrigin.PERSON);
    }

    @Test
    void new_sevenComponents_isThePersons() {
        assertThat(new GlossaryEntry("e1", "p1", "Wales", "Уельс", TermType.PLACE, Gender.UNKNOWN, false).origin())
                .isEqualTo(TargetOrigin.PERSON);
    }

    @Test
    void withSuggestedGender_unlockedEntry_isMarkedAsSuggested() {
        final GlossaryEntry seeded = new GlossaryEntry(
                        "e1", "p1", "John", null, TermType.CHARACTER, Gender.UNKNOWN, false)
                .withSuggestedGender(Gender.MALE);

        assertThat(seeded.isGenderSuggested()).isTrue();
        assertThat(seeded.gender()).isEqualTo(Gender.MALE);
    }

    @Test
    void genderEditedOrEntryLocked_confirmsTheSuggestedGender() {
        final GlossaryEntry seeded = new GlossaryEntry(
                        "e1", "p1", "John", null, TermType.CHARACTER, Gender.UNKNOWN, false)
                .withSuggestedGender(Gender.MALE);

        assertThat(seeded.withGender(Gender.FEMALE).isGenderSuggested()).isFalse();
        assertThat(seeded.withLocked(true).isGenderSuggested()).isFalse();
    }

    @Test
    void new_unknownGender_isNeverMarkedAsSuggested() {
        final GlossaryEntry entry = new GlossaryEntry(
                "e1", "p1", "John", null, TermType.CHARACTER, Gender.UNKNOWN, false, TargetOrigin.PERSON, true);

        assertThat(entry.isGenderSuggested()).isFalse();
    }
}
