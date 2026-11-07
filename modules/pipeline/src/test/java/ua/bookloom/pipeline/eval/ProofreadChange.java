package ua.bookloom.pipeline.eval;

import java.util.Objects;

/**
 * What the Proofread stage decided for one segment, as the {@code PROOFREAD_NEVER_WORSE} grader reads it.
 *
 * @param segmentId the segment the stage read
 * @param kept whether the rewrite was kept (verdict KEPT)
 * @param checksBefore the failing-check count on the text before the stage
 * @param checksAfter the failing-check count on the text after it
 */
record ProofreadChange(String segmentId, boolean kept, int checksBefore, int checksAfter) {

    ProofreadChange {
        Objects.requireNonNull(segmentId, "segmentId");
    }

    /** Whether a kept rewrite left more checks failing than the text it replaced. */
    boolean isWorse() {
        return kept && checksAfter > checksBefore;
    }
}
