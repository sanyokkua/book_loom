package ua.bookloom.pipeline.heal;

import java.util.List;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.project.QaFinding;
import ua.bookloom.api.project.SegmentPath;
import ua.bookloom.pipeline.WhitespaceRestoration;
import ua.bookloom.pipeline.judge.JudgeCall;
import ua.bookloom.pipeline.judge.JudgeVerdict;
import ua.bookloom.pipeline.judge.JudgedPair;
import ua.bookloom.pipeline.prompt.ModelCalls;
import ua.bookloom.pipeline.qa.QaResult;

/**
 * Decides one {@link DraftOutcome.Drafted} segment: the acceptance rule first, then up to the dial's repair budget
 * of self-heal rounds — a directed fix for a concrete finding, otherwise reflect then improve, with a polish pass
 * when the improved target is borderline — deciding ACCEPTED or FLAGGED with every finding recorded
 * ({@code specs/quality-gates/spec.md} "Repair a failing segment within the dial's repair budget before flagging
 * it"). Built fresh per chunk by {@link QualityLoop}; never Guice-constructed.
 */
@Slf4j
final class SegmentHealer {

    private final JudgeCall judgeCall;
    private final DirectedFix directedFix;
    private final ReflectImprove reflectImprove;
    private final Polish polish;
    private final LoopSettings settings;
    private final GateFunction gate;
    private final ModelCalls calls;

    SegmentHealer(
            final JudgeCall judgeCall,
            final DirectedFix directedFix,
            final ReflectImprove reflectImprove,
            final Polish polish,
            final LoopSettings settings,
            final GateFunction gate,
            final ModelCalls calls) {
        this.judgeCall = Objects.requireNonNull(judgeCall, "judgeCall");
        this.directedFix = Objects.requireNonNull(directedFix, "directedFix");
        this.reflectImprove = Objects.requireNonNull(reflectImprove, "reflectImprove");
        this.polish = Objects.requireNonNull(polish, "polish");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.gate = Objects.requireNonNull(gate, "gate");
        this.calls = Objects.requireNonNull(calls, "calls");
    }

    /**
     * Decides one drafted segment.
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
        final JudgeVerdict verdict0 = initialQa.hardGatesPass() ? chunkVerdict : null;
        SegmentHealerLogging.logEvaluation(segmentId, 0, initialQa);
        final MachineTarget machine = machineTargetFrom(outcome, initialQa);
        final boolean accepted = AcceptanceRule.accepts(initialQa, verdict0, segmentId, tau);
        SegmentHealerLogging.logAcceptanceDecision(segmentId, tau, initialQa, verdict0, accepted);
        if (accepted) {
            return Result.ok(buildAccepted(segmentId, machine, initialQa, verdict0, 0, SegmentPath.DRAFT));
        }
        return runRounds(outcome, segmentId, tau, initialQa, verdict0, machine);
    }

    private static MachineTarget machineTargetFrom(final DraftOutcome.Drafted outcome, final QaResult qa) {
        return outcome.restoredTarget() != null && qa.hardGatesPass()
                ? new MachineTarget(outcome.restoredTarget(), outcome.maskedReply())
                : MachineTarget.none();
    }

    private Result<SegmentOutcome> runRounds(
            final DraftOutcome.Drafted outcome,
            final String segmentId,
            final double tau,
            final QaResult qa0,
            @Nullable final JudgeVerdict verdict0,
            final MachineTarget initialMachine) {
        RoundState state = new RoundState(qa0, verdict0, verdict0, initialMachine, outcome.maskedReply());
        final int budget = settings.dial().repairRounds();
        for (int round = 1; round <= budget; round++) {
            final RoundStep step = attemptRound(outcome, segmentId, tau, round, state);
            switch (step) {
                case RoundStep.Terminal terminal -> {
                    return terminal.result();
                }
                case RoundStep.Continue continuing -> state = continuing.state();
            }
        }
        return Result.ok(buildFlagged(segmentId, state.machine(), state.qa(), state.recordedVerdict(), budget, null));
    }

    private RoundStep attemptRound(
            final DraftOutcome.Drafted outcome,
            final String segmentId,
            final double tau,
            final int round,
            final RoundState state) {
        final List<QaFinding> concreteFindings =
                SegmentFindings.concrete(state.qa(), state.routingVerdict(), segmentId);
        SegmentHealerLogging.logRoundChoice(
                segmentId, round, concreteFindings, state.qa(), state.routingVerdict(), tau);
        final RoundOutcome result = concreteFindings.isEmpty()
                ? runReflectImproveRound(outcome, state.rewriteBase(), tau)
                : runDirectedFixRound(outcome, state.rewriteBase(), concreteFindings);
        return switch (result) {
            case RoundOutcome.StepError stepError -> RoundStep.terminal(Result.err(stepError.error()));
            case RoundOutcome.FlagNow flagNow ->
                RoundStep.terminal(Result.ok(buildFlagged(
                        segmentId, state.machine(), state.qa(), state.recordedVerdict(), round, flagNow.error())));
            case RoundOutcome.FlagNowAfterEvaluation flagNowAfter ->
                RoundStep.terminal(
                        Result.ok(flaggedAfterEvaluation(segmentId, state.recordedVerdict(), round, flagNowAfter)));
            case RoundOutcome.Failed ignored -> RoundStep.continueWith(state);
            case RoundOutcome.Evaluated evaluated -> decideEvaluated(outcome, segmentId, tau, round, state, evaluated);
        };
    }

    private SegmentOutcome flaggedAfterEvaluation(
            final String segmentId,
            @Nullable final JudgeVerdict recordedVerdict,
            final int round,
            final RoundOutcome.FlagNowAfterEvaluation flagNowAfter) {
        final RoundOutcome.Evaluated evaluated = flagNowAfter.evaluated();
        final MachineTarget machine = new MachineTarget(evaluated.restoredTarget(), evaluated.maskedCandidate());
        return buildFlagged(segmentId, machine, evaluated.qa(), recordedVerdict, round, flagNowAfter.error());
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
                ? new MachineTarget(evaluated.restoredTarget(), evaluated.maskedCandidate())
                : previous.machine();
        return eligibleForRejudge(qa, tau)
                ? decideWithRejudge(outcome, segmentId, tau, round, machine, evaluated)
                : decideWithoutRejudge(segmentId, tau, round, previous, machine, evaluated);
    }

    private RoundStep decideWithoutRejudge(
            final String segmentId,
            final double tau,
            final int round,
            final RoundState previous,
            final MachineTarget machine,
            final RoundOutcome.Evaluated evaluated) {
        final QaResult qa = evaluated.qa();
        final boolean accepted = AcceptanceRule.accepts(qa, null, segmentId, tau);
        SegmentHealerLogging.logAcceptanceDecision(segmentId, tau, qa, null, accepted);
        if (accepted) {
            return RoundStep.terminal(
                    Result.ok(buildAccepted(segmentId, machine, qa, null, round, SegmentPath.REPAIRED)));
        }
        // Not re-judged: routing resets (this text has no verdict of its own), the recorded verdict persists.
        return RoundStep.continueWith(
                new RoundState(qa, null, previous.recordedVerdict(), machine, evaluated.maskedCandidate()));
    }

    private RoundStep decideWithRejudge(
            final DraftOutcome.Drafted outcome,
            final String segmentId,
            final double tau,
            final int round,
            final MachineTarget machine,
            final RoundOutcome.Evaluated evaluated) {
        final QaResult qa = evaluated.qa();
        final Result<JudgeVerdict> rejudge = judgeCall.judge(
                List.of(new JudgedPair(segmentId, outcome.maskedSource(), evaluated.maskedCandidate())),
                settings.frame(),
                settings.glossaryTerms(),
                calls);
        if (rejudge.isErr()) {
            return RoundStep.terminal(Result.err(Objects.requireNonNull(rejudge.error())));
        }
        final JudgeVerdict verdict = rejudge.data();
        final boolean accepted = AcceptanceRule.accepts(qa, verdict, segmentId, tau);
        SegmentHealerLogging.logAcceptanceDecision(segmentId, tau, qa, verdict, accepted);
        if (accepted) {
            return RoundStep.terminal(
                    Result.ok(buildAccepted(segmentId, machine, qa, verdict, round, SegmentPath.REPAIRED)));
        }
        // Re-judged: both the routing and the recorded verdict become this fresh one.
        return RoundStep.continueWith(new RoundState(qa, verdict, verdict, machine, evaluated.maskedCandidate()));
    }

    private boolean eligibleForRejudge(final QaResult qa, final double tau) {
        return settings.dial().judge()
                && qa.hardGatesPass()
                && !qa.failedOutright()
                && qa.confidence() >= tau - AcceptanceRule.ACCEPTANCE_TOLERANCE;
    }

    private RoundOutcome runDirectedFixRound(
            final DraftOutcome.Drafted outcome, final String rewriteBase, final List<QaFinding> findings) {
        final Result<RepairReply> reply = directedFix.fix(
                outcome.segment(), settings.frame(), outcome.maskedSource(), rewriteBase, findings, calls);
        return classify(outcome, reply);
    }

    private RoundOutcome runReflectImproveRound(
            final DraftOutcome.Drafted outcome, final String rewriteBase, final double tau) {
        final Result<List<String>> issues =
                reflectImprove.reflect(outcome.segment(), settings.frame(), outcome.maskedSource(), rewriteBase, calls);
        if (issues.isErr()) {
            return new RoundOutcome.StepError(Objects.requireNonNull(issues.error()));
        }
        final Result<RepairReply> improveReply = reflectImprove.improve(
                outcome.segment(),
                settings.frame(),
                outcome.maskedSource(),
                rewriteBase,
                Objects.requireNonNull(issues.data()),
                calls);
        final RoundOutcome improved = classify(outcome, improveReply);
        return switch (improved) {
            case RoundOutcome.Evaluated evaluated -> polishIfBorderline(outcome, tau, evaluated);
            case RoundOutcome.Failed failed -> failed;
            case RoundOutcome.FlagNow flagNow -> flagNow;
            case RoundOutcome.FlagNowAfterEvaluation flagNowAfterEvaluation -> flagNowAfterEvaluation;
            case RoundOutcome.StepError stepError -> stepError;
        };
    }

    private RoundOutcome polishIfBorderline(
            final DraftOutcome.Drafted outcome, final double tau, final RoundOutcome.Evaluated improved) {
        final QaResult qa = improved.qa();
        final boolean borderline =
                Borderline.isBorderline(qa.hardGatesPass(), qa.failedOutright(), qa.confidence(), tau);
        SegmentHealerLogging.logBorderlineDecision(outcome.segment().id(), qa.confidence(), tau, borderline);
        if (!borderline) {
            return improved;
        }
        final Result<RepairReply> polishReply = polish.polish(
                outcome.segment(), settings.frame(), outcome.maskedSource(), improved.maskedCandidate(), calls);
        return resolvePolishReply(outcome, polishReply, improved);
    }

    private RoundOutcome resolvePolishReply(
            final DraftOutcome.Drafted outcome,
            final Result<RepairReply> reply,
            final RoundOutcome.Evaluated improved) {
        if (reply.isErr()) {
            return new RoundOutcome.StepError(Objects.requireNonNull(reply.error()));
        }
        return switch (Objects.requireNonNull(reply.data())) {
            case RepairReply.FlagNow flagNow -> new RoundOutcome.FlagNowAfterEvaluation(flagNow.error(), improved);
            case RepairReply.Malformed malformed -> keepImproved(outcome, "polish-malformed", improved);
            case RepairReply.Rewritten rewritten -> resolvePolishedTarget(outcome, rewritten.maskedTarget(), improved);
        };
    }

    private RoundOutcome resolvePolishedTarget(
            final DraftOutcome.Drafted outcome, final String maskedCandidate, final RoundOutcome.Evaluated improved) {
        final RoundOutcome polished = evaluateRewrite(outcome, maskedCandidate);
        return switch (polished) {
            case RoundOutcome.Evaluated evaluated when evaluated.qa().hardGatesPass() -> polished;
            case RoundOutcome.StepError stepError -> stepError;
            default -> keepImproved(outcome, "polish-gate-or-hard-gate-failure", improved);
        };
    }

    private RoundOutcome keepImproved(
            final DraftOutcome.Drafted outcome, final String reason, final RoundOutcome.Evaluated improved) {
        log.debug(
                "Polish reply discarded segment={} reason={}; keeping the improved target",
                outcome.segment().id(),
                reason);
        return improved;
    }

    private RoundOutcome classify(final DraftOutcome.Drafted outcome, final Result<RepairReply> reply) {
        if (reply.isErr()) {
            return new RoundOutcome.StepError(Objects.requireNonNull(reply.error()));
        }
        return switch (Objects.requireNonNull(reply.data())) {
            case RepairReply.FlagNow flagNow -> new RoundOutcome.FlagNow(flagNow.error());
            case RepairReply.Malformed malformed -> handleMalformed(outcome, malformed);
            case RepairReply.Rewritten rewritten -> evaluateRewrite(outcome, rewritten.maskedTarget());
        };
    }

    private RoundOutcome handleMalformed(final DraftOutcome.Drafted outcome, final RepairReply.Malformed malformed) {
        log.debug(
                "Self-heal round wasted segment={} diagnostic={}",
                outcome.segment().id(),
                malformed.diagnostic());
        return new RoundOutcome.Failed();
    }

    /**
     * Restores {@code rawCandidate}'s whitespace once and evaluates the result — the same value is what is sent to
     * the gate, what {@code QaEvaluator} reads and what a round records as its masked candidate (one shape, per
     * design D3's "the segment's own whitespace wins").
     */
    private RoundOutcome evaluateRewrite(final DraftOutcome.Drafted outcome, final String rawCandidate) {
        final String maskedCandidate =
                WhitespaceRestoration.restore(outcome.segment().masked(), rawCandidate);
        final Result<String> restored = gate.restore(outcome.segment(), maskedCandidate);
        if (restored.isErr()) {
            final AppError error = Objects.requireNonNull(restored.error());
            if (error.code() == ErrorCode.validation) {
                log.debug(
                        "Self-heal round failed the placeholder gate segment={}",
                        outcome.segment().id());
                return new RoundOutcome.Failed();
            }
            return new RoundOutcome.StepError(error);
        }
        final QaResult qa = QaEvaluation.evaluate(
                List.of(),
                outcome.segment(),
                outcome.maskedSource(),
                maskedCandidate,
                settings,
                outcome.lockedRenderings());
        SegmentHealerLogging.logTraceTarget(
                outcome.segment().id(), maskedCandidate, Objects.requireNonNull(restored.data()));
        return new RoundOutcome.Evaluated(maskedCandidate, Objects.requireNonNull(restored.data()), qa);
    }

    private SegmentOutcome buildAccepted(
            final String segmentId,
            final MachineTarget machine,
            final QaResult qa,
            @Nullable final JudgeVerdict verdict,
            final int rounds,
            final SegmentPath path) {
        final List<QaFinding> findings = SegmentFindings.recorded(qa, verdict, segmentId);
        log.debug(
                "Segment {} accepted path={} rounds={} confidence={} judgeScore={}",
                segmentId,
                path,
                rounds,
                qa.confidence(),
                SegmentFindings.judgeScoreOf(verdict));
        return new SegmentOutcome(
                segmentId,
                SegmentStatus.ACCEPTED,
                machine.restored(),
                machine.masked(),
                qa.confidence(),
                SegmentFindings.judgeScoreOf(verdict),
                findings,
                path,
                rounds,
                null);
    }

    private SegmentOutcome buildFlagged(
            final String segmentId,
            final MachineTarget machine,
            final QaResult qa,
            @Nullable final JudgeVerdict verdict,
            final int rounds,
            @Nullable final AppError flagReason) {
        final List<QaFinding> findings = SegmentFindings.recorded(qa, verdict, segmentId);
        log.warn("Segment {} flagged rounds={} findingKinds={}", segmentId, rounds, SegmentFindings.kindsOf(findings));
        return new SegmentOutcome(
                segmentId,
                SegmentStatus.FLAGGED,
                machine.restored(),
                machine.masked(),
                qa.confidence(),
                SegmentFindings.judgeScoreOf(verdict),
                findings,
                SegmentPath.REPAIRED,
                rounds,
                flagReason);
    }
}
