package ua.bookloom.pipeline.heal;

import java.util.Objects;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.project.QaFinding;
import ua.bookloom.pipeline.qa.QaResult;

/**
 * One segment's state carried from one self-heal round attempt into the next: the last evaluation, the last target
 * that passed every hard gate, and the text the next round rewrites.
 *
 * @param qa the last evaluated candidate's hard-gate and soft outcome
 * @param machine the last target that passed every hard gate, or neither form
 * @param rewriteBase the latest target that passed the placeholder gate, or the original rejected masked reply
 * @param lastGateFinding the finding a gate raised against the latest round's reply, which the next round repairs
 *     in place of the same gate's older finding; {@code null} after any evaluated round, since a reply that passed
 *     the gate leaves nothing to repair
 */
record RoundState(
        QaResult qa,
        MachineTarget machine,
        String rewriteBase,
        @Nullable QaFinding lastGateFinding) {

    /** Rejects a missing non-nullable component. */
    RoundState {
        Objects.requireNonNull(qa, "qa");
        Objects.requireNonNull(machine, "machine");
        Objects.requireNonNull(rewriteBase, "rewriteBase");
    }

    /**
     * The same state after a round whose reply a gate refused.
     *
     * @param gateFinding the finding the gate raised
     * @return this state carrying {@code gateFinding} into the next round
     */
    RoundState withLastGateFinding(final QaFinding gateFinding) {
        return new RoundState(qa, machine, rewriteBase, gateFinding);
    }
}
