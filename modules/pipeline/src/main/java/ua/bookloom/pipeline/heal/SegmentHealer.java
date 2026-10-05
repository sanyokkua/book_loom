package ua.bookloom.pipeline.heal;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppError;
import ua.bookloom.api.Result;
import ua.bookloom.api.pipeline.RoundStarted;
import ua.bookloom.api.project.QaFinding;
import ua.bookloom.api.project.SegmentPath;
import ua.bookloom.pipeline.prompt.ModelCalls;
import ua.bookloom.pipeline.qa.QaResult;
import ua.bookloom.pipeline.reviewer.EditApplier;
import ua.bookloom.pipeline.reviewer.ReviewItem;
import ua.bookloom.pipeline.reviewer.ReviewVerdict;

/**
 * Decides one {@link DraftOutcome.Drafted} segment. A draft that passes the checks goes to the reviewer's answer for it
 * ({@link ReviewResolver}: edits verified and applied, a refused edit fixed once, a rewrite taken only when it passes);
 * a draft a check refuses goes through up to the dial's repair budget of directed-fix rounds, and a repaired target is
 * accepted by the checks alone ({@code specs/quality-gates/spec.md} "Repair a failing segment within the dial's repair
 * budget before flagging it"). A reviewer that never answered flags the segment, rounds that make no progress stop
 * early, and a round a model call interrupted is continued at that call by the next decision of the same segment.
 * Built fresh per chunk by {@link QualityLoop}; never Guice-constructed, so it keeps each interrupted segment's
 * {@link Resumption}.
 */
@Slf4j
final class SegmentHealer {

    private final LoopSettings settings;
    private final RoundRunner rounds;
    private final ReviewResolver resolver;
    private final GenderFix genderFix;
    private final ModelCalls calls;
    private final Map<String, Resumption> resumptions = new HashMap<>();

    SegmentHealer(
            final EditApplier editApplier,
            final DirectedFix directedFix,
            final LoopSettings settings,
            final GateFunction gate,
            final ModelCalls calls) {
        this.settings = Objects.requireNonNull(settings, "settings");
        this.calls = Objects.requireNonNull(calls, "calls");
        final RoundEvaluator evaluator = new RoundEvaluator(gate, settings);
        this.rounds = new RoundRunner(directedFix, settings, evaluator, calls);
        this.resolver = new ReviewResolver(editApplier, directedFix, evaluator, settings, calls);
        this.genderFix = new GenderFix(directedFix, evaluator, settings, calls);
    }

    /**
     * Decides one drafted segment, continuing its rounds where a failed call left them.
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
        return decideKept(outcome, initialQa, verdict).map(decided -> keepRejected(decided, outcome));
    }

    // A segment no target passed for keeps the model's refused reply, so review shows its words instead of the source.
    // A draft the placeholder gate restored but a text check blocked has no refused form of its own: its restored text
    // is the best the model wrote, and must not be lost with the failed check.
    private static SegmentOutcome keepRejected(final SegmentOutcome decided, final DraftOutcome.Drafted outcome) {
        final String rejected = outcome.rejectedForm() != null ? outcome.rejectedForm() : outcome.maskedForm();
        final SegmentOutcome kept = decided.keepingRejected(rejected);
        if (kept.rejectedTarget() != null) {
            log.debug("Rejected reply kept for review segment={} status={}", decided.segmentId(), decided.status());
        }
        return kept;
    }

    private Result<SegmentOutcome> decideKept(
            final DraftOutcome.Drafted outcome, final QaResult initialQa, @Nullable final ReviewVerdict verdict) {
        final String segmentId = outcome.segment().id();
        final Resumption resumption = resumptions.remove(segmentId);
        if (resumption != null) {
            log.info("Continuing segment={} at round={}", segmentId, resumption.round());
            return runRounds(outcome, segmentId, resumption);
        }
        SegmentHealerLogging.logEvaluation(segmentId, 0, initialQa);
        final MachineTarget machine = MachineTarget.of(outcome, initialQa);
        if (verdict != null && AcceptanceRule.readyForReview(initialQa)) {
            return decideReviewed(outcome, initialQa, machine, verdict);
        }
        final boolean accepted = AcceptanceRule.accepts(initialQa, 0);
        SegmentHealerLogging.logAcceptanceDecision(segmentId, initialQa, 0, accepted);
        if (accepted) {
            return Result.ok(genderFix.settle(
                    outcome,
                    new GenderFix.Accepted(
                            machine, initialQa, outcome.maskedReply(), List.of(), 0, SegmentPath.DRAFT)));
        }
        final BestCandidate first = new BestCandidate(initialQa, machine, outcome.maskedReply(), null);
        return runRounds(outcome, segmentId, Resumption.first(first));
    }

    // The reviewer read this pair: a reply it did not give is flagged, anything else is verified in code.
    private Result<SegmentOutcome> decideReviewed(
            final DraftOutcome.Drafted outcome,
            final QaResult initialQa,
            final MachineTarget machine,
            final ReviewVerdict verdict) {
        final String segmentId = outcome.segment().id();
        if (verdict.isUnavailable() || !verdict.readable()) {
            return Result.ok(SegmentOutcomes.reviewerUnavailable(
                    segmentId, machine, initialQa, 0, verdict.unavailableBecause()));
        }
        final ReviewItem item = verdict.itemFor(segmentId).orElseGet(() -> ReviewItem.ok(segmentId));
        return resolver.resolve(outcome, initialQa, item).map(resolution -> decidedBy(outcome, resolution));
    }

    private SegmentOutcome decidedBy(final DraftOutcome.Drafted outcome, final Resolution resolution) {
        final String segmentId = outcome.segment().id();
        final boolean accepted = AcceptanceRule.accepts(resolution.qa(), resolution.verifiedBlockersLeft());
        SegmentHealerLogging.logAcceptanceDecision(
                segmentId, resolution.qa(), resolution.verifiedBlockersLeft(), accepted);
        final SegmentPath path = resolution.rounds() > 0 ? SegmentPath.REPAIRED : SegmentPath.DRAFT;
        return accepted
                ? genderFix.settle(
                        outcome,
                        new GenderFix.Accepted(
                                resolution.machine(),
                                resolution.qa(),
                                resolution.maskedText(),
                                resolution.findings(),
                                resolution.rounds(),
                                path))
                : SegmentOutcomes.flagged(
                        segmentId,
                        resolution.machine(),
                        resolution.qa(),
                        resolution.findings(),
                        resolution.rounds(),
                        resolution.reason());
    }

    /**
     * Flags a segment the run gave up on after its calls kept failing, keeping the latest target that passed every
     * hard gate — the draft's, or the one an interrupted round had reached.
     *
     * @param outcome the drafted segment
     * @param initialQa the outcome's already-evaluated hard-gate and soft outcome
     * @param reason the error the last attempt answered
     * @return the flagged decision
     */
    SegmentOutcome giveUp(final DraftOutcome.Drafted outcome, final QaResult initialQa, final AppError reason) {
        final String segmentId = outcome.segment().id();
        final Resumption resumption = resumptions.remove(segmentId);
        log.debug("Giving up on segment={} midRound={} code={}", segmentId, resumption != null, reason.code());
        if (resumption == null) {
            return keepRejected(
                    SegmentOutcomes.flagged(
                            segmentId, MachineTarget.of(outcome, initialQa), initialQa, List.of(), 0, reason),
                    outcome);
        }
        final BestCandidate state = resumption.state();
        return keepRejected(
                SegmentOutcomes.flagged(
                        segmentId, state.machine(), state.qa(), List.of(), resumption.round() - 1, reason),
                outcome);
    }

    private Result<SegmentOutcome> runRounds(
            final DraftOutcome.Drafted outcome, final String segmentId, final Resumption start) {
        BestCandidate state = start.state();
        final int budget = settings.dial().repairRounds();
        for (int round = start.round(); round <= budget; round++) {
            switch (attemptRound(outcome, segmentId, round, state)) {
                case RoundStep.Terminal terminal -> {
                    return terminal.result();
                }
                case RoundStep.Interrupted interrupted -> {
                    resumptions.put(segmentId, new Resumption(round, state));
                    log.debug("Kept round={} of segment={} for the next decision", round, segmentId);
                    return Result.err(interrupted.error());
                }
                case RoundStep.Continue continuing -> state = continuing.state();
            }
        }
        return Result.ok(SegmentOutcomes.flagged(segmentId, state.machine(), state.qa(), List.of(), budget, null));
    }

    private RoundStep attemptRound(
            final DraftOutcome.Drafted outcome, final String segmentId, final int round, final BestCandidate best) {
        final List<QaFinding> findings =
                SegmentFindings.withCarried(SegmentFindings.concrete(best.qa()), best.lastGateFinding());
        SegmentHealerLogging.logRoundChoice(segmentId, round, findings, best.qa());
        if (findings.isEmpty()) {
            return RoundStep.terminal(Result.ok(
                    SegmentOutcomes.flagged(segmentId, best.machine(), best.qa(), List.of(), round - 1, null)));
        }
        announceRound(segmentId, round, findings);
        return switch (rounds.run(outcome, best.rewriteBase(), findings, round)) {
            case RoundOutcome.StepError stepError -> new RoundStep.Interrupted(stepError.error());
            case RoundOutcome.FlagNow flagNow ->
                RoundStep.terminal(Result.ok(SegmentOutcomes.flagged(
                        segmentId, best.machine(), best.qa(), List.of(), round, flagNow.error())));
            case RoundOutcome.Failed failed -> RoundStep.continueWith(carryingFinding(best, failed));
            case RoundOutcome.Evaluated evaluated -> decideEvaluated(outcome, round, best, evaluated);
        };
    }

    private void announceRound(final String segmentId, final int round, final List<QaFinding> findings) {
        calls.announce(new RoundStarted(
                segmentId,
                round,
                settings.dial().repairRounds(),
                null,
                findings.getFirst().kind()));
    }

    private static BestCandidate carryingFinding(final BestCandidate best, final RoundOutcome.Failed failed) {
        return failed.gateFinding() == null ? best : best.withLastGateFinding(failed.gateFinding());
    }

    // A repaired target is decided by the checks alone (the reviewer read the draft, not the repair), and it replaces
    // the best candidate only when it has fewer blockers: a step that does not improve is discarded and ends the path.
    private RoundStep decideEvaluated(
            final DraftOutcome.Drafted outcome,
            final int round,
            final BestCandidate best,
            final RoundOutcome.Evaluated evaluated) {
        final String segmentId = outcome.segment().id();
        final QaResult qa = evaluated.qa();
        SegmentHealerLogging.logEvaluation(segmentId, round, qa);
        final boolean accepted = AcceptanceRule.accepts(qa, 0);
        SegmentHealerLogging.logAcceptanceDecision(segmentId, qa, 0, accepted);
        if (accepted) {
            return RoundStep.terminal(Result.ok(genderFix.settle(
                    outcome,
                    new GenderFix.Accepted(
                            machineOf(best, evaluated),
                            qa,
                            evaluated.maskedCandidate(),
                            List.of(),
                            round,
                            SegmentPath.REPAIRED))));
        }
        if (!RoundProgress.improves(best, qa, segmentId, round)) {
            return RoundStep.terminal(
                    Result.ok(SegmentOutcomes.flagged(segmentId, best.machine(), best.qa(), List.of(), round, null)));
        }
        return RoundStep.continueWith(
                new BestCandidate(qa, machineOf(best, evaluated), evaluated.maskedCandidate(), null));
    }

    private static MachineTarget machineOf(final BestCandidate best, final RoundOutcome.Evaluated evaluated) {
        return evaluated.qa().hardGatesPass()
                ? new MachineTarget(evaluated.restoredTarget(), evaluated.maskedForm())
                : best.machine();
    }
}
