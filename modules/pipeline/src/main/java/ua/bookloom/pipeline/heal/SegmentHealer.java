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
    private final ModelCalls calls;
    private final Map<String, Resumption> resumptions = new HashMap<>();

    SegmentHealer(
            final EditApplier editApplier,
            final DirectedFix directedFix,
            final ReflectImprove reflectImprove,
            final Polish polish,
            final LoopSettings settings,
            final GateFunction gate,
            final ModelCalls calls) {
        this.settings = Objects.requireNonNull(settings, "settings");
        this.calls = Objects.requireNonNull(calls, "calls");
        final RoundEvaluator evaluator = new RoundEvaluator(gate, settings);
        this.rounds = new RoundRunner(directedFix, reflectImprove, polish, settings, evaluator, calls);
        this.resolver = new ReviewResolver(editApplier, directedFix, evaluator, settings, calls);
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
    private static SegmentOutcome keepRejected(final SegmentOutcome decided, final DraftOutcome.Drafted outcome) {
        final SegmentOutcome kept = decided.keepingRejected(outcome.rejectedForm());
        if (kept.rejectedTarget() != null) {
            log.debug("Rejected reply kept for review segment={} status={}", decided.segmentId(), decided.status());
        }
        return kept;
    }

    private Result<SegmentOutcome> decideKept(
            final DraftOutcome.Drafted outcome, final QaResult initialQa, @Nullable final ReviewVerdict verdict) {
        final String segmentId = outcome.segment().id();
        final double tau = settings.reviewMode().threshold();
        final Resumption resumption = resumptions.remove(segmentId);
        if (resumption != null) {
            log.info("Continuing segment={} at round={}", segmentId, resumption.round());
            return runRounds(outcome, segmentId, tau, resumption);
        }
        SegmentHealerLogging.logEvaluation(segmentId, 0, initialQa);
        final MachineTarget machine = MachineTarget.of(outcome, initialQa);
        if (verdict != null && AcceptanceRule.readyForReview(initialQa)) {
            return decideReviewed(outcome, initialQa, machine, verdict);
        }
        final boolean accepted = AcceptanceRule.accepts(initialQa, 0);
        SegmentHealerLogging.logAcceptanceDecision(segmentId, initialQa, 0, accepted);
        if (accepted) {
            return Result.ok(SegmentOutcomes.accepted(segmentId, machine, initialQa, List.of(), 0, SegmentPath.DRAFT));
        }
        final RoundState first = new RoundState(initialQa, machine, outcome.maskedReply(), null);
        return runRounds(outcome, segmentId, tau, Resumption.first(first));
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
        return resolver.resolve(outcome, initialQa, item).map(resolution -> decidedBy(segmentId, resolution));
    }

    private static SegmentOutcome decidedBy(final String segmentId, final Resolution resolution) {
        final boolean accepted = AcceptanceRule.accepts(resolution.qa(), resolution.verifiedBlockersLeft());
        SegmentHealerLogging.logAcceptanceDecision(
                segmentId, resolution.qa(), resolution.verifiedBlockersLeft(), accepted);
        final SegmentPath path = resolution.rounds() > 0 ? SegmentPath.REPAIRED : SegmentPath.DRAFT;
        return accepted
                ? SegmentOutcomes.accepted(
                        segmentId,
                        resolution.machine(),
                        resolution.qa(),
                        resolution.findings(),
                        resolution.rounds(),
                        path)
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
        final RoundState state = resumption.state();
        return keepRejected(
                SegmentOutcomes.flagged(
                        segmentId, state.machine(), state.qa(), List.of(), resumption.round() - 1, reason),
                outcome);
    }

    private Result<SegmentOutcome> runRounds(
            final DraftOutcome.Drafted outcome, final String segmentId, final double tau, final Resumption start) {
        RoundState state = start.state();
        final int budget = settings.dial().repairRounds();
        for (int round = start.round(); round <= budget; round++) {
            switch (attemptRound(outcome, segmentId, tau, round, state)) {
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
            final DraftOutcome.Drafted outcome,
            final String segmentId,
            final double tau,
            final int round,
            final RoundState state) {
        final List<QaFinding> concreteFindings =
                SegmentFindings.withCarried(SegmentFindings.concrete(state.qa()), state.lastGateFinding());
        SegmentHealerLogging.logRoundChoice(segmentId, round, concreteFindings, state.qa());
        announceRound(segmentId, round, concreteFindings);
        final RoundOutcome result = rounds.run(outcome, state.rewriteBase(), concreteFindings, tau, round);
        return switch (result) {
            case RoundOutcome.StepError stepError -> new RoundStep.Interrupted(stepError.error());
            case RoundOutcome.FlagNow flagNow ->
                RoundStep.terminal(Result.ok(SegmentOutcomes.flagged(
                        segmentId, state.machine(), state.qa(), List.of(), round, flagNow.error())));
            case RoundOutcome.FlagNowAfterEvaluation flagNowAfter ->
                RoundStep.terminal(Result.ok(flaggedAfterEvaluation(segmentId, round, flagNowAfter)));
            case RoundOutcome.Failed failed -> RoundStep.continueWith(carryingFinding(state, failed));
            case RoundOutcome.Evaluated evaluated -> decideEvaluated(segmentId, round, state, evaluated);
        };
    }

    private void announceRound(final String segmentId, final int round, final List<QaFinding> findings) {
        final String blocking = findings.isEmpty() ? null : findings.getFirst().kind();
        calls.announce(new RoundStarted(segmentId, round, settings.dial().repairRounds(), null, blocking));
    }

    private static RoundState carryingFinding(final RoundState state, final RoundOutcome.Failed failed) {
        return failed.gateFinding() == null ? state : state.withLastGateFinding(failed.gateFinding());
    }

    private static SegmentOutcome flaggedAfterEvaluation(
            final String segmentId, final int round, final RoundOutcome.FlagNowAfterEvaluation flagNowAfter) {
        final RoundOutcome.Evaluated evaluated = flagNowAfter.evaluated();
        final MachineTarget machine = new MachineTarget(evaluated.restoredTarget(), evaluated.maskedForm());
        return SegmentOutcomes.flagged(segmentId, machine, evaluated.qa(), List.of(), round, flagNowAfter.error());
    }

    // A repaired target is decided by the checks alone: the reviewer read the draft, not the repair.
    private RoundStep decideEvaluated(
            final String segmentId,
            final int round,
            final RoundState previous,
            final RoundOutcome.Evaluated evaluated) {
        final QaResult qa = evaluated.qa();
        SegmentHealerLogging.logEvaluation(segmentId, round, qa);
        final MachineTarget machine = qa.hardGatesPass()
                ? new MachineTarget(evaluated.restoredTarget(), evaluated.maskedForm())
                : previous.machine();
        final boolean accepted = AcceptanceRule.accepts(qa, 0);
        SegmentHealerLogging.logAcceptanceDecision(segmentId, qa, 0, accepted);
        if (accepted) {
            return RoundStep.terminal(Result.ok(
                    SegmentOutcomes.accepted(segmentId, machine, qa, List.of(), round, SegmentPath.REPAIRED)));
        }
        if (RoundProgress.unchangedText(previous, evaluated, segmentId, round)) {
            return RoundStep.terminal(
                    Result.ok(SegmentOutcomes.flagged(segmentId, machine, qa, List.of(), round, null)));
        }
        return RoundStep.continueWith(new RoundState(qa, machine, evaluated.maskedCandidate(), null));
    }
}
