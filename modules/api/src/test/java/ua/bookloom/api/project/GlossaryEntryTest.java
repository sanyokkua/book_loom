package ua.bookloom.api.project;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

/** Who chose a target follows the edit: the person's edits confirm it, a type or gender change keeps it; a type carries the origin it is given. */
class GlossaryEntryTest {

    private static GlossaryEntry suggested() {
        return GlossaryEntry.of("e1", "p1", "Wales", "Уельс", TermType.PLACE, Gender.UNKNOWN, false)
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
                "e1",
                "p1",
                "Wales",
                List.of(),
                false,
                target,
                locked ? TargetState.LOCKED : TargetState.SUGGESTED,
                Origin.LIKELY,
                TermType.PLACE,
                null,
                Origin.USER,
                Gender.UNKNOWN,
                null,
                false,
                false,
                null,
                null,
                null,
                0);

        assertThat(entry.isSuggested()).isFalse();
        assertThat(entry.targetOrigin()).isNotEqualTo(Origin.LIKELY);
    }

    @Test
    void withTarget_typedOverASuggestion_isThePersons() {
        assertThat(suggested().withTarget("Вейлз").targetOrigin()).isEqualTo(Origin.USER);
    }

    @Test
    void withLocked_turnedOn_confirmsTheSuggestion() {
        assertThat(suggested().withLocked(true).targetOrigin()).isEqualTo(Origin.USER);
    }

    @Test
    void withTypeAndGender_onASuggestion_keepItSuggested() {
        final GlossaryEntry edited =
                suggested().withType(TermType.TERM, Origin.USER).withGender(Gender.MALE);

        assertThat(edited.targetOrigin()).isEqualTo(Origin.LIKELY);
    }

    @ParameterizedTest
    @EnumSource(Origin.class)
    void withType_statedOrigin_isTheTypesOrigin(final Origin origin) {
        final GlossaryEntry typed = suggested().withType(TermType.CHARACTER, origin);

        assertThat(typed.type()).isEqualTo(TermType.CHARACTER);
        assertThat(typed.typeOrigin()).isEqualTo(origin);
    }

    @Test
    void accepted_suggestion_keepsTheTargetAsThePersons() {
        final GlossaryEntry accepted = suggested().accepted();

        assertThat(accepted.target()).isEqualTo("Уельс");
        assertThat(accepted.targetOrigin()).isEqualTo(Origin.USER);
    }

    @Test
    void new_sevenComponents_isThePersons() {
        assertThat(GlossaryEntry.of("e1", "p1", "Wales", "Уельс", TermType.PLACE, Gender.UNKNOWN, false)
                        .targetOrigin())
                .isEqualTo(Origin.USER);
    }

    @Test
    void withSuggestedGender_unlockedEntry_isMarkedAsSuggested() {
        final GlossaryEntry seeded = GlossaryEntry.of(
                        "e1", "p1", "John", null, TermType.CHARACTER, Gender.UNKNOWN, false)
                .withSuggestedGender(Gender.MALE);

        assertThat(seeded.genderOrigin()).isEqualTo(Origin.LIKELY);
        assertThat(seeded.gender()).isEqualTo(Gender.MALE);
    }

    @Test
    void genderEditedOrEntryLocked_confirmsTheSuggestedGender() {
        final GlossaryEntry seeded = GlossaryEntry.of(
                        "e1", "p1", "John", null, TermType.CHARACTER, Gender.UNKNOWN, false)
                .withSuggestedGender(Gender.MALE);

        assertThat(seeded.withGender(Gender.FEMALE).genderOrigin()).isEqualTo(Origin.USER);
        assertThat(seeded.withLocked(true).genderOrigin()).isEqualTo(Origin.USER);
    }

    // A lock the code sets by rule (a brand, a kept original) fixes the rendering but confirms nothing the person did
    // not see: a suggested gender stays a suggestion (15i review).
    @Test
    void withRuleLock_suggestedGender_locksTheRenderingAndKeepsTheGenderSuggested() {
        final GlossaryEntry seeded = GlossaryEntry.of(
                        "e1", "p1", "John", null, TermType.CHARACTER, Gender.UNKNOWN, false)
                .withSuggestedGender(Gender.MALE)
                .withSuggestedTarget("John");

        final GlossaryEntry locked = seeded.withRuleLock();

        assertThat(locked.locked()).isTrue();
        assertThat(locked.genderOrigin()).isEqualTo(Origin.LIKELY);
        assertThat(locked.withLocked(true).genderOrigin()).isEqualTo(Origin.USER);
    }

    @Test
    void new_unknownGender_isNeverMarkedAsSuggested() {
        final GlossaryEntry entry = GlossaryEntry.of(
                        "e1", "p1", "John", null, TermType.CHARACTER, Gender.UNKNOWN, false)
                .withGenderFrom(Gender.UNKNOWN, Origin.LIKELY);

        assertThat(entry.genderOrigin()).isNull();
    }

    // A meaning note and the person's answer about it travel with every later copy of the entry (design D5).
    @Test
    void withMeaningAndChoice_laterCopies_keepBoth() {
        final GlossaryEntry entry = GlossaryEntry.of(
                        "e1", "p1", "Brightwater", null, TermType.PLACE, Gender.UNKNOWN, false)
                .withMeaning("bright water")
                .withMeaningChoice(MeaningChoice.TRANSCRIBE_GLOSS);

        final GlossaryEntry later =
                entry.withSuggestedTarget("Брайтвотер").withLocked(true).withType(TermType.CHARACTER, Origin.USER);

        assertThat(later.meaning()).isEqualTo("bright water");
        assertThat(later.meaningChoice()).isEqualTo(MeaningChoice.TRANSCRIBE_GLOSS);
        assertThat(entry.withMeaning("  ").meaning()).isNull();
    }
}
