package ua.bookloom.pipeline.heal;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Whether a self-heal round got anywhere. A rewrite identical to the text it was asked to repair means another round
 * would most likely repeat itself too, so the segment is flagged now instead of spending the rest of the budget ({@code specs/quality-gates/spec.md} "Stop
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
}
