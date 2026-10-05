package ua.bookloom.pipeline;

import java.util.Objects;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.document.Segment;
import ua.bookloom.pipeline.heal.DraftOutcome;
import ua.bookloom.pipeline.heal.GateFunction;
import ua.bookloom.pipeline.heal.GateResult;
import ua.bookloom.pipeline.memory.ProtectedMask;
import ua.bookloom.pipeline.prompt.DraftContext;

/**
 * Turns the target a batch reply gave for one item into the segment's draft: its whitespace restored and its protected
 * spans put back through the chunk's gate. A target the gate refuses is not repaired here; the run drafts that segment
 * on its own, with the repairs of a single draft.
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class BatchAdoption {

    static Optional<DraftOutcome> adopt(
            final GateFunction gate, final Segment segment, final ProtectedMask mask, final String maskedTarget) {
        Objects.requireNonNull(segment, "segment");
        Objects.requireNonNull(mask, "mask");
        Objects.requireNonNull(maskedTarget, "maskedTarget");
        final String restoredWhitespace = WhitespaceRestoration.restore(segment.masked(), maskedTarget);
        if (log.isTraceEnabled()) {
            log.trace("Batch target restored={}", restoredWhitespace);
        }
        if (gate.restore(segment, restoredWhitespace) instanceof GateResult.Restored restored) {
            log.debug("Adopted a batch target segmentId={} targetLength={}", segment.id(), maskedTarget.length());
            return Optional.ofNullable(DraftOutcomes.drafted(
                            DraftAttempt.of(segment, DraftContext.empty(), mask), restoredWhitespace, restored)
                    .data());
        }
        log.debug("A batch target was refused by the gate segmentId={}; it is drafted on its own", segment.id());
        return Optional.empty();
    }
}
