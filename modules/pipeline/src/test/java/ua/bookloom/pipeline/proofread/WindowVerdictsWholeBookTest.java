package ua.bookloom.pipeline.proofread;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.pipeline.proofread.ProofreadFixtures.decided;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.project.ProofreadOutcome;
import ua.bookloom.api.project.ProofreadVerdict;
import ua.bookloom.api.project.SegmentRecord;

/**
 * Max's whole-book round keeps a small rewrite of an unmarked paragraph at an equal score (design D14); the marked
 * round, and a marked paragraph in either round, keep the score rule. A word doubled across a comma is a slip no
 * check scores (the doubled-word check leaves it to speech), so its fix leaves the score equal. Invented ru→uk lines.
 */
class WindowVerdictsWholeBookTest {

    private static final String ID = WindowVerdictsTest.ID;
    private static final String SOURCE = "Он пошёл домой по длинной дороге.";
    private static final String DOUBLED = "Він пішов, пішов додому довгою дорогою.";
    private static final String FIXED = "Він пішов додому довгою дорогою.";

    @Test
    void rewrite_unmarkedParagraphInWholeBookRound_keptRevised() {
        final SegmentRecord record = decide(List.of(), true);

        assertThat(record.machineTarget()).isEqualTo(FIXED);
        assertThat(record.status()).isEqualTo(SegmentStatus.REVISED);
        assertThat(record.proofread())
                .extracting(ProofreadVerdict::outcome, ProofreadVerdict::before)
                .containsExactly(ProofreadOutcome.KEPT, DOUBLED);
    }

    @Test
    void rewrite_unmarkedParagraphInMarkedRound_rejectedNoGain() {
        final SegmentRecord record = decide(List.of(), false);

        assertThat(record.machineTarget()).isEqualTo(DOUBLED);
        assertThat(record.proofread())
                .extracting(ProofreadVerdict::outcome, ProofreadVerdict::reason)
                .containsExactly(ProofreadOutcome.REJECTED, "no-gain");
    }

    @Test
    void rewrite_markedParagraphInWholeBookRound_rejectedNoGain() {
        final SegmentRecord record = decide(List.of("doubt: register"), true);

        assertThat(record.machineTarget()).isEqualTo(DOUBLED);
        assertThat(record.proofread())
                .extracting(ProofreadVerdict::outcome, ProofreadVerdict::reason)
                .containsExactly(ProofreadOutcome.REJECTED, "no-gain");
    }

    private static SegmentRecord decide(final List<String> reasons, final boolean wholeBook) {
        final Segment segment = ProofreadFixtures.chapter("ch02.md", SOURCE)
                .units()
                .getFirst()
                .segments()
                .getFirst();
        final SegmentRecord record = decided(ID, SegmentStatus.ACCEPTED, DOUBLED);
        final WindowVerdicts.Decided decided = WindowVerdictsTest.verdicts(List.of(), List.of())
                .decide(
                        List.of(new WindowVerdicts.Read(
                                segment,
                                record,
                                Objects.requireNonNull(MarkList.maskedTarget(record)),
                                reasons,
                                wholeBook)),
                        Map.of(ID, FIXED),
                        "r2:whole");
        return decided.records().getFirst();
    }
}
