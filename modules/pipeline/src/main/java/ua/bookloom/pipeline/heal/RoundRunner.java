package ua.bookloom.pipeline.heal;

import java.util.List;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.Result;
import ua.bookloom.api.project.QaFinding;
import ua.bookloom.pipeline.prompt.ModelCalls;

/**
 * Runs one repair round's model call and classifies the reply: a directed fix for the evidenced findings or, for a
 * segment drafted in pieces, a redraft of its pieces. There is no call without a finding to name.
 * {@link SegmentHealer} decides what the round's outcome means for the segment.
 */
@Slf4j
final class RoundRunner {

    private final DirectedFix directedFix;
    private final LoopSettings settings;
    private final RoundEvaluator evaluator;
    private final ModelCalls calls;

    RoundRunner(
            final DirectedFix directedFix,
            final LoopSettings settings,
            final RoundEvaluator evaluator,
            final ModelCalls calls) {
        this.directedFix = Objects.requireNonNull(directedFix, "directedFix");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.evaluator = Objects.requireNonNull(evaluator, "evaluator");
        this.calls = Objects.requireNonNull(calls, "calls");
    }

    // A segment drafted in pieces is too large for any one repair call, so every round drafts its pieces again with the
    // findings.
    RoundOutcome run(
            final DraftOutcome.Drafted outcome,
            final String rewriteBase,
            final List<QaFinding> findings,
            final int round) {
        if (outcome.inPieces()) {
            return runPieceRedraftRound(outcome, findings, round);
        }
        final Result<RepairReply> reply = directedFix.fix(
                outcome.segment(), settings.frame(), outcome.maskedSource(), rewriteBase, findings, calls);
        return evaluator.classify(outcome, reply);
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
}
