package ua.bookloom.pipeline.heal;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppError;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.project.QaFinding;
import ua.bookloom.api.project.SegmentPath;
import ua.bookloom.api.project.Severity;
import ua.bookloom.pipeline.judge.JudgeVerdict;
import ua.bookloom.pipeline.qa.QaResult;

/** Builds the decided {@link SegmentOutcome} of a drafted segment: accepted, flagged, or flagged for want of a judge. */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class SegmentOutcomes {

    /** The kind of the finding a segment carries when the judge never answered for it. */
    static final String JUDGE_UNAVAILABLE = "judge-unavailable";

    private static final String JUDGE = "judge";
    private static final String JUDGE_UNAVAILABLE_NOTE =
            "The judge did not answer, so this target was decided by the quality checks alone.";

    static SegmentOutcome accepted(
            final String segmentId,
            final MachineTarget machine,
            final QaResult qa,
            @Nullable final JudgeVerdict verdict,
            final int rounds,
            final SegmentPath path) {
        final List<QaFinding> findings = SegmentFindings.recorded(qa, verdict, segmentId);
        log.debug(
                "Segment {} accepted path={} rounds={} confidence={} judgeScore={}",
                segmentId,
                path,
                rounds,
                qa.confidence(),
                SegmentFindings.judgeScoreOf(verdict));
        return new SegmentOutcome(
                segmentId,
                SegmentStatus.ACCEPTED,
                machine.restored(),
                machine.masked(),
                qa.confidence(),
                SegmentFindings.judgeScoreOf(verdict),
                findings,
                path,
                rounds,
                null);
    }

    static SegmentOutcome flagged(
            final String segmentId,
            final MachineTarget machine,
            final QaResult qa,
            @Nullable final JudgeVerdict verdict,
            final int rounds,
            @Nullable final AppError flagReason) {
        return flaggedWith(
                segmentId, machine, qa, verdict, rounds, flagReason, SegmentFindings.recorded(qa, verdict, segmentId));
    }

    /**
     * Flags a segment the judge could not judge, keeping its latest target and adding the {@link #JUDGE_UNAVAILABLE}
     * finding, so the quality checks alone never accept it ({@code specs/quality-gates/spec.md} "Flag a segment the
     * judge could not judge").
     *
     * @param recordedVerdict the last verdict that did judge this segment, or null when none did
     * @param unavailable the verdict of the judge call that never answered
     */
    static SegmentOutcome judgeUnavailable(
            final String segmentId,
            final MachineTarget machine,
            final QaResult qa,
            @Nullable final JudgeVerdict recordedVerdict,
            final int rounds,
            final JudgeVerdict unavailable) {
        final AppError cause = Objects.requireNonNull(unavailable.unavailableBecause(), "unavailableBecause");
        final List<QaFinding> findings = new ArrayList<>(SegmentFindings.recorded(qa, recordedVerdict, segmentId));
        findings.add(new QaFinding(JUDGE_UNAVAILABLE, Severity.MEDIUM, JUDGE_UNAVAILABLE_NOTE, JUDGE));
        log.debug("Judge unavailable for segment={} rounds={} code={}", segmentId, rounds, cause.code());
        return flaggedWith(segmentId, machine, qa, recordedVerdict, rounds, cause, findings);
    }

    private static SegmentOutcome flaggedWith(
            final String segmentId,
            final MachineTarget machine,
            final QaResult qa,
            @Nullable final JudgeVerdict verdict,
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
                SegmentFindings.judgeScoreOf(verdict),
                findings,
                rounds > 0 ? SegmentPath.REPAIRED : SegmentPath.DRAFT,
                rounds,
                flagReason);
    }
}
