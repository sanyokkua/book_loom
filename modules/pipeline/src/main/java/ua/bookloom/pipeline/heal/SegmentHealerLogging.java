package ua.bookloom.pipeline.heal;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.project.QaFinding;
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
            final String segmentId, final QaResult qa, final int verifiedBlockersLeft, final boolean accepted) {
        log.debug(
                "Acceptance decision segment={} confidence={} accepted={} blockingFindings={} verifiedBlockersLeft={}",
                segmentId,
                qa.confidence(),
                accepted,
                SegmentFindings.kindsOf(SegmentFindings.concrete(qa)),
                verifiedBlockersLeft);
    }

    static void logRoundChoice(
            final String segmentId, final int round, final List<QaFinding> concreteFindings, final QaResult qa) {
        if (concreteFindings.isEmpty()) {
            log.debug(
                    "Round choice segment={} round={} kind=none confidence={}: no evidenced finding to fix",
                    segmentId,
                    round,
                    qa.confidence());
            return;
        }
        log.debug(
                "Round choice segment={} round={} kind=directed-fix findingKinds={}",
                segmentId,
                round,
                SegmentFindings.kindsOf(concreteFindings));
    }

    static void logTraceTarget(final String segmentId, final String masked, final String restored) {
        if (log.isTraceEnabled()) {
            log.trace("Round target segment={} masked={} restored={}", segmentId, masked, restored);
        }
    }
}
