package ua.bookloom.pipeline.heal;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.project.QaFinding;
import ua.bookloom.api.project.Severity;
import ua.bookloom.pipeline.judge.JudgeFinding;
import ua.bookloom.pipeline.judge.JudgeVerdict;
import ua.bookloom.pipeline.qa.CheckResult;
import ua.bookloom.pipeline.qa.QaResult;

/**
 * Builds the two finding lists {@link SegmentHealer} needs: the concrete findings that route a round to a directed
 * fix, and the findings a decided segment's {@link SegmentOutcome} records.
 */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class SegmentFindings {

    /**
     * The concrete findings that send a round to a directed fix: a failed hard gate, a soft check failed outright,
     * or a medium/high judge finding against {@code segmentId}
     * ({@code specs/quality-gates/spec.md} "Repair a failing segment within the dial's repair budget before
     * flagging it").
     */
    static List<QaFinding> concrete(final QaResult qa, @Nullable final JudgeVerdict verdict, final String segmentId) {
        final List<QaFinding> findings = new ArrayList<>();
        qa.hardGates().stream()
                .filter(result -> !result.passed())
                .map(CheckResult::finding)
                .filter(Objects::nonNull)
                .forEach(findings::add);
        qa.soft().stream()
                .filter(CheckResult::blocking)
                .map(CheckResult::finding)
                .filter(Objects::nonNull)
                .forEach(findings::add);
        addJudgeFindings(findings, verdict, segmentId, true);
        return List.copyOf(findings);
    }

    /**
     * A decided segment's recorded findings: its last evaluation's findings plus every finding — any severity — of
     * the verdict that last decided it, on this segment, de-duplicated ({@code specs/quality-gates/spec.md}
     * "Record each segment's findings for review and repair").
     */
    static List<QaFinding> recorded(final QaResult qa, @Nullable final JudgeVerdict verdict, final String segmentId) {
        final LinkedHashSet<QaFinding> combined = new LinkedHashSet<>(qa.findings());
        final List<QaFinding> judgeFindings = new ArrayList<>();
        addJudgeFindings(judgeFindings, verdict, segmentId, false);
        combined.addAll(judgeFindings);
        return List.copyOf(combined);
    }

    /** The score of the verdict that decided a segment, or {@code null} when none did or it was unreadable. */
    static @Nullable Double judgeScoreOf(@Nullable final JudgeVerdict verdict) {
        return verdict != null && verdict.readable() ? verdict.score() : null;
    }

    /** The kinds of a segment's findings, for a WARN log line that never carries the findings' text. */
    static List<String> kindsOf(final List<QaFinding> findings) {
        return findings.stream().map(QaFinding::kind).toList();
    }

    private static void addJudgeFindings(
            final List<QaFinding> target,
            @Nullable final JudgeVerdict verdict,
            final String segmentId,
            final boolean mediumOrHighOnly) {
        if (verdict == null || !verdict.readable()) {
            return;
        }
        verdict.findings().stream()
                .filter(finding -> finding.segmentId().equals(segmentId))
                .filter(finding -> !mediumOrHighOnly
                        || finding.severity() == Severity.MEDIUM
                        || finding.severity() == Severity.HIGH)
                .map(JudgeFinding::toQaFinding)
                .forEach(target::add);
    }
}
