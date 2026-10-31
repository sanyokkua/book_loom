package ua.bookloom.api.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.pipeline.FieldEvidence.Basis;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.Narrator;
import ua.bookloom.api.project.NarratorPerson;
import ua.bookloom.api.project.Register;

/**
 * The fields a suggestion fills with only the model's most probable value (book-brief "Mark a suggested field likely
 * unless its basis holds"): those the brief marks likely until the person confirms or changes them.
 */
class BriefSuggestionLikelyTest {

    private static final FieldEvidence TWO_PASSAGES = new FieldEvidence(Basis.PASSAGES, List.of("q1", "q2"), 2, 4);
    private static final FieldEvidence GUESS = new FieldEvidence(Basis.LIKELY, List.of(), 0, 4);

    @Test
    void likelyFields_genreOnPassagesAndAudienceAndNarratorGuessed_areTheAudienceAndTheNarrator() {
        final BriefSuggestion suggestion = new BriefSuggestion(
                "Mystery",
                Register.NEUTRAL,
                null,
                "general",
                NarratorPerson.FIRST,
                Gender.FEMALE,
                Map.of(
                        BriefField.GENRE, TWO_PASSAGES,
                        BriefField.AUDIENCE, GUESS,
                        BriefField.REGISTER, TWO_PASSAGES,
                        BriefField.NARRATOR_GENDER, GUESS));

        assertThat(suggestion.likelyFields())
                .containsExactlyInAnyOrder(
                        ua.bookloom.api.project.BriefField.AUDIENCE, ua.bookloom.api.project.BriefField.NARRATOR);
        assertThat(suggestion.suggestedNarrator())
                .isEqualTo(
                        new Narrator(NarratorPerson.FIRST, Gender.FEMALE, Narrator.GenderOrigin.SUGGESTED, List.of()));
    }

    @Test
    void likelyFields_everyFieldOnABasis_isEmpty() {
        final BriefSuggestion suggestion = new BriefSuggestion(
                "Mystery",
                Register.CASUAL,
                null,
                null,
                NarratorPerson.THIRD,
                Gender.UNKNOWN,
                Map.of(BriefField.GENRE, TWO_PASSAGES, BriefField.REGISTER, TWO_PASSAGES));

        assertThat(suggestion.likelyFields()).isEmpty();
    }

    @Test
    void describe_likely_saysItIsTheModelsBestGuess() {
        assertThat(GUESS.describe()).isEqualTo("the model's best guess: 0 of 4 passages agree");
        assertThat(GUESS.isKept()).isTrue();
        assertThat(GUESS.isLikely()).isTrue();
    }
}
