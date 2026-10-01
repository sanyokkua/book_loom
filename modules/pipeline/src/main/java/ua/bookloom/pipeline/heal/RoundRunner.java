package ua.bookloom.pipeline.heal;

import java.util.List;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.Result;
import ua.bookloom.api.project.QaFinding;
import ua.bookloom.pipeline.prompt.ModelCalls;
import ua.bookloom.pipeline.qa.QaResult;

/**
 * Runs one self-heal round's model calls and classifies the reply: a directed fix for concrete findings, otherwise
 * reflect then improve — with a polish pass when the improved target is borderline — or, for a segment drafted in
 * pieces, a redraft of its pieces. {@link SegmentHealer} decides what the round's outcome means for the segment.
 */
@Slf4j
final class RoundRunner {

    private final DirectedFix directedFix;
    private final ReflectImprove reflectImprove;
    private final Polish polish;
    private final LoopSettings settings;
    private final RoundEvaluator evaluator;
    private final ModelCalls calls;

    RoundRunner(
            final DirectedFix directedFix,
            final ReflectImprove reflectImprove,
            final Polish polish,
            final LoopSettings settings,
            final RoundEvaluator evaluator,
            final ModelCalls calls) {
        this.directedFix = Objects.requireNonNull(directedFix, "directedFix");
        this.reflectImprove = Objects.requireNonNull(reflectImprove, "reflectImprove");
        this.polish = Objects.requireNonNull(polish, "polish");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.evaluator = Objects.requireNonNull(evaluator, "evaluator");
        this.calls = Objects.requireNonNull(calls, "calls");
    }

    // A segment drafted in pieces is too large for any one repair call, so every round drafts its pieces again — with
    // the findings when there are any, plain when a reflect round would have run.
    RoundOutcome run(
            final DraftOutcome.Drafted outcome,
            final String rewriteBase,
            final List<QaFinding> findings,
            final double tau,
            final int round) {
        if (outcome.inPieces()) {
            return runPieceRedraftRound(outcome, findings, round);
        }
        return findings.isEmpty()
                ? runReflectImproveRound(outcome, rewriteBase, tau)
                : runDirectedFixRound(outcome, rewriteBase, findings);
    }

    private RoundOutcome runPieceRedraftRound(
            final DraftOutcome.Drafted outcome, final List<QaFinding> findings, final int round) {
        log.debug(
                "Redrafting the pieces of segment={} round={} findings={}",
                outcome.segment().id(),
                round,
                findings.size());
        final Result<RepairReply> reply =
                Objects.requireNonNull(outcome.pieceRedraft()).redraft(findings, calls);
        return evaluator.classify(outcome, reply);
    }

    private RoundOutcome runDirectedFixRound(
            final DraftOutcome.Drafted outcome, final String rewriteBase, final List<QaFinding> findings) {
        final Result<RepairReply> reply = directedFix.fix(
                outcome.segment(), settings.frame(), outcome.maskedSource(), rewriteBase, findings, calls);
        return evaluator.classify(outcome, reply);
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
        final RoundOutcome improved = evaluator.classify(outcome, improveReply);
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
        final RoundOutcome polished = evaluator.evaluateRewrite(outcome, maskedCandidate);
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
}
