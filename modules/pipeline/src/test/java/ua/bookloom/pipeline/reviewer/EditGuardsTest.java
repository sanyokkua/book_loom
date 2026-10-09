package ua.bookloom.pipeline.reviewer;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** The result of a reviewer edit is judged as hard as the edit itself: a damaging edit is worse than none. */
class EditGuardsTest {

    private static final EditApplier APPLIER = new EditApplier(new EditVerifier());
    private static final CandidateChecker CLEAN = text -> Optional.of(Set.of());
    private static final String CANDIDATE = "Він пішов додому. Потім він заснув.";

    private static EditOutcome apply(final String candidate, final ReviewEdit edit, final List<String> renderings) {
        return APPLIER.apply(candidate, List.of(edit), CLEAN, Set.of(), renderings, Set.of());
    }

    private static void assertRefused(
            final EditOutcome outcome, final String candidate, final Verification.FailureReason reason) {
        assertThat(outcome.text()).isEqualTo(candidate);
        assertThat(outcome.applied()).isEmpty();
        assertThat(outcome.failed())
                .singleElement()
                .satisfies(f -> assertThat(f.reason()).isEqualTo(reason));
    }

    @Test
    void apply_editCreatesDoubledWord_isRefused() {
        final String candidate = "Він побачив старий будинок.";
        final EditOutcome outcome = apply(
                candidate,
                new ReviewEdit(ReviewCriterion.MEANING, "старий будинок", "старий старий будинок"),
                List.of());

        assertRefused(outcome, candidate, Verification.FailureReason.DOUBLED_WORD);
    }

    @Test
    void apply_editLowercasesASentenceStart_isRefused() {
        final EditOutcome outcome =
                apply(CANDIDATE, new ReviewEdit(ReviewCriterion.AGREEMENT, "Потім він", "потім він"), List.of());

        assertRefused(outcome, CANDIDATE, Verification.FailureReason.CASE_CHANGED);
    }

    @Test
    void apply_editCapitalisesAWordMidSentence_isRefused() {
        final EditOutcome outcome =
                apply(CANDIDATE, new ReviewEdit(ReviewCriterion.AGREEMENT, "додому", "Додому"), List.of());

        assertRefused(outcome, CANDIDATE, Verification.FailureReason.CASE_CHANGED);
    }

    @Test
    void apply_editBringsALatinLetterIntoACyrillicWord_isRefused() {
        final EditOutcome outcome =
                apply(CANDIDATE, new ReviewEdit(ReviewCriterion.AGREEMENT, "заснув", "заснуv"), List.of());

        assertRefused(outcome, CANDIDATE, Verification.FailureReason.FOREIGN_SCRIPT);
    }

    @Test
    void apply_editAddsASentence_isRefused() {
        final String candidate = "Він пішов додому.";
        final EditOutcome outcome =
                apply(candidate, new ReviewEdit(ReviewCriterion.MEANING, "додому", "додому. Там"), List.of());

        assertRefused(outcome, candidate, Verification.FailureReason.COUNTS_CHANGED);
    }

    @Test
    void apply_editDropsADialogueDash_isRefused() {
        final String candidate = "— Він пішов додому.";
        final EditOutcome outcome =
                apply(candidate, new ReviewEdit(ReviewCriterion.MEANING, "— Він пішов", "Він пішов"), List.of());

        assertRefused(outcome, candidate, Verification.FailureReason.COUNTS_CHANGED);
    }

    @Test
    void apply_editReinflectsAGlossaryName_isRefused() {
        final String candidate = "Синіх вогнів не було.";
        final EditOutcome outcome = apply(
                candidate,
                new ReviewEdit(ReviewCriterion.INVENTED_WORD, "Синіх вогнів", "Синій вогонь"),
                List.of("Сині Вогні"));

        assertRefused(outcome, candidate, Verification.FailureReason.GLOSSARY_RENDERING);
    }

    @Test
    void apply_meaningEditSwapsAContentWord_isRefusedAndLeftToTheDirectedFix() {
        final String candidate = "Він побачив криголам у порту.";
        final EditOutcome outcome = apply(
                candidate, new ReviewEdit(ReviewCriterion.MEANING, "побачив криголам", "побачив льодовик"), List.of());

        assertRefused(outcome, candidate, Verification.FailureReason.MEANING_SWAP);
    }

    @Test
    void apply_meaningEditKeepingTheStem_isApplied() {
        final String candidate = "Він побачив криголам у порту.";
        final EditOutcome outcome = apply(
                candidate, new ReviewEdit(ReviewCriterion.MEANING, "побачив криголам", "побачив криголама"), List.of());

        assertThat(outcome.text()).isEqualTo("Він побачив криголама у порту.");
        assertThat(outcome.failed()).isEmpty();
    }

    @Test
    void apply_meaningEditUsingTheGlossaryRendering_isApplied() {
        final String candidate = "Він побачив крижину у порту.";
        final EditOutcome outcome = apply(
                candidate,
                new ReviewEdit(ReviewCriterion.MEANING, "побачив крижину", "побачив льодовик"),
                List.of("льодовик"));

        assertThat(outcome.text()).isEqualTo("Він побачив льодовик у порту.");
    }

    @Test
    void apply_editThatChangesNothing_isDroppedWithoutCounting() {
        final EditOutcome outcome =
                apply(CANDIDATE, new ReviewEdit(ReviewCriterion.AGREEMENT, "додому", "додому"), List.of());

        assertThat(outcome.applied()).isEmpty();
        assertThat(outcome.failed()).isEmpty();
        assertThat(outcome.ignored()).isZero();
    }

    @Test
    void apply_quoteNotInTheText_isIgnoredNotRefused() {
        final EditOutcome outcome =
                apply(CANDIDATE, new ReviewEdit(ReviewCriterion.AGREEMENT, "нема такого", "є такий"), List.of());

        assertThat(outcome.failed()).isEmpty();
        assertThat(outcome.ignored()).isEqualTo(1);
    }

    // A refusal that disproves the edit is set aside; the draft is not in doubt (15h.A4).
    @Test
    void apply_caseChange_isDisprovedAndNotEvidence() {
        final EditOutcome outcome =
                apply(CANDIDATE, new ReviewEdit(ReviewCriterion.AGREEMENT, "Потім він", "потім він"), List.of());

        assertThat(outcome.disproved()).hasSize(1);
        assertThat(outcome.evidenced()).isEmpty();
    }

    @Test
    void apply_meaningSwap_isEvidenceAndNotDisproved() {
        final String candidate = "Він побачив криголам у порту.";
        final EditOutcome outcome = apply(
                candidate, new ReviewEdit(ReviewCriterion.MEANING, "побачив криголам", "побачив льодовик"), List.of());

        assertThat(outcome.evidenced()).hasSize(1);
        assertThat(outcome.disproved()).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({
        "AMBIGUOUS_QUOTE,false",
        "MEANING_SWAP,false",
        "CASE_CHANGED,true",
        "GLOSSARY_RENDERING,true",
        "FOREIGN_SCRIPT,true",
        "DOUBLED_WORD,true",
        "TOKENS_CHANGED,true",
        "CHECKS_REFUSED,true",
        "BLOCKERS_GREW,true",
        "COUNTS_CHANGED,true"
    })
    void disprovesEdit_perReason_onlyAMeaningSwapAndAnAmbiguousQuoteKeepTheFinding(
            final Verification.FailureReason reason, final boolean disproves) {
        assertThat(reason.disprovesEdit()).isEqualTo(disproves);
    }
}
