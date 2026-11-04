package ua.bookloom.pipeline.heal;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppError;
import ua.bookloom.api.Result;
import ua.bookloom.api.project.SegmentPath;
import ua.bookloom.pipeline.prompt.ModelCalls;
import ua.bookloom.pipeline.qa.QaResult;
import ua.bookloom.pipeline.reviewer.EditApplier;
import ua.bookloom.pipeline.reviewer.ReviewItem;
import ua.bookloom.pipeline.reviewer.ReviewVerdict;

/**
 * Decides one {@link DraftOutcome.Drafted} segment. A draft that passes the checks goes to the reviewer's answer for it
 * ({@link ReviewResolver}: edits verified and applied, a refused edit fixed once, a rewrite taken only when it passes);
 * a reviewer that never answered flags the segment. The text that stays then gets the {@link OneRepair} when it still
 * holds a BLOCK or REPAIR finding, and the acceptance rule reads the findings left on the text that stays
 * ({@code specs/quality-gates/spec.md} "Read a segment's outcome from the findings left after the one repair"). A
 * decision a model call interrupted is made again from the draft kept in memory; a repair that was cut off is made
 * again from the text it was given ({@code specs/resume/spec.md} "Make a cut-off repair again from the draft kept in
 * memory"). Built fresh per chunk by
 * {@link QualityLoop}; never Guice-constructed.
 */
@Slf4j
final class SegmentHealer {

    private final ReviewResolver resolver;
    private final OneRepair oneRepair;
    // The text a segment's repair was cut off on, kept until the repair answers, so a re-decision after a pause or a
    // pausing error makes only the repair again, not the reviewer's fix of a refused edit before it. A chunk is
    // decided on one thread.
    private final Map<String, Resolution> pendingRepair = new HashMap<>();

    SegmentHealer(
            final EditApplier editApplier,
            final DirectedFix directedFix,
            final LoopSettings settings,
            final GateFunction gate,
            final ModelCalls calls) {
        Objects.requireNonNull(settings, "settings");
        Objects.requireNonNull(calls, "calls");
        final ReplyEvaluator evaluator = new ReplyEvaluator(gate, settings);
        this.resolver = new ReviewResolver(editApplier, directedFix, evaluator, settings, calls);
        this.oneRepair = new OneRepair(evaluator, settings, calls);
    }

    /**
     * Decides one drafted segment.
     *
     * @param outcome the drafted segment
     * @param initialQa the outcome's already-evaluated hard-gate and soft outcome
     * @param verdict the chunk's reviewer verdict, or {@code null} when the reviewer is off or read no pair of the
     *     chunk
     * @return the segment's decision, or the error a model call answered that ends this step
     */
    Result<SegmentOutcome> decide(
            final DraftOutcome.Drafted outcome, final QaResult initialQa, @Nullable final ReviewVerdict verdict) {
        Objects.requireNonNull(outcome, "outcome");
        Objects.requireNonNull(initialQa, "initialQa");
        final String segmentId = outcome.segment().id();
        final Resolution pending = pendingRepair.get(segmentId);
        if (pending != null) {
            log.debug("Repair made again segment={} from the text it was cut off on", segmentId);
            return settle(outcome, pending);
        }
        SegmentHealerLogging.logEvaluation(segmentId, 0, initialQa);
        final MachineTarget machine = MachineTarget.of(outcome, initialQa);
        if (verdict != null && AcceptanceRule.readyForReview(initialQa)) {
            if (verdict.isUnavailable() || !verdict.readable()) {
                return Result.ok(keepRejected(
                        SegmentOutcomes.reviewerUnavailable(
                                segmentId, machine, initialQa, verdict.unavailableBecause()),
                        outcome,
                        null));
            }
            final ReviewItem item = verdict.itemFor(segmentId).orElseGet(() -> ReviewItem.ok(segmentId));
            return resolver.resolve(outcome, initialQa, item).flatMap(resolution -> settle(outcome, resolution));
        }
        log.debug("Deciding segment={} without the reviewer", segmentId);
        return settle(outcome, new Resolution(machine, initialQa, List.of(), 0, 0, null, outcome.maskedReply()));
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
        pendingRepair.remove(outcome.segment().id());
        return keepRejected(
                SegmentOutcomes.flagged(
                        outcome.segment().id(),
                        MachineTarget.of(outcome, initialQa),
                        initialQa,
                        List.of(),
                        false,
                        reason),
                outcome,
                null);
    }

    private Result<SegmentOutcome> settle(final DraftOutcome.Drafted outcome, final Resolution standing) {
        final String segmentId = outcome.segment().id();
        pendingRepair.put(segmentId, standing);
        final Result<OneRepair.Repaired> repaired = oneRepair.repair(outcome, standing);
        if (repaired.isOk()) {
            pendingRepair.remove(segmentId);
        } else {
            log.debug("Repair of segment={} kept for the next decision", segmentId);
        }
        return repaired.map(done -> decided(outcome, done));
    }

    private static SegmentOutcome decided(final DraftOutcome.Drafted outcome, final OneRepair.Repaired repaired) {
        final String segmentId = outcome.segment().id();
        final Resolution end = repaired.standing();
        final boolean accepted = AcceptanceRule.accepts(end.qa(), end.verifiedBlockersLeft());
        SegmentHealerLogging.logAcceptanceDecision(segmentId, end.qa(), end.verifiedBlockersLeft(), accepted);
        if (accepted) {
            // The path says whose text stays: the repair's (or the reviewer's change), else the draft's.
            final SegmentPath path = end.rounds() > 0 ? SegmentPath.REPAIRED : SegmentPath.DRAFT;
            return SegmentOutcomes.accepted(segmentId, end.machine(), end.qa(), end.findings(), path);
        }
        log.warn("Segment flagged after its one repair segment={} reason={}", segmentId, repaired.reason());
        return keepRejected(
                SegmentOutcomes.flagged(
                        segmentId, end.machine(), end.qa(), end.findings(), repaired.repaired(), end.reason()),
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
