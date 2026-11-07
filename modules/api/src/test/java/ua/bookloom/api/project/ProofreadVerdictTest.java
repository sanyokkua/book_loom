package ua.bookloom.api.project;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ua.bookloom.api.document.SegmentKind;
import ua.bookloom.api.document.SegmentStatus;

/** A segment's proofread verdict rides on the record, and a commit counts the verdicts it carries. */
class ProofreadVerdictTest {

    private static final ProofreadVerdict KEPT = new ProofreadVerdict(
            "fp",
            ProofreadOutcome.KEPT,
            "gender now known",
            "Тарн прийшов.",
            SegmentStatus.FLAGGED,
            "Тарн прийшов.",
            List.of());

    private static SegmentRecord segment() {
        return new SegmentRecord(
                "p1",
                "s1",
                "u1",
                0,
                SegmentKind.PARAGRAPH,
                SegmentStatus.ACCEPTED,
                "Тарн прийшла.",
                "Тарн прийшла.",
                null,
                null,
                1.0,
                null,
                List.of(),
                SegmentPath.DRAFT,
                false,
                null);
    }

    @Test
    void withProofread_verdict_keepsEveryOtherFieldAndClearsOnNull() {
        final SegmentRecord proofread = segment().withProofread(KEPT);

        assertThat(proofread.proofread()).isEqualTo(KEPT);
        assertThat(proofread.machineTarget()).isEqualTo("Тарн прийшла.");
        assertThat(proofread.withProofread(null).proofread()).isNull();
    }

    @Test
    void verdictCount_oneOfTwoSegmentsProofread_isOne() {
        final ChunkCommit commit = new ChunkCommit(
                "p1",
                List.of(segment().withProofread(KEPT), segment()),
                List.of(),
                List.of(),
                List.of(),
                List.of("d1"),
                null);

        assertThat(commit.verdictCount()).isEqualTo(1);
        assertThat(commit.resolvedDeferralIds()).containsExactly("d1");
    }

    @Test
    void newCommit_withoutResolvedIds_settlesNothing() {
        assertThat(new ChunkCommit("p1", List.of(), List.of(), List.of(), List.of()).resolvedDeferralIds())
                .isEmpty();
    }

    // Undo proofread needs the text, its masked form and the status before; a verdict that kept nothing has none.
    @ParameterizedTest(name = "{0}")
    @CsvSource({"KEPT, true", "PROPOSED, false", "REJECTED, false", "UNCHANGED, false"})
    void isUndoable_onlyAKeptEditWithItsEarlierText(final ProofreadOutcome outcome, final boolean undoable) {
        final ProofreadVerdict verdict = new ProofreadVerdict(
                "fp", outcome, "", "Тарн прийде.", SegmentStatus.ACCEPTED, "Тарн прийде.", List.of());

        assertThat(verdict.isUndoable()).isEqualTo(undoable);
    }

    @Test
    void isUndoable_keptWithoutAnEarlierMaskedTextOrStatus_isFalse() {
        assertThat(new ProofreadVerdict("fp", ProofreadOutcome.KEPT, "").isUndoable())
                .isFalse();
        assertThat(new ProofreadVerdict("fp", ProofreadOutcome.KEPT, "", "a", SegmentStatus.ACCEPTED, null, List.of())
                        .isUndoable())
                .isFalse();
        assertThat(new ProofreadVerdict("fp", ProofreadOutcome.KEPT, "", "a", null, "a", List.of()).isUndoable())
                .isFalse();
    }

    @Test
    void restamped_newFingerprint_keepsEverythingElse() {
        final ProofreadVerdict now = KEPT.restamped("r2:new");

        assertThat(now.fingerprint()).isEqualTo("r2:new");
        assertThat(now).usingRecursiveComparison().ignoringFields("fingerprint").isEqualTo(KEPT);
    }
}
