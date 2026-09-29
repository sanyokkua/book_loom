package ua.bookloom.pipeline.heal;

import java.util.List;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.Result;
import ua.bookloom.pipeline.WhitespaceRestoration;
import ua.bookloom.pipeline.qa.QaResult;

/**
 * Turns one self-heal call's reply into a {@link RoundOutcome}: a rewritten reply goes through the gate and
 * {@code QaEvaluator}, a malformed one wastes the round, and a flag or failure ends the segment or the step.
 */
@Slf4j
final class RoundEvaluator {

    private final GateFunction gate;
    private final LoopSettings settings;

    RoundEvaluator(final GateFunction gate, final LoopSettings settings) {
        this.gate = Objects.requireNonNull(gate, "gate");
        this.settings = Objects.requireNonNull(settings, "settings");
    }

    RoundOutcome classify(final DraftOutcome.Drafted outcome, final Result<RepairReply> reply) {
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
                "Self-heal round wasted segment={} diagnostic={} gateFindingCarried=false",
                outcome.segment().id(),
                malformed.diagnostic());
        return new RoundOutcome.Failed(null);
    }

    /**
     * Restores {@code rawCandidate}'s whitespace once and evaluates the gate's masked form of the result — the same
     * whitespace-restored value is what is sent to the gate and what a round keeps as the text to rewrite (one shape,
     * per design D3's "the segment's own whitespace wins").
     */
    RoundOutcome evaluateRewrite(final DraftOutcome.Drafted outcome, final String rawCandidate) {
        final String maskedCandidate =
                WhitespaceRestoration.restore(outcome.segment().masked(), rawCandidate);
        final String segmentId = outcome.segment().id();
        return switch (gate.restore(outcome.segment(), maskedCandidate)) {
            case GateResult.Restored restored -> evaluated(outcome, maskedCandidate, restored);
            case GateResult.GateFailed failed -> {
                log.debug(
                        "Self-heal round gate outcome segment={} outcome=GateFailed raisedBy={}"
                                + " gateFindingCarried=true",
                        segmentId,
                        failed.finding().raisedBy());
                yield new RoundOutcome.Failed(failed.finding());
            }
            case GateResult.StepError stepError -> {
                log.debug(
                        "Self-heal round gate outcome segment={} outcome=StepError code={}",
                        segmentId,
                        stepError.error().code());
                yield new RoundOutcome.StepError(stepError.error());
            }
        };
    }

    private RoundOutcome evaluated(
            final DraftOutcome.Drafted outcome, final String maskedCandidate, final GateResult.Restored restored) {
        log.debug(
                "Self-heal round gate outcome segment={} outcome=Restored",
                outcome.segment().id());
        final QaResult qa = QaEvaluation.evaluate(
                List.of(),
                outcome.segment(),
                outcome.maskedSource(),
                maskedCandidate,
                restored.maskedForm(),
                settings,
                outcome.lockedRenderings());
        SegmentHealerLogging.logTraceTarget(outcome.segment().id(), restored.maskedForm(), restored.restored());
        return new RoundOutcome.Evaluated(maskedCandidate, restored.maskedForm(), restored.restored(), qa);
    }
}
