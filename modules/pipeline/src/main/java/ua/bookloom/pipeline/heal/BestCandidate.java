package ua.bookloom.pipeline.heal;

import java.util.Objects;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.project.QaFinding;
import ua.bookloom.pipeline.qa.QaResult;

/**
 * The best candidate a segment's repair path has reached, carried from one round attempt into the next. A round's
 * result replaces it only when it has fewer blockers ({@link RoundProgress}), so a step can never leave the segment
 * worse than it was; the final target is always this one.
 *
 * @param qa the best candidate's hard-gate and soft outcome
 * @param machine the target the best candidate stands on, or neither form while no target passed every hard gate
 * @param rewriteBase the best candidate's masked text, or the original rejected masked reply; the next round
 *     rewrites it
 * @param lastGateFinding the finding a gate raised against the latest round's reply, which the next round repairs
 *     in place of the same gate's older finding; {@code null} after any evaluated round, since a reply that passed
 *     the gate leaves nothing to repair
 */
record BestCandidate(
        QaResult qa,
        MachineTarget machine,
        String rewriteBase,
        @Nullable QaFinding lastGateFinding) {

    /** Rejects a missing non-nullable component. */
    BestCandidate {
        Objects.requireNonNull(qa, "qa");
        Objects.requireNonNull(machine, "machine");
        Objects.requireNonNull(rewriteBase, "rewriteBase");
    }

    /**
     * The same best candidate after a round whose reply a gate refused.
     *
     * @param gateFinding the finding the gate raised
     * @return this state carrying {@code gateFinding} into the next round
     */
    BestCandidate withLastGateFinding(final QaFinding gateFinding) {
        return new BestCandidate(qa, machine, rewriteBase, gateFinding);
    }
}
