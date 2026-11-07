package ua.bookloom.api.project;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.document.SegmentKind;
import ua.bookloom.api.document.SegmentStatus;

/** A segment's proofread verdict rides on the record, and a commit counts the verdicts it carries. */
class ProofreadVerdictTest {

    private static final ProofreadVerdict KEPT = new ProofreadVerdict(
            "fp", ProofreadOutcome.KEPT, "gender now known", "Тарн прийшов.", SegmentStatus.FLAGGED);

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
}
