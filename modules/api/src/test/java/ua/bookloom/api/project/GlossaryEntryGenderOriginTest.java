package ua.bookloom.api.project;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * A known gender knows where it came from (15i.N0): the person's edit is {@code PERSON}, a model answer a window shows
 * or a pronoun count the code made is {@code VERIFIED}, a first-name or unverified guess is {@code SUGGESTED}, and an
 * unknown gender has no origin.
 */
class GlossaryEntryGenderOriginTest {

    private static GlossaryEntry unknown() {
        return GlossaryEntry.of("e1", "p1", "Wren", null, TermType.CHARACTER, Gender.UNKNOWN, false);
    }

    private static GlossaryEntry person() {
        return unknown().withGender(Gender.MALE);
    }

    private static GlossaryEntry verified() {
        return unknown().withInferredGender(Gender.MALE);
    }

    private static GlossaryEntry suggested() {
        return unknown().withSuggestedGender(Gender.MALE);
    }

    @Test
    void origin_enum_listsTheFourOriginsFromStrongestToWeakest() {
        assertThat(Origin.values()).containsExactly(Origin.USER, Origin.BOOK, Origin.RULE, Origin.LIKELY);
    }

    @Test
    void new_unknownGender_hasNoOrigin() {
        assertThat(unknown().genderOrigin()).isNull();
    }

    // IF a constructor with a known gender and no stated origin guessed a model origin, THEN a typed gender would be
    // overwritten by the next review; the old short constructors are the person's, as the dialog and the CSV use them.
    @Test
    void new_sevenComponentsWithAKnownGender_isThePersons() {
        final GlossaryEntry entry =
                GlossaryEntry.of("e1", "p1", "Wren", null, TermType.CHARACTER, Gender.FEMALE, false);

        assertThat(entry.genderOrigin()).isEqualTo(Origin.USER);
    }

    @Test
    void new_nineComponentsMarkedSuggested_isSuggested() {
        final GlossaryEntry entry = withOrigin(Gender.FEMALE, false, Origin.LIKELY);

        assertThat(entry.genderOrigin()).isEqualTo(Origin.LIKELY);
    }

    @ParameterizedTest
    @EnumSource(Origin.class)
    void new_unknownGenderWithAnyOrigin_hasNoOrigin(final Origin stated) {
        final GlossaryEntry entry = withOrigin(Gender.UNKNOWN, false, stated);

        assertThat(entry.genderOrigin()).isNull();
    }

    // IF a known gender could carry no origin, THEN the readers (review, table, checks) would each need a default.
    @Test
    void new_knownGenderWithNoStatedOrigin_isThePersons() {
        final GlossaryEntry entry = withOrigin(Gender.FEMALE, false, null);

        assertThat(entry.genderOrigin()).isEqualTo(Origin.USER);
    }

    @ParameterizedTest
    @EnumSource(
            value = Origin.class,
            names = {"USER", "BOOK"})
    void new_knownGenderWithAStatedOrigin_keepsIt(final Origin stated) {
        assertThat(withOrigin(Gender.FEMALE, false, stated).genderOrigin()).isEqualTo(stated);
    }

    // A locked record says nothing of who locked it; only the person's lock (withLocked) confirms a suggestion, so a
    // rule's lock leaves it suggested (15i review); a gender already verified by the book stays verified.
    @Test
    void new_lockedEntryWithASuggestedGender_keepsItSuggested() {
        assertThat(withOrigin(Gender.FEMALE, true, Origin.LIKELY).genderOrigin())
                .isEqualTo(Origin.LIKELY);
    }

    @Test
    void new_lockedEntryWithAVerifiedGender_staysVerified() {
        assertThat(withOrigin(Gender.FEMALE, true, Origin.BOOK).genderOrigin()).isEqualTo(Origin.BOOK);
    }

    @Test
    void withGender_aGender_isThePersons() {
        assertThat(unknown().withGender(Gender.FEMALE).genderOrigin()).isEqualTo(Origin.USER);
    }

    // IF the person agreeing with a suggestion or a verified gender changed nothing, THEN "choose a value to confirm
    // it" would be false and the next review could still overwrite the value the person chose.
    @Test
    void withGender_theGenderAlreadyHeldAsSuggestedOrVerified_becomesThePersons() {
        assertThat(suggested().withGender(Gender.MALE).genderOrigin()).isEqualTo(Origin.USER);
        assertThat(verified().withGender(Gender.MALE).genderOrigin()).isEqualTo(Origin.USER);
    }

    @Test
    void withGender_anotherGenderOverVerifiedOrSuggested_isThePersons() {
        assertThat(suggested().withGender(Gender.FEMALE).genderOrigin()).isEqualTo(Origin.USER);
        assertThat(verified().withGender(Gender.FEMALE).genderOrigin()).isEqualTo(Origin.USER);
    }

    // IF a gender cleared to unknown kept its old origin, THEN an entry would claim a person fixed nothing.
    @Test
    void withGender_clearedToUnknown_dropsTheOriginAndMarksSeedingTried() {
        final GlossaryEntry cleared = person().withGender(Gender.UNKNOWN);

        assertThat(cleared.gender()).isEqualTo(Gender.UNKNOWN);
        assertThat(cleared.genderOrigin()).isNull();
        assertThat(cleared.genderSeedTried()).isTrue();
    }

    @Test
    void withInferredGender_aNewGender_isVerified() {
        assertThat(unknown().withInferredGender(Gender.FEMALE).genderOrigin()).isEqualTo(Origin.BOOK);
    }

    // IF a verdict that disagrees replaced a suggestion silently as a person's choice, THEN the table would show a
    // confirmed gender nobody confirmed.
    @Test
    void withInferredGender_anotherGenderOverASuggestion_isVerified() {
        final GlossaryEntry replaced = suggested().withInferredGender(Gender.FEMALE);

        assertThat(replaced.gender()).isEqualTo(Gender.FEMALE);
        assertThat(replaced.genderOrigin()).isEqualTo(Origin.BOOK);
    }

    @Test
    void withInferredGender_clearedToUnknown_dropsTheOrigin() {
        final GlossaryEntry cleared = verified().withInferredGender(Gender.UNKNOWN);

        assertThat(cleared.gender()).isEqualTo(Gender.UNKNOWN);
        assertThat(cleared.genderOrigin()).isNull();
    }

    // The gender a person set is never downgraded to "verified" by a model repeating it.
    @Test
    void withInferredGender_theGenderTheEntryAlreadyHolds_changesNothing() {
        assertThat(person().withInferredGender(Gender.MALE).genderOrigin()).isEqualTo(Origin.USER);
        assertThat(suggested().withInferredGender(Gender.MALE).genderOrigin()).isEqualTo(Origin.LIKELY);
        assertThat(verified().withInferredGender(Gender.MALE).genderOrigin()).isEqualTo(Origin.BOOK);
    }

    @Test
    void withSuggestedGender_aGender_isSuggestedAndMarksSeedingTried() {
        final GlossaryEntry seeded = unknown().withSuggestedGender(Gender.FEMALE);

        assertThat(seeded.gender()).isEqualTo(Gender.FEMALE);
        assertThat(seeded.genderOrigin()).isEqualTo(Origin.LIKELY);
        assertThat(seeded.genderSeedTried()).isTrue();
    }

    @Test
    void withSuggestedGender_unknown_hasNoOrigin() {
        assertThat(unknown().withSuggestedGender(Gender.UNKNOWN).genderOrigin()).isNull();
    }

    @Test
    void withLocked_onASuggestedGender_confirmsItAsThePersons() {
        assertThat(suggested().withLocked(true).genderOrigin()).isEqualTo(Origin.USER);
    }

    @Test
    void withLocked_onAVerifiedOrPersonsGender_keepsItsOrigin() {
        assertThat(verified().withLocked(true).genderOrigin()).isEqualTo(Origin.BOOK);
        assertThat(person().withLocked(true).genderOrigin()).isEqualTo(Origin.USER);
    }

    // IF a copy that touches another field lost the origin, THEN a target edit would make a verified gender the
    // person's, or a suggestion look confirmed.
    @ParameterizedTest
    @MethodSource("editsThatLeaveTheGenderAlone")
    void otherEdits_leaveTheGenderOriginAsItWas(final UnaryOperator<GlossaryEntry> edit) {
        assertThat(edit.apply(verified()).genderOrigin()).isEqualTo(Origin.BOOK);
        assertThat(edit.apply(suggested()).genderOrigin()).isEqualTo(Origin.LIKELY);
        assertThat(edit.apply(person()).genderOrigin()).isEqualTo(Origin.USER);
    }

    static Stream<Arguments> editsThatLeaveTheGenderAlone() {
        return Stream.of(
                Arguments.of(Named.<UnaryOperator<GlossaryEntry>>of("withTarget", entry -> entry.withTarget("Рен"))),
                Arguments.of(Named.<UnaryOperator<GlossaryEntry>>of(
                        "withSuggestedTarget", entry -> entry.withSuggestedTarget("Рен"))),
                Arguments.of(
                        Named.<UnaryOperator<GlossaryEntry>>of("withType", entry -> entry.withType(TermType.OTHER))),
                Arguments.of(Named.<UnaryOperator<GlossaryEntry>>of("accepted", GlossaryEntry::accepted)),
                Arguments.of(Named.<UnaryOperator<GlossaryEntry>>of(
                        "withForms", entry -> entry.withForms(List.of("Wrens")))));
    }

    private static GlossaryEntry withOrigin(final Gender gender, final boolean locked, @Nullable final Origin stated) {
        return new GlossaryEntry(
                "e1",
                "p1",
                "Wren",
                List.of(),
                false,
                locked ? "Рен" : null,
                locked ? TargetState.LOCKED : TargetState.PINNED,
                Origin.USER,
                TermType.CHARACTER,
                null,
                Origin.USER,
                gender,
                stated,
                false,
                false,
                null,
                null,
                null);
    }
}
