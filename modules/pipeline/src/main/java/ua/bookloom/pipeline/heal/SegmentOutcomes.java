package ua.bookloom.pipeline.heal;

import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppError;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.project.QaFinding;
import ua.bookloom.api.project.SegmentPath;
import ua.bookloom.api.project.Severity;
import ua.bookloom.pipeline.qa.QaResult;

/**
 * Builds the decided {@link SegmentOutcome} of a drafted segment: accepted, flagged, or flagged for want of a
 * reviewer. The outcome's {@code judgeScore} is always {@code null}: the reviewer gives no score, and the confidence
 * the checks derive is what orders segments.
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class SegmentOutcomes {

    /** The kind of the finding a segment carries when the reviewer never answered for it. */
    static final String REVIEWER_UNAVAILABLE = "reviewer-unavailable";

    /** The {@code raisedBy} of every finding the reviewer raises that is not an applied edit. */
    static final String REVIEWER = "reviewer";

    private static final String REVIEWER_UNAVAILABLE_NOTE =
            "The reviewer did not answer, so this target was decided by the quality checks alone.";

    static SegmentOutcome accepted(
            final String segmentId,
            final MachineTarget machine,
            final QaResult qa,
            final List<QaFinding> reviewerFindings,
            final int rounds,
            final SegmentPath path) {
        log.debug(
                "Segment {} accepted path={} rounds={} confidence={} reviewerFindings={}",
                segmentId,
                path,
                rounds,
                qa.confidence(),
                reviewerFindings.size());
        return new SegmentOutcome(
                segmentId,
                SegmentStatus.ACCEPTED,
                machine.restored(),
                machine.masked(),
                qa.confidence(),
                null,
                SegmentFindings.recorded(qa, reviewerFindings),
                path,
                rounds,
                null);
    }

    static SegmentOutcome flagged(
            final String segmentId,
            final MachineTarget machine,
            final QaResult qa,
            final List<QaFinding> reviewerFindings,
            final int rounds,
            @Nullable final AppError flagReason) {
        return flaggedWith(segmentId, machine, qa, rounds, flagReason, SegmentFindings.recorded(qa, reviewerFindings));
    }

    /**
     * Flags a segment the reviewer could not review, keeping its latest target and adding the
     * {@link #REVIEWER_UNAVAILABLE} finding, so the quality checks alone never accept it
     * ({@code specs/quality-gates/spec.md} "Flag a segment the reviewer could not review").
     *
     * @param cause the error the call that never answered ended with, or {@code null} for a reply that could not be
     *     read
     */
    static SegmentOutcome reviewerUnavailable(
            final String segmentId,
            final MachineTarget machine,
            final QaResult qa,
            final int rounds,
            @Nullable final AppError cause) {
        final List<QaFinding> findings = new ArrayList<>(SegmentFindings.recorded(qa, List.of()));
        findings.add(new QaFinding(REVIEWER_UNAVAILABLE, Severity.MEDIUM, REVIEWER_UNAVAILABLE_NOTE, REVIEWER));
        log.debug(
                "Reviewer unavailable for segment={} rounds={} code={}",
                segmentId,
                rounds,
                cause == null ? null : cause.code());
        return flaggedWith(segmentId, machine, qa, rounds, cause, findings);
    }

    private static SegmentOutcome flaggedWith(
            final String segmentId,
            final MachineTarget machine,
            final QaResult qa,
            final int rounds,
            @Nullable final AppError flagReason,
            final List<QaFinding> findings) {
        log.warn("Segment {} flagged rounds={} findingKinds={}", segmentId, rounds, SegmentFindings.kindsOf(findings));
        return new SegmentOutcome(
                segmentId,
                SegmentStatus.FLAGGED,
                machine.restored(),
                machine.masked(),
                qa.confidence(),
                null,
                findings,
                rounds > 0 ? SegmentPath.REPAIRED : SegmentPath.DRAFT,
                rounds,
                flagReason);
    }
}
