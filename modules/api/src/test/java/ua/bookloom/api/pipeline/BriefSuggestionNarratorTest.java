package ua.bookloom.api.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.pipeline.FieldEvidence.Basis;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.Narrator;
import ua.bookloom.api.project.Narrator.GenderOrigin;
import ua.bookloom.api.project.NarratorPerson;
import ua.bookloom.api.project.Register;

/**
 * What a suggestion hands the brief about its narrator and its source language (15i.B4): a gender the code verified
 * from quotes reaches the brief with those quotes and the origin {@code VERIFIED}; a gender the person set stays the
 * person's; the language the passages are written in is only a proposal.
 */
class BriefSuggestionNarratorTest {

    private static final String QUOTE = "Thank you, ma'am";
    private static final String SECOND = "Mind the gate, miss";

    private static BriefSuggestion verifiedFemale() {
        return new BriefSuggestion(
                null,
                Register.NEUTRAL,
                null,
                null,
                NarratorPerson.FIRST,
                Gender.FEMALE,
                Map.of(BriefField.NARRATOR_GENDER, new FieldEvidence(Basis.PASSAGES, List.of(QUOTE, SECOND), 2, 4)),
                null);
    }

    // IF the quotes stayed behind on the suggestion, THEN 15i.T5 could not check them again after Names & style.
    @Test
    void suggestedNarrator_genderVerifiedFromQuotes_carriesTheQuotesAndTheVerifiedOrigin() {
        assertThat(verifiedFemale().suggestedNarrator())
                .isEqualTo(new Narrator(
                        NarratorPerson.FIRST, Gender.FEMALE, GenderOrigin.VERIFIED, List.of(QUOTE, SECOND)));
    }

    // The person chose male: aligning keeps it, and the narrator it hands back is still the person's, with no quotes.
    @Test
    void suggestedNarrator_alignedToAGenderThePersonSet_isThePersonsWithNoQuotes() {
        final BriefSuggestion aligned = verifiedFemale().alignedTo(new Narrator(NarratorPerson.FIRST, Gender.MALE));

        assertThat(aligned.suggestedNarrator()).isEqualTo(new Narrator(NarratorPerson.FIRST, Gender.MALE));
        assertThat(aligned.suggestedNarrator().genderOrigin()).isEqualTo(GenderOrigin.PERSON);
    }

    // An unknown gender has no quote to keep and nothing to re-check.
    @Test
    void suggestedNarrator_genderUnknown_hasNoQuotes() {
        final BriefSuggestion unknown = new BriefSuggestion(
                null,
                Register.NEUTRAL,
                null,
                null,
                NarratorPerson.FIRST,
                Gender.UNKNOWN,
                Map.of(BriefField.NARRATOR_GENDER, new FieldEvidence(Basis.UNCERTAIN, List.of(), 1, 4)),
                null);

        assertThat(unknown.suggestedNarrator().gender()).isEqualTo(Gender.UNKNOWN);
        assertThat(unknown.suggestedNarrator().evidence()).isEmpty();
    }

    @Test
    void languageProposal_suggestionBuiltWithoutOne_isNull() {
        assertThat(new BriefSuggestion(null, Register.NEUTRAL, null, null, NarratorPerson.UNSPECIFIED, Gender.UNKNOWN)
                        .languageProposal())
                .isNull();
    }

    // IF aligning to a set narrator dropped the proposal, THEN a first-person book under Auto would lose its language.
    @Test
    void alignedTo_setNarrator_keepsTheLanguageProposal() {
        final BriefSuggestion proposing = new BriefSuggestion(
                null, Register.NEUTRAL, null, null, NarratorPerson.THIRD, Gender.UNKNOWN, Map.of(), "fr");

        assertThat(proposing
                        .alignedTo(new Narrator(NarratorPerson.FIRST, Gender.UNKNOWN))
                        .languageProposal())
                .isEqualTo("fr");
    }
}
