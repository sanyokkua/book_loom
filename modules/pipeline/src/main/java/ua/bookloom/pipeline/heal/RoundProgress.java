package ua.bookloom.pipeline.heal;

import java.util.Set;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.pipeline.qa.QaResult;

/**
 * Whether a repair round got anywhere, judged by the sets of checks that still block — never by a score, which a
 * rewrite can raise while the text gets worse. A candidate beats the best one only with fewer failed hard gates, or
 * the same number and fewer blockers; a swap of one blocker for another, a repeat and a regression all make no
 * progress, so the loop stops and keeps the best ({@code specs/quality-gates/spec.md} "Keep the best candidate through
 * the repair path").
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class RoundProgress {

    /**
     * Whether the round's candidate is better than the best one so far.
     *
     * @param best the best candidate the round started from
     * @param candidate the round's evaluated rewrite
     * @param segmentId the segment, for the log line
     * @param round the round, for the log line
     * @return {@code true} when the candidate replaces the best
     */
    static boolean improves(
            final BestCandidate best, final QaResult candidate, final String segmentId, final int round) {
        final Set<String> before = Blockers.of(best.qa());
        final Set<String> after = Blockers.of(candidate);
        final boolean improves = beats(candidate, best.qa(), after.size(), before.size());
        log.debug(
                "Round blockers segment={} round={} before={} after={} improves={}",
                segmentId,
                round,
                before,
                after,
                improves);
        if (!improves) {
            log.warn("Repair rounds stopped segment={} round={} reason=no-progress", segmentId, round);
        }
        return improves;
    }

    /**
     * Whether a changed text is worse than the one it would replace: more failed hard gates, or as many and more
     * blockers. Equal is not worse, because a fix the checks cannot see (a gender, a name) leaves them equal.
     *
     * @param candidate the evaluated new text; never null
     * @param before the evaluated text it would replace; never null
     * @return {@code true} when the new text is worse
     */
    public static boolean isWorse(final QaResult candidate, final QaResult before) {
        return beats(
                before,
                candidate,
                Blockers.of(before).size(),
                Blockers.of(candidate).size());
    }

    private static boolean beats(
            final QaResult candidate, final QaResult best, final int candidateBlockers, final int bestBlockers) {
        final long candidateHard = failedHardGates(candidate);
        final long bestHard = failedHardGates(best);
        return candidateHard < bestHard || (candidateHard == bestHard && candidateBlockers < bestBlockers);
    }

    private static long failedHardGates(final QaResult qa) {
        return qa.hardGates().stream().filter(result -> !result.passed()).count();
    }
}
