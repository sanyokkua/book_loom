package ua.bookloom.api.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ua.bookloom.api.project.Narrator.GenderOrigin;

/**
 * The brief's narrator keeps the quotes its gender was decided from and who decided it (15i.B4), so a later step can
 * check the quotes again against the glossary (15i.T5) and tell a person's choice from the code's.
 */
class NarratorTest {

    private static final String QUOTE = "Thank you, ma'am";

    @Test
    void genderOrigin_values_areThePersonVerifiedSuggestedInferredAndNoneInOrder() {
        assertThat(GenderOrigin.values())
                .containsExactly(
                        GenderOrigin.PERSON,
                        GenderOrigin.VERIFIED,
                        GenderOrigin.SUGGESTED,
                        GenderOrigin.INFERRED,
                        GenderOrigin.NONE);
    }

    // IF an unknown gender claimed the person's origin, THEN a later step would see a choice the person never made and
    // leave the gender unfilled.
    @Test
    void narrator_unknownGender_hasNoOriginWhoeverBuiltIt() {
        assertThat(new Narrator(NarratorPerson.FIRST, Gender.UNKNOWN).genderOrigin())
                .isEqualTo(GenderOrigin.NONE);
        assertThat(Narrator.unspecified().genderOrigin()).isEqualTo(GenderOrigin.NONE);
        assertThat(new Narrator(NarratorPerson.FIRST, Gender.UNKNOWN, GenderOrigin.PERSON, List.of(QUOTE))
                        .genderOrigin())
                .isEqualTo(GenderOrigin.NONE);
    }

    // IF an unknown gender dropped the quotes it was tried from, THEN a name that becomes a verified glossary person
    // later (15i.T5) could never be matched to the narrator: the quote is held as pending evidence.
    @Test
    void narrator_unknownGenderWithQuotes_keepsThePendingQuotes() {
        final Narrator pending = new Narrator(NarratorPerson.FIRST, Gender.UNKNOWN, GenderOrigin.NONE, List.of(QUOTE));

        assertThat(pending.evidence()).containsExactly(QUOTE);
        assertThat(pending.genderOrigin()).isEqualTo(GenderOrigin.NONE);
        assertThat(pending.hasCheckableGender()).isFalse();
        assertThat(pending).isNotEqualTo(new Narrator(NarratorPerson.FIRST, Gender.UNKNOWN));
    }

    // INFERRED is the run's own count of the narrator's words (15i.T5): enforced like a verified gender, so the
    // minority slips are revised and later drafts are held to it; a guess nobody confirmed is still not.
    @ParameterizedTest(name = "[{index}] {0} {1} {2} -> {3}")
    @CsvSource({
        "FIRST, INFERRED, MALE, true",
        "FIRST, INFERRED, FEMALE, true",
        "THIRD, INFERRED, MALE, false",
        "FIRST, SUGGESTED, MALE, false"
    })
    void hasCheckableGender_inferredOrigin_isCheckableLikeAVerifiedOne(
            final NarratorPerson person, final GenderOrigin origin, final Gender gender, final boolean checkable) {
        assertThat(new Narrator(person, gender, origin, List.of()).hasCheckableGender())
                .isEqualTo(checkable);
    }

    @Test
    void narrator_knownGenderWithNoOrigin_isRejected() {
        assertThatThrownBy(() -> new Narrator(NarratorPerson.FIRST, Gender.MALE, GenderOrigin.NONE, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // The person changed only who tells the story: the gender's origin and quotes are still true of it.
    @Test
    void withPerson_verifiedGender_keepsTheOriginAndTheQuotes() {
        final Narrator verified =
                new Narrator(NarratorPerson.FIRST, Gender.FEMALE, GenderOrigin.VERIFIED, List.of(QUOTE));

        assertThat(verified.withPerson(NarratorPerson.THIRD))
                .isEqualTo(new Narrator(NarratorPerson.THIRD, Gender.FEMALE, GenderOrigin.VERIFIED, List.of(QUOTE)));
    }

    // IF a narrator built by a person's choice carried an origin of the code, THEN a later re-check could undo it.
    @Test
    void narrator_personAndGenderOnly_isThePersonsChoiceWithNoQuotes() {
        final Narrator narrator = new Narrator(NarratorPerson.FIRST, Gender.MALE);

        assertThat(narrator.genderOrigin()).isEqualTo(GenderOrigin.PERSON);
        assertThat(narrator.evidence()).isEmpty();
    }

    @Test
    void narrator_verifiedFromAQuote_keepsTheQuoteAndTheOrigin() {
        final Narrator narrator =
                new Narrator(NarratorPerson.FIRST, Gender.FEMALE, GenderOrigin.VERIFIED, List.of(QUOTE));

        assertThat(narrator.genderOrigin()).isEqualTo(GenderOrigin.VERIFIED);
        assertThat(narrator.evidence()).containsExactly(QUOTE);
        assertThat(narrator.hasCheckableGender()).isTrue();
    }

    // IF the record shared the caller's list, THEN a later edit of that list would rewrite the stored evidence.
    @Test
    void narrator_quotesListChangedAfterwards_keepsItsOwnCopy() {
        final List<String> quotes = new ArrayList<>(List.of(QUOTE));
        final Narrator narrator = new Narrator(NarratorPerson.FIRST, Gender.FEMALE, GenderOrigin.VERIFIED, quotes);

        quotes.add("another line");

        assertThat(narrator.evidence()).containsExactly(QUOTE);
        assertThatThrownBy(() -> narrator.evidence().add("x")).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @SuppressWarnings("NullAway")
    void narrator_missingOriginOrQuotes_isRejected() {
        assertThatThrownBy(() -> new Narrator(NarratorPerson.FIRST, Gender.FEMALE, null, List.of()))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new Narrator(NarratorPerson.FIRST, Gender.FEMALE, GenderOrigin.VERIFIED, null))
                .isInstanceOf(NullPointerException.class);
    }

    // Two narrators that differ only in who decided the gender are not the same narrator: the screen marks one.
    @Test
    void equals_sameGenderDifferentOrigin_isNotEqual() {
        assertThat(new Narrator(NarratorPerson.FIRST, Gender.FEMALE, GenderOrigin.VERIFIED, List.of(QUOTE)))
                .isNotEqualTo(new Narrator(NarratorPerson.FIRST, Gender.FEMALE));
    }

    // IF a narrator gender nobody confirmed were held against the text, THEN a wrong guess shown as "(suggested)"
    // would be "corrected" into every first-person paragraph (15i.T1).
    @ParameterizedTest(name = "[{index}] {0} {1} -> {2}")
    @CsvSource({
        "PERSON, MALE, true",
        "PERSON, FEMALE, true",
        "VERIFIED, MALE, true",
        "VERIFIED, FEMALE, true",
        "SUGGESTED, MALE, false",
        "SUGGESTED, FEMALE, false"
    })
    void hasCheckableGender_firstPersonByOrigin_isCheckableOnlyWhenConfirmed(
            final GenderOrigin origin, final Gender gender, final boolean checkable) {
        final Narrator narrator = new Narrator(NarratorPerson.FIRST, gender, origin, List.of());

        assertThat(narrator.hasCheckableGender()).isEqualTo(checkable);
    }
}
