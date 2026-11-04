package ua.bookloom.pipeline.heal;

import java.util.Objects;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.PlaceholderRepair;
import ua.bookloom.pipeline.WhitespaceRestoration;
import ua.bookloom.pipeline.qa.QaResult;

/**
 * Turns one fixing call's reply — the one repair's, or the reviewer's fix of a refused edit — into a
 * {@link RepairOutcome}: a rewritten reply goes through the gate, the checks and the code's quote repair, a malformed
 * one produces nothing, and a flag or failure ends the segment or the step. The reviewer's edits are evaluated through
 * {@link #evaluateRewrite} too, so every changed text is judged one way.
 */
@Slf4j
final class ReplyEvaluator {

    private final GateFunction gate;
    private final LoopSettings settings;

    ReplyEvaluator(final GateFunction gate, final LoopSettings settings) {
        this.gate = Objects.requireNonNull(gate, "gate");
        this.settings = Objects.requireNonNull(settings, "settings");
    }

    RepairOutcome classify(final DraftOutcome.Drafted outcome, final Result<RepairReply> reply) {
        if (reply.isErr()) {
            return new RepairOutcome.StepError(Objects.requireNonNull(reply.error()));
        }
        return switch (Objects.requireNonNull(reply.data())) {
            case RepairReply.FlagNow(var error) -> new RepairOutcome.FlagNow(error);
            case RepairReply.Malformed malformed -> handleMalformed(outcome, malformed);
            case RepairReply.Rewritten(var maskedTarget) -> evaluateRewrite(outcome, maskedTarget);
        };
    }

    private RepairOutcome handleMalformed(final DraftOutcome.Drafted outcome, final RepairReply.Malformed malformed) {
        log.debug(
                "Fixing reply unreadable segment={} diagnostic={}",
                outcome.segment().id(),
                malformed.diagnostic());
        return new RepairOutcome.Failed(ReplyFormatFinding.of(malformed.diagnostic()));
    }

    /**
     * Restores {@code rawCandidate}'s whitespace once and evaluates the gate's masked form of the result — the same
     * whitespace-restored value is what is sent to the gate and what is kept as the candidate's text (one shape, per
     * design D3's "the segment's own whitespace wins").
     */
    RepairOutcome evaluateRewrite(final DraftOutcome.Drafted outcome, final String rawCandidate) {
        final String maskedCandidate =
                WhitespaceRestoration.restore(outcome.segment().masked(), rawCandidate);
        final String segmentId = outcome.segment().id();
        return switch (gate.restoreRepairing(outcome.segment(), maskedCandidate, PlaceholderRepair.RESTORE_MISSING)) {
            case GateResult.Restored restored -> evaluated(outcome, restored.candidateOr(maskedCandidate), restored);
            case GateResult.GateFailed failed -> {
                log.debug(
                        "Fixing reply gate outcome segment={} outcome=GateFailed raisedBy={}",
                        segmentId,
                        failed.finding().raisedBy());
                yield new RepairOutcome.Failed(failed.finding(), failed.candidate());
            }
            case GateResult.StepError(var error) -> {
                log.debug("Fixing reply gate outcome segment={} outcome=StepError code={}", segmentId, error.code());
                yield new RepairOutcome.StepError(error);
            }
        };
    }

    private RepairOutcome evaluated(
            final DraftOutcome.Drafted outcome, final String maskedCandidate, final GateResult.Restored restored) {
        log.debug(
                "Fixing reply gate outcome segment={} outcome=Restored",
                outcome.segment().id());
        final QaResult qa = QaEvaluation.evaluate(
                DraftEvaluation.notices(outcome.segment().id(), restored.autoRepair(), restored.normalised()),
                outcome.subject(),
                maskedCandidate,
                restored.maskedForm(),
                settings.softContext());
        final Optional<QuoteFixUp.Fixed> fixed =
                QuoteFixUp.fix(outcome.subject(), maskedCandidate, qa, restored.normalised(), gate, settings);
        if (fixed.isPresent()) {
            return evaluatedOf(
                    fixed.get().maskedCandidate(),
                    fixed.get().restored(),
                    fixed.get().qa());
        }
        SegmentHealerLogging.logTraceTarget(outcome.segment().id(), restored.maskedForm(), restored.restored());
        return evaluatedOf(maskedCandidate, restored, qa);
    }

    private static RepairOutcome evaluatedOf(
            final String maskedCandidate, final GateResult.Restored restored, final QaResult qa) {
        return new RepairOutcome.Evaluated(maskedCandidate, restored.maskedForm(), restored.restored(), qa);
    }
}
