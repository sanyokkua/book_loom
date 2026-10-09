package ua.bookloom.pipeline.heal;

import java.util.List;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.pipeline.RoundStarted;
import ua.bookloom.api.project.QaFinding;
import ua.bookloom.api.project.SegmentPath;
import ua.bookloom.pipeline.prompt.ModelCalls;
import ua.bookloom.pipeline.qa.QaResult;

/**
 * Settles an accepted segment that carries a soft finding a directed fix can mend ({@link SoftFindings}): a word of
 * the wrong gender, a word with a letter outside the target alphabet, a number the translation changed, a lost or misspelt glossary name, a foreign word. Such a finding is soft — a rule that cannot be sure
 * must never flag a segment — so all of them together earn exactly one directed fix, taken only when the fixed text
 * passes every check and holds none of them any more; otherwise the segment stays accepted as it was with the
 * findings kept as a note for review. Built fresh per chunk by {@link SegmentHealer}.
 */
@Slf4j
final class SoftFix {

    private final DirectedFix directedFix;
    private final RoundEvaluator evaluator;
    private final LoopSettings settings;
    private final ModelCalls calls;

    SoftFix(
            final DirectedFix directedFix,
            final RoundEvaluator evaluator,
            final LoopSettings settings,
            final ModelCalls calls) {
        this.directedFix = Objects.requireNonNull(directedFix, "directedFix");
        this.evaluator = Objects.requireNonNull(evaluator, "evaluator");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.calls = Objects.requireNonNull(calls, "calls");
    }

    /** An accepted target as the decision paths of {@link SegmentHealer} hold it. */
    record Accepted(
            MachineTarget machine,
            QaResult qa,
            String maskedText,
            List<QaFinding> reviewerFindings,
            int rounds,
            SegmentPath path) {}

    SegmentOutcome settle(final DraftOutcome.Drafted outcome, final Accepted accepted) {
        final String segmentId = outcome.segment().id();
        final List<QaFinding> fixable = fixableFindings(accepted.qa());
        if (fixable.isEmpty() || !worthAFix(outcome)) {
            return asDecided(segmentId, accepted);
        }
        log.debug("Directed fix for {} soft finding(s) segment={}", fixable.size(), segmentId);
        calls.announce(new RoundStarted(
                segmentId,
                1,
                settings.dial().repairRounds(),
                null,
                fixable.getFirst().kind()));
        final RoundOutcome result = evaluator.classify(
                outcome,
                directedFix.fix(
                        outcome.segment(),
                        settings.frame(),
                        outcome.maskedSource(),
                        accepted.maskedText(),
                        fixable,
                        calls));
        if (result instanceof RoundOutcome.Evaluated fixed && clears(fixed.qa())) {
            log.debug("Soft fix adopted segment={}", segmentId);
            return asDecided(segmentId, adopted(accepted, fixed));
        }
        log.debug("Soft fix discarded, the accepted text stays segment={}", segmentId);
        return asDecided(segmentId, accepted);
    }

    private boolean worthAFix(final DraftOutcome.Drafted outcome) {
        final boolean repairable = settings.dial().repairRounds() >= 1 && !outcome.inPieces();
        if (!repairable) {
            log.debug(
                    "Soft finding kept as a note segment={} (no repair round or drafted in pieces)",
                    outcome.segment().id());
        }
        return repairable;
    }

    private static Accepted adopted(final Accepted accepted, final RoundOutcome.Evaluated fixed) {
        return new Accepted(
                new MachineTarget(fixed.restoredTarget(), fixed.maskedForm()),
                fixed.qa(),
                fixed.maskedCandidate(),
                accepted.reviewerFindings(),
                accepted.rounds() + 1,
                SegmentPath.REPAIRED);
    }

    private static boolean clears(final QaResult qa) {
        return AcceptanceRule.accepts(qa, 0) && fixableFindings(qa).isEmpty();
    }

    private static SegmentOutcome asDecided(final String segmentId, final Accepted accepted) {
        return SegmentOutcomes.accepted(
                segmentId,
                accepted.machine(),
                accepted.qa(),
                accepted.reviewerFindings(),
                accepted.rounds(),
                accepted.path());
    }

    private static List<QaFinding> fixableFindings(final QaResult qa) {
        return SoftFindings.fixableIn(qa);
    }
}
