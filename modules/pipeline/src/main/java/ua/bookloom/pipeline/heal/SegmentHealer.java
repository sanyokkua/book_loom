package ua.bookloom.pipeline.heal;

import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppError;
import ua.bookloom.api.Result;
import ua.bookloom.api.project.SegmentPath;
import ua.bookloom.pipeline.prompt.ModelCalls;
import ua.bookloom.pipeline.qa.QaResult;

/**
 * Decides one {@link DraftOutcome.Drafted} segment: the {@link OneRepair} when the draft still holds a BLOCK or REPAIR
 * finding, then the acceptance rule reads the findings left on the text that stays
 * ({@code specs/quality-gates/spec.md} "Read a segment's outcome from the findings left after the one repair"). A
 * decision a model call interrupted is made again from the draft kept in memory, so a repair that was cut off is made
 * again from the text it was given ({@code specs/resume/spec.md} "Make a cut-off repair again from the draft kept in
 * memory"). Built fresh per chunk by {@link QualityLoop}; never Guice-constructed.
 */
@Slf4j
final class SegmentHealer {

    private final OneRepair oneRepair;

    SegmentHealer(final LoopSettings settings, final GateFunction gate, final ModelCalls calls) {
        Objects.requireNonNull(settings, "settings");
        Objects.requireNonNull(calls, "calls");
        this.oneRepair = new OneRepair(new ReplyEvaluator(gate, settings), settings, calls);
    }

    /**
     * Decides one drafted segment.
     *
     * @param outcome the drafted segment
     * @param initialQa the outcome's already-evaluated hard-gate and soft outcome
     * @return the segment's decision, or the error a model call answered that ends this step
     */
    Result<SegmentOutcome> decide(final DraftOutcome.Drafted outcome, final QaResult initialQa) {
        Objects.requireNonNull(outcome, "outcome");
        Objects.requireNonNull(initialQa, "initialQa");
        final String segmentId = outcome.segment().id();
        SegmentHealerLogging.logEvaluation(segmentId, 0, initialQa);
        final Resolution standing =
                new Resolution(MachineTarget.of(outcome, initialQa), initialQa, 0, null, outcome.maskedReply());
        return oneRepair.repair(outcome, standing).map(done -> decided(outcome, done));
    }

    /**
     * Flags a segment the run gave up on after its calls kept failing, keeping the draft's target when it passed every
     * hard gate.
     *
     * @param outcome the drafted segment
     * @param initialQa the outcome's already-evaluated hard-gate and soft outcome
     * @param reason the error the last attempt answered
     * @return the flagged decision
     */
    SegmentOutcome giveUp(final DraftOutcome.Drafted outcome, final QaResult initialQa, final AppError reason) {
        log.debug("Giving up on segment={} code={}", outcome.segment().id(), reason.code());
        return keepRejected(
                SegmentOutcomes.flagged(
                        outcome.segment().id(), MachineTarget.of(outcome, initialQa), initialQa, false, reason),
                outcome,
                null);
    }

    private static SegmentOutcome decided(final DraftOutcome.Drafted outcome, final OneRepair.Repaired repaired) {
        final String segmentId = outcome.segment().id();
        final Resolution end = repaired.standing();
        final boolean accepted = AcceptanceRule.accepts(end.qa());
        SegmentHealerLogging.logAcceptanceDecision(segmentId, end.qa(), accepted);
        if (accepted) {
            // The path says whose text stays: the repair's, else the draft's.
            final SegmentPath path = end.rounds() > 0 ? SegmentPath.REPAIRED : SegmentPath.DRAFT;
            return SegmentOutcomes.accepted(segmentId, end.machine(), end.qa(), path);
        }
        log.warn("Segment flagged after its one repair segment={} reason={}", segmentId, repaired.reason());
        return keepRejected(
                SegmentOutcomes.flagged(segmentId, end.machine(), end.qa(), repaired.repaired(), end.reason()),
                outcome,
                repaired.rejected());
    }

    // A segment no target passed for keeps the model's last refused reply, so review shows its words instead of the
    // source: the repair's, else the draft's. A draft the placeholder gate restored but a text check blocked has no
    // refused form of its own: its restored text is the best the model wrote, and must not be lost with the check.
    private static SegmentOutcome keepRejected(
            final SegmentOutcome decided, final DraftOutcome.Drafted outcome, @Nullable final String repairRejected) {
        final String draftRejected = outcome.rejectedForm() != null ? outcome.rejectedForm() : outcome.maskedForm();
        final SegmentOutcome kept = decided.keepingRejected(repairRejected != null ? repairRejected : draftRejected);
        if (kept.rejectedTarget() != null) {
            log.debug("Rejected reply kept for review segment={} status={}", decided.segmentId(), decided.status());
        }
        return kept;
    }
}
