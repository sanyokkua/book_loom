package ua.bookloom.api.project;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * An unknown gender can carry the mark "the scan found no evidence in the book" (15i.N5): the mark exists only while
 * the gender is unknown, and any later gender, from the book or from the person, or the person's edit back to unknown,
 * clears it.
 */
class GlossaryEntryNoEvidenceTest {

    private static GlossaryEntry unknown() {
        return new GlossaryEntry("e1", "p1", "Wren", null, TermType.CHARACTER, Gender.UNKNOWN, false);
    }

    @Test
    void new_anyEntry_hasNoMarkByDefault() {
        assertThat(unknown().noGenderEvidence()).isFalse();
    }

    @Test
    void withNoGenderEvidence_unknownGender_marksTheEntryAndKeepsTheGenderUnknown() {
        final GlossaryEntry marked = unknown().withNoGenderEvidence();

        assertThat(marked.noGenderEvidence()).isTrue();
        assertThat(marked.gender()).isEqualTo(Gender.UNKNOWN);
        assertThat(marked.genderOrigin()).isNull();
    }

    // IF a known gender could carry the mark, THEN the table would say "no evidence" beside a gender it shows.
    @Test
    void withNoGenderEvidence_knownGender_leavesTheEntryAsItIs() {
        final GlossaryEntry known = unknown().withInferredGender(Gender.MALE);

        assertThat(known.withNoGenderEvidence()).isEqualTo(known);
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(
            value = Gender.class,
            names = {"MALE", "FEMALE"})
    void withGender_aMarkedEntry_isThePersonsAndUnmarked(final Gender gender) {
        final GlossaryEntry edited = unknown().withNoGenderEvidence().withGender(gender);

        assertThat(edited.noGenderEvidence()).isFalse();
        assertThat(edited.genderOrigin()).isEqualTo(GenderOrigin.PERSON);
    }

    // The person's edit back to "not specified" is their decision, not the scan's finding.
    @Test
    void withGender_unknownOnAMarkedEntry_clearsTheMark() {
        final GlossaryEntry edited = unknown().withNoGenderEvidence().withGender(Gender.UNKNOWN);

        assertThat(edited.noGenderEvidence()).isFalse();
    }

    @Test
    void withInferredGender_aMarkedEntry_clearsTheMark() {
        final GlossaryEntry later = unknown().withNoGenderEvidence().withInferredGender(Gender.FEMALE);

        assertThat(later.noGenderEvidence()).isFalse();
        assertThat(later.genderOrigin()).isEqualTo(GenderOrigin.VERIFIED);
    }

    @Test
    void withSuggestedGender_aMarkedEntry_clearsTheMark() {
        final GlossaryEntry later = unknown().withNoGenderEvidence().withSuggestedGender(Gender.FEMALE);

        assertThat(later.noGenderEvidence()).isFalse();
        assertThat(later.genderOrigin()).isEqualTo(GenderOrigin.SUGGESTED);
    }

    // Other edits (a target, a lock, a type, aliases) say nothing about the gender: the finding stays.
    @ParameterizedTest(name = "{0}")
    @MethodSource("editsThatSayNothingOfTheGender")
    void otherWithers_aMarkedEntry_keepTheMark(final String name, final UnaryOperator<GlossaryEntry> edit) {
        final GlossaryEntry marked = unknown().withNoGenderEvidence();

        assertThat(edit.apply(marked).noGenderEvidence()).isTrue();
    }

    private static Stream<Arguments> editsThatSayNothingOfTheGender() {
        return Stream.of(
                edit("withTarget", entry -> entry.withTarget("Врен")),
                edit("withSuggestedTarget", entry -> entry.withSuggestedTarget("Врен")),
                edit("withLocked", entry -> entry.withLocked(true)),
                edit("withRuleLock", GlossaryEntry::withRuleLock),
                edit("withType", entry -> entry.withType(TermType.CHARACTER)),
                edit("accepted", GlossaryEntry::accepted),
                edit("withAliases", entry -> entry.withAliases(List.of("Wrens"))));
    }

    private static Arguments edit(final String name, final UnaryOperator<GlossaryEntry> edit) {
        return Arguments.of(name, edit);
    }
}
