package ua.bookloom.pipeline.heal;

import java.util.Set;
import org.jspecify.annotations.Nullable;
import ua.bookloom.pipeline.qa.CheckName;
import ua.bookloom.pipeline.qa.CheckResult;
import ua.bookloom.pipeline.qa.QaResult;

/**
 * The last target that passed every hard gate, or failed only a text check that spoils its typography and not its
 * meaning (quote balance, script purity), in both its restored and masked forms, or neither when none ever did — what {@link SegmentOutcome#machineTarget()}/{@link SegmentOutcome#maskedMachineTarget()} are built from.
 *
 * @param restored the restored (unmasked) form, or {@code null} when no candidate has passed every hard gate yet
 * @param masked {@code restored}'s masked form, still carrying its {@code ⟦gN⟧} tokens; {@code null} exactly when
 *     {@code restored} is
 */
record MachineTarget(@Nullable String restored, @Nullable String masked) {

    private static final MachineTarget NONE = new MachineTarget(null, null);

    // A failure of either check leaves the model's words intact, so the draft is a better export than the source;
    // any other failed gate (an English leftover, a refusal, markup, a locked name) says the draft is not a
    // translation.
    private static final Set<CheckName> TOLERATED = Set.of(CheckName.QUOTE_BALANCE, CheckName.SCRIPT_PURITY);

    /**
     * The state before any target has passed every hard gate.
     *
     * @return a machine target carrying neither form
     */
    static MachineTarget none() {
        return NONE;
    }

    /**
     * The target a draft stands on: its own when it passed every hard gate or failed only a tolerated text check,
     * else none.
     *
     * @param outcome the drafted segment
     * @param qa the draft's evaluation
     * @return the draft's restored and masked forms, or none when it did not restore or a hard gate failed
     */
    static MachineTarget of(final DraftOutcome.Drafted outcome, final QaResult qa) {
        return outcome.restoredTarget() != null ? standingOn(outcome.restoredTarget(), outcome.maskedForm(), qa) : NONE;
    }

    /**
     * The target a restored candidate stands on.
     *
     * @param restored the candidate restored through the placeholder gate
     * @param masked its masked form
     * @param qa the candidate's evaluation
     * @return the candidate's forms, or none when a hard gate other than a tolerated text check failed
     */
    static MachineTarget standingOn(final String restored, @Nullable final String masked, final QaResult qa) {
        return qa.hardGates().stream().filter(result -> !result.passed()).allMatch(MachineTarget::isTolerated)
                ? new MachineTarget(restored, masked)
                : NONE;
    }

    private static boolean isTolerated(final CheckResult failed) {
        return TOLERATED.contains(failed.check());
    }
}
