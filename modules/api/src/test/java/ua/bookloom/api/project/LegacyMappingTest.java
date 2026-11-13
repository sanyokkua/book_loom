package ua.bookloom.api.project;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/** The old shapes map onto the new ones: locked is LOCKED, the person is USER, verified is BOOK, suggested LIKELY. */
class LegacyMappingTest {

    private static GlossaryEntry entry(final boolean locked) {
        return GlossaryEntry.of("e1", "p1", "Hale", "Гейл", TermType.CHARACTER, Gender.MALE, locked);
    }

    // Proves the factory reads a locked entry as LOCKED with the person's target and gender.
    @Test
    void mapping_lockedAndPerson_becomeLockedAndUser() {
        final GlossaryEntry locked = entry(true);

        assertThat(locked.targetState()).isEqualTo(TargetState.LOCKED);
        assertThat(locked.targetOrigin()).isEqualTo(Origin.USER);
        assertThat(locked.genderOrigin()).isEqualTo(Origin.USER);
        assertThat(locked.locked()).isTrue();
    }

    // Proves an unlocked entry with a target is PINNED by the person.
    @Test
    void mapping_unlockedWithTarget_becomesPinned() {
        final GlossaryEntry unlocked = entry(false);

        assertThat(unlocked.targetState()).isEqualTo(TargetState.PINNED);
        assertThat(unlocked.targetOrigin()).isEqualTo(Origin.USER);
        assertThat(unlocked.locked()).isFalse();
    }

    // Proves an entry with no target holds no target origin.
    @Test
    void mapping_noTarget_hasNoTargetOrigin() {
        final GlossaryEntry empty =
                GlossaryEntry.of("e1", "p1", "Hale", null, TermType.CHARACTER, Gender.UNKNOWN, false);

        assertThat(empty.targetState()).isEqualTo(TargetState.PINNED);
        assertThat(empty.targetOrigin()).isNull();
        assertThat(empty.genderOrigin()).isNull();
    }

    // Proves the model's suggested target maps to SUGGESTED with a LIKELY origin.
    @Test
    void mapping_suggestedTarget_becomesSuggestedAndLikely() {
        final GlossaryEntry suggested = entry(false).withSuggestedTarget("Хейл");

        assertThat(suggested.targetState()).isEqualTo(TargetState.SUGGESTED);
        assertThat(suggested.targetOrigin()).isEqualTo(Origin.LIKELY);
        assertThat(suggested.isSuggested()).isTrue();
    }

    // Proves a verified gender maps to BOOK and is enforced.
    @Test
    void mapping_verifiedGender_becomesBook() {
        final GlossaryEntry inferred = entry(false).withInferredGender(Gender.FEMALE);

        assertThat(inferred.genderOrigin()).isEqualTo(Origin.BOOK);
        assertThat(inferred.hasConfirmedGender()).isTrue();
    }

    // Proves a suggested gender maps to LIKELY and is not enforced.
    @Test
    void mapping_suggestedGender_becomesLikelyAndUnconfirmed() {
        final GlossaryEntry guessed = entry(false).withSuggestedGender(Gender.FEMALE);

        assertThat(guessed.genderOrigin()).isEqualTo(Origin.LIKELY);
        assertThat(guessed.isGenderSuggested()).isTrue();
        assertThat(guessed.hasConfirmedGender()).isFalse();
    }

    // Proves the canonical constructor normalises what cannot hold: a place kind off a place, blank text, a locked
    // guess.
    @Test
    void new_inconsistentParts_areNormalised() {
        final GlossaryEntry entry = new GlossaryEntry(
                "e1",
                "p1",
                "Velemir",
                List.of("Velemira"),
                false,
                "Велемир",
                TargetState.LOCKED,
                Origin.LIKELY,
                TermType.CHARACTER,
                PlaceKind.RIVER,
                Origin.USER,
                Gender.UNKNOWN,
                Origin.BOOK,
                false,
                true,
                "  ",
                null,
                null,
                0);

        assertThat(entry.targetOrigin()).isEqualTo(Origin.USER);
        assertThat(entry.placeKind()).isNull();
        assertThat(entry.genderOrigin()).isNull();
        assertThat(entry.noGenderEvidence()).isTrue();
        assertThat(entry.meaning()).isNull();
        assertThat(entry.names()).containsExactly("Velemir", "Velemira");
    }
}
