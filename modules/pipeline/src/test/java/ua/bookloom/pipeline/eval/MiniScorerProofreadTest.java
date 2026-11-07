package ua.bookloom.pipeline.eval;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/** The {@code PROOFREAD_NEVER_WORSE} grader, kept apart from {@link MiniScorerTest} by the file-length limit. */
class MiniScorerProofreadTest {

    @Test
    void proofreadNeverWorse_keptChangeWithMoreFailingChecks_fails() {
        final MiniScorer.Verdict verdict = MiniGraders.proofreadNeverWorse(List.of(
                new ProofreadChange("a", true, 1, 1),
                new ProofreadChange("b", true, 0, 2),
                new ProofreadChange("c", false, 0, 3)));

        assertThat(verdict.ok()).isFalse();
        assertThat(verdict.detail()).contains("[b]");
    }

    @Test
    void proofreadNeverWorse_noKeptChangeWorse_passes() {
        final MiniScorer.Verdict verdict = MiniGraders.proofreadNeverWorse(
                List.of(new ProofreadChange("a", true, 2, 1), new ProofreadChange("c", false, 0, 3)));

        assertThat(verdict.ok()).isTrue();
    }
}
