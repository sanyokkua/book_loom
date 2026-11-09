package ua.bookloom.pipeline.heal;

import java.util.LinkedHashMap;
import java.util.Map;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

    static void logEvaluation(final String segmentId, final int repairs, final QaResult qa) {
        log.debug(
                "Evaluated segment={} repairs={} confidence={} failedOutright={} hardGatesPass={} margins={}"
                        + " hardGates={}",
                segmentId,
                repairs,
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

    static void logAcceptanceDecision(final String segmentId, final QaResult qa, final boolean accepted) {
        final AcceptanceRule.Decision decision = AcceptanceRule.decide(qa);
        log.debug(
                "Acceptance decision segment {} findings block={} repair={} note={} outcome={} mark={} confidence={}"
                        + " blockingFindings={}",
                segmentId,
                decision.block().size(),
                decision.repair().size(),
                decision.note().size(),
                accepted ? "accepted" : "flagged",
                decision.mark(),
                qa.confidence(),
                SegmentFindings.kindsOf(SegmentFindings.concrete(qa)));
    }

    static void logTraceTarget(final String segmentId, final String masked, final String restored) {
        if (log.isTraceEnabled()) {
            log.trace("Round target segment={} masked={} restored={}", segmentId, masked, restored);
        }
    }
}
