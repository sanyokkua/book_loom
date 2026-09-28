package ua.bookloom.pipeline.heal;

import java.util.Objects;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.project.Severity;
import ua.bookloom.pipeline.judge.JudgeVerdict;
import ua.bookloom.pipeline.qa.QaResult;

/**
 * Whether a drafted or repaired target may be accepted: every condition below must hold at once, so one strong
 * signal never carries a segment a weaker one would keep out ({@code specs/quality-gates/spec.md} "Accept a segment
 * only by the acceptance rule"). Pure and silent — the caller logs the decision it reaches with this rule.
 */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class AcceptanceRule {

    /**
     * The floating-point tolerance every τ/τ_judge comparison in this package is read with — this rule's own and
     * {@link Borderline}'s window — so a sum a hair below the true bound still meets it (design D8, "Tuning
     * constants": confidence comparison).
     */
    public static final double ACCEPTANCE_TOLERANCE = 1e-9;

    /**
     * Decides whether one segment's evaluated target may be accepted.
     *
     * @param qa the target's hard-gate and soft-check outcome
     * @param verdict the verdict deciding this segment — the chunk's judge verdict, or a re-judge's — or
     *     {@code null} when the judge is off or did not judge this target
     * @param segmentId the segment a {@code verdict}'s findings are filtered to
     * @param tau the review mode's threshold, read as both τ and τ_judge
     * @return {@code true} when hard gates pass, no soft check failed outright, confidence reaches
     *     {@code tau - }{@link #ACCEPTANCE_TOLERANCE}, and either {@code verdict} is {@code null} or it is readable,
     *     reaches the same threshold and carries no medium or high finding against {@code segmentId}
     */
    public static boolean accepts(
            final QaResult qa, @Nullable final JudgeVerdict verdict, final String segmentId, final double tau) {
        Objects.requireNonNull(qa, "qa");
        Objects.requireNonNull(segmentId, "segmentId");
        if (!qa.hardGatesPass() || qa.failedOutright()) {
            return false;
        }
        if (qa.confidence() < tau - ACCEPTANCE_TOLERANCE) {
            return false;
        }
        return verdict == null || acceptedByJudge(verdict, segmentId, tau);
    }

    private static boolean acceptedByJudge(final JudgeVerdict verdict, final String segmentId, final double tau) {
        if (!verdict.readable() || verdict.score() < tau - ACCEPTANCE_TOLERANCE) {
            return false;
        }
        return verdict.findings().stream()
                .filter(finding -> finding.segmentId().equals(segmentId))
                .noneMatch(finding -> finding.severity() == Severity.MEDIUM || finding.severity() == Severity.HIGH);
    }
}
