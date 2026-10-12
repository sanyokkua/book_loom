package ua.bookloom.api.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.project.Narrator.GenderOrigin;

/**
 * The brief's narrator keeps the quotes its gender was decided from and who decided it (15i.B4), so a later step can
 * check the quotes again against the glossary (15i.T5) and tell a person's choice from the code's.
 */
class NarratorTest {

    private static final String QUOTE = "Thank you, ma'am";

    @Test
    void genderOrigin_values_areThePersonVerifiedSuggestedAndInferredInOrder() {
        assertThat(GenderOrigin.values())
                .containsExactly(
                        GenderOrigin.PERSON, GenderOrigin.VERIFIED, GenderOrigin.SUGGESTED, GenderOrigin.INFERRED);
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
}
