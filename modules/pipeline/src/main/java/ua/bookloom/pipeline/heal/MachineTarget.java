package ua.bookloom.pipeline.heal;

import org.jspecify.annotations.Nullable;
import ua.bookloom.pipeline.qa.QaResult;

/**
 * The last target that passed every hard gate, in both its restored and masked forms, or neither when none ever
 * did — what {@link SegmentOutcome#machineTarget()}/{@link SegmentOutcome#maskedMachineTarget()} are built from.
 *
 * @param restored the restored (unmasked) form, or {@code null} when no candidate has passed every hard gate yet
 * @param masked {@code restored}'s masked form, still carrying its {@code ⟦gN⟧} tokens; {@code null} exactly when
 *     {@code restored} is
 */
record MachineTarget(@Nullable String restored, @Nullable String masked) {

    private static final MachineTarget NONE = new MachineTarget(null, null);

    /**
     * The state before any target has passed every hard gate.
     *
     * @return a machine target carrying neither form
     */
    static MachineTarget none() {
        return NONE;
    }

    /**
     * The target a draft stands on: its own when it passed every hard gate, else none.
     *
     * @param outcome the drafted segment
     * @param qa the draft's evaluation
     * @return the draft's restored and masked forms, or none when a hard gate failed
     */
    static MachineTarget of(final DraftOutcome.Drafted outcome, final QaResult qa) {
        return outcome.restoredTarget() != null && qa.hardGatesPass()
                ? new MachineTarget(outcome.restoredTarget(), outcome.maskedForm())
                : NONE;
    }
}
