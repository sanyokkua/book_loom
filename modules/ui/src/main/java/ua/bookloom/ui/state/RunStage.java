package ua.bookloom.ui.state;

import java.util.Objects;
import ua.bookloom.api.pipeline.JobStage;
import ua.bookloom.api.pipeline.ProofreadCoverage;

/**
 * The stage a running or paused run is in, as the title bar's stage chip and the Translating strip show it (design D2
 * "Stages"): Prepare, Translate or Proofread, with what Proofread reads at the run's quality and the round in work.
 *
 * @param stage the stage in work
 * @param coverage what the Proofread stage reads at the run's quality; {@link ProofreadCoverage#NONE} at Fast, which
 *     makes no proofread call
 * @param round the Proofread round in work, 1 or 2; 0 before a round has started or in another stage
 */
public record RunStage(JobStage stage, ProofreadCoverage coverage, int round) {

    /** Rejects a missing part and a round no run has. */
    public RunStage {
        Objects.requireNonNull(stage, "stage");
        Objects.requireNonNull(coverage, "coverage");
        if (round < 0 || round > 2) {
            throw new IllegalArgumentException("round must be 0, 1 or 2: " + round);
        }
    }

    /** {@return {@code true} if the run reads the whole book a second time, at Max, {@code false} otherwise} */
    public boolean hasTwoRounds() {
        return coverage == ProofreadCoverage.MARKED_AND_DOUBTFUL_THEN_EVERY_PARAGRAPH;
    }

    /** {@return {@code true} if the run's Proofread stage makes no model call, at Fast, {@code false} otherwise} */
    public boolean isCodeOnly() {
        return coverage == ProofreadCoverage.NONE;
    }
}
