package ua.bookloom.pipeline.heal;

import org.jspecify.annotations.Nullable;

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
}
