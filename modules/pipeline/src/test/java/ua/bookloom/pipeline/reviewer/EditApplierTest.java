package ua.bookloom.pipeline.reviewer;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** {@link EditApplier}: edits apply in order, each verified against the text the earlier ones left. */
class EditApplierTest {

    private static final EditApplier APPLIER = new EditApplier(new EditVerifier());
    private static final CandidateChecker CLEAN = text -> Optional.of(Set.of());
    private static final String CANDIDATE = "Марія сказав, що він втомилась.";

    private static ReviewEdit edit(final ReviewCriterion criterion, final String quote, final String replacement) {
        return new ReviewEdit(criterion, quote, replacement);
    }

    @Test
    void apply_genderPairOfEdits_appliesBothInOrder() {
        final EditOutcome outcome = APPLIER.apply(
                CANDIDATE,
                List.of(
                        edit(ReviewCriterion.GENDER, "Марія сказав", "Марія сказала"),
                        edit(ReviewCriterion.GENDER, "що він втомилась", "що вона втомилась")),
                CLEAN,
                Set.of());

        assertThat(outcome.text()).isEqualTo("Марія сказала, що вона втомилась.");
        assertThat(outcome.applied()).hasSize(2);
        assertThat(outcome.failed()).isEmpty();
    }

    @Test
    void apply_secondEditQuotesTextTheFirstRemoved_isIgnored() {
        final EditOutcome outcome = APPLIER.apply(
                CANDIDATE,
                List.of(
                        edit(ReviewCriterion.GENDER, "Марія сказав, що він", "Марія сказала, що вона"),
                        edit(ReviewCriterion.GENDER, "що він втомилась", "що вона втомилась")),
                CLEAN,
                Set.of());

        assertThat(outcome.text()).isEqualTo("Марія сказала, що вона втомилась.");
        assertThat(outcome.applied()).hasSize(1);
        assertThat(outcome.ignored()).isEqualTo(1);
    }

    @Test
    void apply_sameNameEditTwice_appliesItOnce() {
        final ReviewEdit edit = edit(ReviewCriterion.TERMINOLOGY, "Джон", "Джонатан");

        final EditOutcome outcome =
                APPLIER.apply("Джон прийшов.", List.of(edit, edit), CLEAN, Set.of(), List.of("Джонатан"));

        assertThat(outcome.text()).isEqualTo("Джонатан прийшов.");
        assertThat(outcome.applied()).hasSize(1);
    }

    @Test
    void apply_sameOmissionEditTwice_appliesItOnce() {
        final ReviewEdit edit = edit(ReviewCriterion.OMISSION, "двері", "старі двері");

        final EditOutcome outcome = APPLIER.apply("Він відчинив двері.", List.of(edit, edit), CLEAN, Set.of());

        assertThat(outcome.text()).isEqualTo("Він відчинив старі двері.");
        assertThat(outcome.applied()).hasSize(1);
    }

    @Test
    void apply_fluencyAndStyleEdits_areNotesAndNeverApplied() {
        final EditOutcome outcome = APPLIER.apply(
                CANDIDATE,
                List.of(
                        edit(ReviewCriterion.FLUENCY, "втомилась", "стомилась"),
                        edit(ReviewCriterion.STYLE, "що", "бо")),
                CLEAN,
                Set.of());

        assertThat(outcome.text()).isEqualTo(CANDIDATE);
        assertThat(outcome.notes()).hasSize(2);
        assertThat(outcome.applied()).isEmpty();
    }

    @Test
    void apply_oneRefusedEdit_isReportedWithItsReasonAndTheOthersStillApply() {
        final EditOutcome outcome = APPLIER.apply(
                "Він ⟦g0⟧пішов⟦g1⟧ і зник.",
                List.of(
                        edit(ReviewCriterion.INVENTED_WORD, "⟦g0⟧пішов⟦g1⟧", "пішов"),
                        edit(ReviewCriterion.AGREEMENT, "зник", "зникла")),
                CLEAN,
                Set.of());

        assertThat(outcome.text()).isEqualTo("Він ⟦g0⟧пішов⟦g1⟧ і зникла.");
        assertThat(outcome.failed())
                .singleElement()
                .satisfies(failed -> assertThat(failed.reason()).isEqualTo(Verification.FailureReason.TOKENS_CHANGED));
    }
}
