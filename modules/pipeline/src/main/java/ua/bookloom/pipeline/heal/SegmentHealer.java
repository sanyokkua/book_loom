package ua.bookloom.pipeline.heal;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppError;
import ua.bookloom.api.Result;
import ua.bookloom.api.project.QaFinding;
import ua.bookloom.api.project.SegmentPath;
import ua.bookloom.pipeline.judge.JudgeCall;
import ua.bookloom.pipeline.judge.JudgeDeferral;
import ua.bookloom.pipeline.judge.JudgeVerdict;
import ua.bookloom.pipeline.judge.JudgedPair;
import ua.bookloom.pipeline.prompt.ModelCalls;
import ua.bookloom.pipeline.qa.QaResult;

/**
 * Decides one {@link DraftOutcome.Drafted} segment: the acceptance rule first, then up to the dial's repair budget
 * of self-heal rounds — a directed fix for a concrete finding, otherwise reflect then improve, with a polish pass
 * when the improved target is borderline — deciding ACCEPTED or FLAGGED with every finding recorded
 * ({@code specs/quality-gates/spec.md} "Repair a failing segment within the dial's repair budget before flagging
 * it"). A judge that never answered flags the segment, rounds that make no progress stop early, and a round a model
 * call interrupted is continued at that call by the next decision of the same segment. Built fresh per chunk by
 * {@link QualityLoop}; never Guice-constructed, so it keeps the chunk's re-judge deferrals for
 * {@link ChunkDecider#deferrals()} to hand up and each interrupted segment's {@link Resumption}.
 */
@Slf4j
final class SegmentHealer {

    private final JudgeCall judgeCall;
    private final LoopSettings settings;
    private final RoundRunner rounds;
    private final ModelCalls calls;
    private final List<JudgeDeferral> rejudgeDeferrals = new ArrayList<>();
    private final Map<String, Resumption> resumptions = new HashMap<>();

    SegmentHealer(
            final JudgeCall judgeCall,
            final DirectedFix directedFix,
            final ReflectImprove reflectImprove,
            final Polish polish,
            final LoopSettings settings,
            final GateFunction gate,
            final ModelCalls calls) {
        this.judgeCall = Objects.requireNonNull(judgeCall, "judgeCall");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.calls = Objects.requireNonNull(calls, "calls");
        this.rounds = new RoundRunner(
                directedFix, reflectImprove, polish, settings, new RoundEvaluator(gate, settings), calls);
    }

    /**
     * Decides one drafted segment, continuing its rounds where a failed call left them.
     *
     * @param outcome the drafted segment
     * @param initialQa the outcome's already-evaluated hard-gate and soft outcome
     * @param chunkVerdict the chunk's judge verdict, or {@code null} when the judge is off or did not judge this
     *     outcome
     * @return the segment's decision, or the error a model call answered that ends this step
     */
    Result<SegmentOutcome> decide(
            final DraftOutcome.Drafted outcome, final QaResult initialQa, @Nullable final JudgeVerdict chunkVerdict) {
        Objects.requireNonNull(outcome, "outcome");
        Objects.requireNonNull(initialQa, "initialQa");
        final String segmentId = outcome.segment().id();
        final double tau = settings.reviewMode().threshold();
        final Resumption resumption = resumptions.remove(segmentId);
        if (resumption != null) {
            log.info(
                    "Continuing segment={} at round={} rejudgeOnly={}",
                    segmentId,
                    resumption.round(),
                    resumption.evaluated() != null);
            return runRounds(outcome, segmentId, tau, resumption);
        }
        final JudgeVerdict verdict0 = initialQa.hardGatesPass() ? chunkVerdict : null;
        SegmentHealerLogging.logEvaluation(segmentId, 0, initialQa);
        final MachineTarget machine = machineTargetFrom(outcome, initialQa);
        if (verdict0 != null && verdict0.isUnavailable()) {
            return Result.ok(SegmentOutcomes.judgeUnavailable(segmentId, machine, initialQa, null, 0, verdict0));
        }
        final boolean accepted = AcceptanceRule.accepts(initialQa, verdict0, segmentId, tau);
        SegmentHealerLogging.logAcceptanceDecision(segmentId, tau, initialQa, verdict0, accepted);
        if (accepted) {
            return Result.ok(SegmentOutcomes.accepted(segmentId, machine, initialQa, verdict0, 0, SegmentPath.DRAFT));
        }
        final RoundState first = new RoundState(initialQa, verdict0, verdict0, machine, outcome.maskedReply(), null);
        return runRounds(outcome, segmentId, tau, Resumption.first(first));
    }

    /**
     * Flags a segment the run gave up on after its calls kept failing, keeping the latest target that passed every
     * hard gate — the draft's, or the one an interrupted round had reached.
     *
     * @param outcome the drafted segment
     * @param initialQa the outcome's already-evaluated hard-gate and soft outcome
     * @param chunkVerdict the chunk's judge verdict, or {@code null} when none judged this outcome
     * @param reason the error the last attempt answered
     * @return the flagged decision
     */
    SegmentOutcome giveUp(
            final DraftOutcome.Drafted outcome,
            final QaResult initialQa,
            @Nullable final JudgeVerdict chunkVerdict,
            final AppError reason) {
        final String segmentId = outcome.segment().id();
        final Resumption resumption = resumptions.remove(segmentId);
        log.debug("Giving up on segment={} midRound={} code={}", segmentId, resumption != null, reason.code());
        if (resumption == null) {
            final JudgeVerdict verdict0 = initialQa.hardGatesPass() ? chunkVerdict : null;
            return SegmentOutcomes.flagged(
                    segmentId, machineTargetFrom(outcome, initialQa), initialQa, verdict0, 0, reason);
        }
        final RoundState state = resumption.state();
        return SegmentOutcomes.flagged(
                segmentId, state.machine(), state.qa(), state.recordedVerdict(), resumption.round() - 1, reason);
    }

    private static MachineTarget machineTargetFrom(final DraftOutcome.Drafted outcome, final QaResult qa) {
        return outcome.restoredTarget() != null && qa.hardGatesPass()
                ? new MachineTarget(outcome.restoredTarget(), outcome.maskedForm())
                : MachineTarget.none();
    }

    private Result<SegmentOutcome> runRounds(
            final DraftOutcome.Drafted outcome, final String segmentId, final double tau, final Resumption start) {
        RoundState state = start.state();
        final int budget = settings.dial().repairRounds();
        for (int round = start.round(); round <= budget; round++) {
            final RoundOutcome.Evaluated pending = round == start.round() ? start.evaluated() : null;
            final RoundStep step = pending == null
                    ? attemptRound(outcome, segmentId, tau, round, state)
                    : decideEvaluated(outcome, segmentId, tau, round, state, pending);
            switch (step) {
                case RoundStep.Terminal terminal -> {
                    return terminal.result();
                }
                case RoundStep.Interrupted interrupted -> {
                    resumptions.put(segmentId, new Resumption(round, state, interrupted.evaluated()));
                    log.debug("Kept round={} of segment={} for the next decision", round, segmentId);
                    return Result.err(interrupted.error());
                }
                case RoundStep.Continue continuing -> state = continuing.state();
            }
        }
        return Result.ok(
                SegmentOutcomes.flagged(segmentId, state.machine(), state.qa(), state.recordedVerdict(), budget, null));
    }

    private RoundStep attemptRound(
            final DraftOutcome.Drafted outcome,
            final String segmentId,
            final double tau,
            final int round,
            final RoundState state) {
        final List<QaFinding> concreteFindings = SegmentFindings.withCarried(
                SegmentFindings.concrete(state.qa(), state.routingVerdict(), segmentId), state.lastGateFinding());
        SegmentHealerLogging.logRoundChoice(
                segmentId, round, concreteFindings, state.qa(), state.routingVerdict(), tau);
        final RoundOutcome result = rounds.run(outcome, state.rewriteBase(), concreteFindings, tau, round);
        return switch (result) {
            case RoundOutcome.StepError stepError -> new RoundStep.Interrupted(stepError.error(), null);
            case RoundOutcome.FlagNow flagNow ->
                RoundStep.terminal(Result.ok(SegmentOutcomes.flagged(
                        segmentId, state.machine(), state.qa(), state.recordedVerdict(), round, flagNow.error())));
            case RoundOutcome.FlagNowAfterEvaluation flagNowAfter ->
                RoundStep.terminal(
                        Result.ok(flaggedAfterEvaluation(segmentId, state.recordedVerdict(), round, flagNowAfter)));
            case RoundOutcome.Failed failed -> RoundStep.continueWith(carryingFinding(state, failed));
            case RoundOutcome.Evaluated evaluated -> decideEvaluated(outcome, segmentId, tau, round, state, evaluated);
        };
    }

    private static RoundState carryingFinding(final RoundState state, final RoundOutcome.Failed failed) {
        return failed.gateFinding() == null ? state : state.withLastGateFinding(failed.gateFinding());
    }

    private static SegmentOutcome flaggedAfterEvaluation(
            final String segmentId,
            @Nullable final JudgeVerdict recordedVerdict,
            final int round,
            final RoundOutcome.FlagNowAfterEvaluation flagNowAfter) {
        final RoundOutcome.Evaluated evaluated = flagNowAfter.evaluated();
        final MachineTarget machine = new MachineTarget(evaluated.restoredTarget(), evaluated.maskedForm());
        return SegmentOutcomes.flagged(
                segmentId, machine, evaluated.qa(), recordedVerdict, round, flagNowAfter.error());
    }

    private RoundStep decideEvaluated(
            final DraftOutcome.Drafted outcome,
            final String segmentId,
            final double tau,
            final int round,
            final RoundState previous,
            final RoundOutcome.Evaluated evaluated) {
        final QaResult qa = evaluated.qa();
        SegmentHealerLogging.logEvaluation(segmentId, round, qa);
        final MachineTarget machine = qa.hardGatesPass()
                ? new MachineTarget(evaluated.restoredTarget(), evaluated.maskedForm())
                : previous.machine();
        final Round current = new Round(segmentId, tau, round, previous, machine, evaluated);
        return eligibleForRejudge(qa, tau) ? decideWithRejudge(outcome, current) : decideWithoutRejudge(current);
    }

    private RoundStep decideWithoutRejudge(final Round current) {
        final QaResult qa = current.evaluated().qa();
        final String segmentId = current.segmentId();
        final boolean accepted = AcceptanceRule.accepts(qa, null, segmentId, current.tau());
        SegmentHealerLogging.logAcceptanceDecision(segmentId, current.tau(), qa, null, accepted);
        if (accepted) {
            return RoundStep.terminal(Result.ok(SegmentOutcomes.accepted(
                    segmentId, current.machine(), qa, null, current.round(), SegmentPath.REPAIRED)));
        }
        final RoundState previous = current.previous();
        if (RoundProgress.unchangedText(previous, current.evaluated(), segmentId, current.round())) {
            return RoundStep.terminal(Result.ok(SegmentOutcomes.flagged(
                    segmentId, current.machine(), qa, previous.recordedVerdict(), current.round(), null)));
        }
        // Not re-judged: routing resets (this text has no verdict of its own), the recorded verdict persists.
        return RoundStep.continueWith(new RoundState(
                qa,
                null,
                previous.recordedVerdict(),
                current.machine(),
                current.evaluated().maskedCandidate(),
                null));
    }

    private RoundStep decideWithRejudge(final DraftOutcome.Drafted outcome, final Round current) {
        final String segmentId = current.segmentId();
        final Result<JudgeVerdict> rejudge = judgeCall.judge(
                List.of(new JudgedPair(
                        segmentId,
                        outcome.segment().masked(),
                        current.evaluated().maskedForm())),
                settings.frame(),
                settings.glossaryTerms(),
                calls);
        if (rejudge.isErr()) {
            return new RoundStep.Interrupted(Objects.requireNonNull(rejudge.error()), current.evaluated());
        }
        final JudgeVerdict verdict = Objects.requireNonNull(rejudge.data());
        final QaResult qa = current.evaluated().qa();
        if (verdict.isUnavailable()) {
            return RoundStep.terminal(Result.ok(SegmentOutcomes.judgeUnavailable(
                    segmentId, current.machine(), qa, current.previous().recordedVerdict(), current.round(), verdict)));
        }
        keepDeferrals(segmentId, verdict);
        return afterRejudge(current, verdict);
    }

    private RoundStep afterRejudge(final Round current, final JudgeVerdict verdict) {
        final String segmentId = current.segmentId();
        final QaResult qa = current.evaluated().qa();
        final boolean accepted = AcceptanceRule.accepts(qa, verdict, segmentId, current.tau());
        SegmentHealerLogging.logAcceptanceDecision(segmentId, current.tau(), qa, verdict, accepted);
        if (accepted) {
            return RoundStep.terminal(Result.ok(SegmentOutcomes.accepted(
                    segmentId, current.machine(), qa, verdict, current.round(), SegmentPath.REPAIRED)));
        }
        final RoundState previous = current.previous();
        if (RoundProgress.unchangedText(previous, current.evaluated(), segmentId, current.round())
                || RoundProgress.repeatedVerdict(previous, verdict, segmentId, current.round())) {
            return RoundStep.terminal(Result.ok(
                    SegmentOutcomes.flagged(segmentId, current.machine(), qa, verdict, current.round(), null)));
        }
        // Re-judged: both the routing and the recorded verdict become this fresh one.
        return RoundStep.continueWith(new RoundState(
                qa, verdict, verdict, current.machine(), current.evaluated().maskedCandidate(), null));
    }

    /**
     * The deferrals every re-judge of this chunk reported, in the order they came. A round sent again after a pause
     * may repeat one; the caller's commit keys deferrals by segment, reason and text, so a repeat stores nothing.
     */
    List<JudgeDeferral> rejudgeDeferrals() {
        return List.copyOf(rejudgeDeferrals);
    }

    private void keepDeferrals(final String segmentId, final JudgeVerdict verdict) {
        log.debug(
                "Re-judge deferrals segment={} count={}",
                segmentId,
                verdict.deferrals().size());
        rejudgeDeferrals.addAll(verdict.deferrals());
    }

    private boolean eligibleForRejudge(final QaResult qa, final double tau) {
        return settings.dial().judge()
                && qa.hardGatesPass()
                && !qa.failedOutright()
                && qa.confidence() >= tau - AcceptanceRule.ACCEPTANCE_TOLERANCE;
    }

    /**
     * One round's evaluated rewrite and what the decision about it needs.
     *
     * @param segmentId the segment
     * @param tau the review mode's threshold
     * @param round the round, counted from one
     * @param previous the state the round started from
     * @param machine the latest target that passed every hard gate, this rewrite's when it did
     * @param evaluated the round's evaluated rewrite
     */
    private record Round(
            String segmentId,
            double tau,
            int round,
            RoundState previous,
            MachineTarget machine,
            RoundOutcome.Evaluated evaluated) {}
}
