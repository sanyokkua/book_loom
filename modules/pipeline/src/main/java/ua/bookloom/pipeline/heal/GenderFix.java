package ua.bookloom.pipeline.heal;

import java.util.List;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.pipeline.RoundStarted;
import ua.bookloom.api.project.QaFinding;
import ua.bookloom.api.project.SegmentPath;
import ua.bookloom.pipeline.prompt.ModelCalls;
import ua.bookloom.pipeline.qa.CheckName;
import ua.bookloom.pipeline.qa.QaResult;

/**
 * Settles an accepted segment whose narrator's words carry the wrong gender. The finding is soft — a mismatch the
 * suffix rule cannot be sure of must never flag a segment — so it earns exactly one directed fix, taken only when the
 * fixed text passes every check and no longer has the finding; otherwise the segment stays accepted as it was with the
 * finding kept as a note for review. Built fresh per chunk by {@link SegmentHealer}.
 */
@Slf4j
final class GenderFix {

    private final DirectedFix directedFix;
    private final RoundEvaluator evaluator;
    private final LoopSettings settings;
    private final ModelCalls calls;

    GenderFix(
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
        final QaFinding finding = genderFinding(accepted.qa());
        if (finding == null || !worthAFix(outcome)) {
            return asDecided(segmentId, accepted);
        }
        log.debug("Directed fix for the narrator's gender segment={}", segmentId);
        calls.announce(new RoundStarted(segmentId, 1, settings.dial().repairRounds(), null, finding.kind()));
        final RoundOutcome result = evaluator.classify(
                outcome,
                directedFix.fix(
                        outcome.segment(),
                        settings.frame(),
                        outcome.maskedSource(),
                        accepted.maskedText(),
                        List.of(finding),
                        calls));
        if (result instanceof RoundOutcome.Evaluated fixed && clears(fixed.qa())) {
            log.debug("Gender fix adopted segment={}", segmentId);
            return asDecided(segmentId, adopted(accepted, fixed));
        }
        log.debug("Gender fix discarded, the accepted text stays segment={}", segmentId);
        return asDecided(segmentId, accepted);
    }

    private boolean worthAFix(final DraftOutcome.Drafted outcome) {
        final boolean repairable = settings.dial().repairRounds() >= 1 && !outcome.inPieces();
        if (!repairable) {
            log.debug(
                    "Gender finding kept as a note segment={} (no repair round or drafted in pieces)",
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
        return AcceptanceRule.accepts(qa, 0) && genderFinding(qa) == null;
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

    private static @Nullable QaFinding genderFinding(final QaResult qa) {
        return qa.findings().stream().filter(GenderFix::isGender).findFirst().orElse(null);
    }

    private static boolean isGender(final QaFinding finding) {
        return CheckName.GENDER.raisedBy().equals(finding.raisedBy());
    }
}
