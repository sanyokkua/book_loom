package ua.bookloom.pipeline.heal;

import java.util.Set;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.project.Severity;
import ua.bookloom.pipeline.judge.JudgeFinding;
import ua.bookloom.pipeline.judge.JudgeVerdict;

/**
 * Whether a self-heal round got anywhere. A rewrite identical to the text it was asked to repair, or a re-judge that
 * repeats the same blocking findings at the same score, means another round would most likely repeat itself too, so
 * the segment is flagged now instead of spending the rest of the budget ({@code specs/quality-gates/spec.md} "Stop
 * repairing a segment that does not change").
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class RoundProgress {

    /**
     * Whether the round's rewrite is the very text it was asked to repair.
     *
     * @param previous the state the round started from
     * @param evaluated the round's evaluated rewrite
     * @param segmentId the segment, for the log line
     * @param round the round, for the log line
     * @return {@code true} when nothing changed
     */
    static boolean unchangedText(
            final RoundState previous,
            final RoundOutcome.Evaluated evaluated,
            final String segmentId,
            final int round) {
        final boolean unchanged = evaluated.maskedCandidate().equals(previous.rewriteBase());
        if (unchanged) {
            log.warn("Repair rounds stopped segment={} round={} reason=unchanged-text", segmentId, round);
        }
        return unchanged;
    }

    /**
     * Whether the round's re-judge repeats the verdict the round set out to answer.
     *
     * @param previous the state the round started from, whose routing verdict sent it to a repair
     * @param verdict the round's re-judge
     * @param segmentId the segment the findings are filtered to
     * @param round the round, for the log line
     * @return {@code true} when both verdicts are readable, score the same and name the same non-empty set of medium or
     *     high finding kinds for {@code segmentId}
     */
    static boolean repeatedVerdict(
            final RoundState previous, final JudgeVerdict verdict, final String segmentId, final int round) {
        final JudgeVerdict before = previous.routingVerdict();
        if (before == null || !before.readable() || !verdict.readable()) {
            return false;
        }
        final Set<String> blocking = blockingKinds(verdict, segmentId);
        final boolean repeated = !blocking.isEmpty()
                && Math.abs(before.score() - verdict.score()) <= AcceptanceRule.ACCEPTANCE_TOLERANCE
                && blocking.equals(blockingKinds(before, segmentId));
        if (repeated) {
            log.warn(
                    "Repair rounds stopped segment={} round={} reason=repeated-verdict score={} kinds={}",
                    segmentId,
                    round,
                    verdict.score(),
                    blocking);
        }
        return repeated;
    }

    private static Set<String> blockingKinds(@Nullable final JudgeVerdict verdict, final String segmentId) {
        if (verdict == null) {
            return Set.of();
        }
        return verdict.findings().stream()
                .filter(finding -> finding.segmentId().equals(segmentId))
                .filter(finding -> finding.severity() == Severity.MEDIUM || finding.severity() == Severity.HIGH)
                .map(JudgeFinding::type)
                .collect(Collectors.toUnmodifiableSet());
    }
}
