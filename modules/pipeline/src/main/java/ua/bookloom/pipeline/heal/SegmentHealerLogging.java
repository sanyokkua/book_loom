package ua.bookloom.pipeline.heal;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.project.QaFinding;
import ua.bookloom.pipeline.judge.JudgeVerdict;
import ua.bookloom.pipeline.qa.CheckName;
import ua.bookloom.pipeline.qa.QaResult;

/**
 * {@link SegmentHealer}'s DEBUG/WARN/TRACE lines, kept apart only to keep that class under the file-length limit —
 * every line here is diagnostic for a decision {@code SegmentHealer} makes, not a decision of its own.
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class SegmentHealerLogging {

    static void logEvaluation(final String segmentId, final int round, final QaResult qa) {
        log.debug(
                "Evaluated segment={} round={} confidence={} failedOutright={} hardGatesPass={} margins={}"
                        + " hardGates={}",
                segmentId,
                round,
                qa.confidence(),
                qa.failedOutright(),
                qa.hardGatesPass(),
                marginsOf(qa),
                hardGateResultsOf(qa));
    }

    private static Map<CheckName, Double> marginsOf(final QaResult qa) {
        final Map<CheckName, Double> margins = new LinkedHashMap<>();
        qa.soft().forEach(result -> margins.put(result.check(), result.margin()));
        return margins;
    }

    private static Map<CheckName, String> hardGateResultsOf(final QaResult qa) {
        final Map<CheckName, String> results = new LinkedHashMap<>();
        qa.hardGates().forEach(result -> results.put(result.check(), result.passed() ? "pass" : "fail"));
        return results;
    }

    static void logAcceptanceDecision(
            final String segmentId,
            final double tau,
            final QaResult qa,
            @Nullable final JudgeVerdict verdict,
            final boolean accepted) {
        final List<String> blocking = SegmentFindings.kindsOf(SegmentFindings.concrete(qa, verdict, segmentId));
        log.debug(
                "Acceptance decision segment={} tau={} confidence={} judgeScore={} accepted={} blockingFindings={}",
                segmentId,
                tau,
                qa.confidence(),
                verdict == null ? null : verdict.score(),
                accepted,
                blocking);
    }

    static void logRoundChoice(
            final String segmentId,
            final int round,
            final List<QaFinding> concreteFindings,
            final QaResult qa,
            @Nullable final JudgeVerdict verdict,
            final double tau) {
        if (concreteFindings.isEmpty()) {
            log.debug(
                    "Round choice segment={} round={} kind=reflect-improve reason={}",
                    segmentId,
                    round,
                    reflectImproveReason(qa, verdict, tau));
            return;
        }
        log.debug(
                "Round choice segment={} round={} kind=directed-fix findingKinds={}",
                segmentId,
                round,
                SegmentFindings.kindsOf(concreteFindings));
    }

    private static String reflectImproveReason(
            final QaResult qa, @Nullable final JudgeVerdict verdict, final double tau) {
        if (qa.confidence() < tau - AcceptanceRule.ACCEPTANCE_TOLERANCE) {
            return "confidence below τ";
        }
        if (verdict != null && !verdict.readable()) {
            return "unreadable verdict";
        }
        if (verdict != null && verdict.score() < tau - AcceptanceRule.ACCEPTANCE_TOLERANCE) {
            return "judge score below τ_judge";
        }
        return "no concrete finding";
    }

    static void logBorderlineDecision(
            final String segmentId, final double confidence, final double tau, final boolean borderline) {
        log.debug(
                "Borderline decision segment={} confidence={} tau={} borderline={}",
                segmentId,
                confidence,
                tau,
                borderline);
    }

    static void logTraceTarget(final String segmentId, final String masked, final String restored) {
        if (log.isTraceEnabled()) {
            log.trace("Round target segment={} masked={} restored={}", segmentId, masked, restored);
        }
    }
}
