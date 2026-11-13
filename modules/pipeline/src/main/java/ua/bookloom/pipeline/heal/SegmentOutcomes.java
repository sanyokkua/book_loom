package ua.bookloom.pipeline.heal;

import java.util.List;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppError;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.project.QaFinding;
import ua.bookloom.api.project.SegmentPath;
import ua.bookloom.pipeline.qa.QaResult;

/**
 * Builds the decided {@link SegmentOutcome} of a drafted segment: accepted or flagged. The outcome's
 * {@code judgeScore} is always {@code null}: the confidence the checks derive is what orders segments.
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class SegmentOutcomes {

    static SegmentOutcome accepted(
            final String segmentId, final MachineTarget machine, final QaResult qa, final SegmentPath path) {
        log.debug("Segment {} accepted path={} confidence={}", segmentId, path, qa.confidence());
        return new SegmentOutcome(
                segmentId,
                SegmentStatus.ACCEPTED,
                machine.restored(),
                machine.masked(),
                qa.confidence(),
                null,
                SegmentFindings.recorded(qa),
                path,
                null);
    }

    static SegmentOutcome flagged(
            final String segmentId,
            final MachineTarget machine,
            final QaResult qa,
            final boolean repaired,
            @Nullable final AppError flagReason) {
        return flaggedWith(segmentId, machine, qa, repaired, flagReason, SegmentFindings.recorded(qa));
    }

    private static SegmentOutcome flaggedWith(
            final String segmentId,
            final MachineTarget machine,
            final QaResult qa,
            final boolean repaired,
            @Nullable final AppError flagReason,
            final List<QaFinding> findings) {
        log.warn(
                "Segment {} flagged repaired={} findingKinds={}",
                segmentId,
                repaired,
                SegmentFindings.kindsOf(findings));
        return new SegmentOutcome(
                segmentId,
                SegmentStatus.FLAGGED,
                machine.restored(),
                machine.masked(),
                qa.confidence(),
                null,
                findings,
                repaired ? SegmentPath.REPAIRED : SegmentPath.DRAFT,
                flagReason);
    }
}
