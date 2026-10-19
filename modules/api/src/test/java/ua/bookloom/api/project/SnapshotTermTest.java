package ua.bookloom.api.project;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** A snapshot term keeps who decided its gender, so a replayed draft cannot turn a guess into the person's word. */
class SnapshotTermTest {

    @Test
    void constructor_suggestedOrigin_isKept() {
        final SnapshotTerm term = new SnapshotTerm(
                "Lyra", "Ліра", TermType.CHARACTER, Gender.FEMALE, false, false, GenderOrigin.SUGGESTED);

        assertThat(term.genderOrigin()).isEqualTo(GenderOrigin.SUGGESTED);
    }

    // A snapshot built before origins existed held only the person's or the code's confirmed genders.
    @Test
    void constructor_knownGenderWithNoOrigin_isThePersons() {
        assertThat(new SnapshotTerm("Lyra", "Ліра", TermType.CHARACTER, Gender.FEMALE, false, false, null)
                        .genderOrigin())
                .isEqualTo(GenderOrigin.PERSON);
    }

    @Test
    void constructor_unknownGender_hasNoOriginWhateverWasPassed() {
        final SnapshotTerm term = new SnapshotTerm(
                "Lyra", "Ліра", TermType.CHARACTER, Gender.UNKNOWN, false, false, GenderOrigin.SUGGESTED);

        assertThat(term.genderOrigin()).isNull();
    }
}
