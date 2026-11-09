package ua.bookloom.pipeline.heal;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import ua.bookloom.api.project.QaFinding;
import ua.bookloom.pipeline.qa.CheckLevel;
import ua.bookloom.pipeline.qa.CheckLevels;
import ua.bookloom.pipeline.qa.CheckResult;
import ua.bookloom.pipeline.qa.QaResult;

/**
 * Builds the finding lists {@link SegmentHealer} needs: the concrete findings that block a target, the defects the
 * one repair names, and the findings a decided segment's {@link SegmentOutcome} records.
 */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class SegmentFindings {

    /**
     * The concrete findings that block a target: a failed hard gate or a soft check failed outright. Public so a
     * prompt eval names the same findings in a directed fix that a run does.
     */
    public static List<QaFinding> concrete(final QaResult qa) {
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
        return List.copyOf(findings);
    }

    /**
     * The defects the one repair names: every BLOCK and REPAIR finding of the text, and every check that blocks it.
     * Public so a prompt eval builds the repair a run sends.
     *
     * @param qa the evaluation of the text in place
     * @return the defects in the order the checks raised them; never null, empty when there is none
     */
    public static List<QaFinding> defects(final QaResult qa) {
        final Set<QaFinding> defects = new LinkedHashSet<>();
        qa.findings().stream()
                .filter(finding -> CheckLevels.of(finding) != CheckLevel.NOTE)
                .forEach(defects::add);
        defects.addAll(concrete(qa));
        return List.copyOf(defects);
    }

    /** A decided segment's recorded findings: its last evaluation's, de-duplicated. */
    static List<QaFinding> recorded(final QaResult qa) {
        return List.copyOf(new LinkedHashSet<>(qa.findings()));
    }

    /** The kinds of a segment's findings, for a WARN log line that never carries the findings' text. */
    static List<String> kindsOf(final List<QaFinding> findings) {
        return findings.stream().map(QaFinding::kind).toList();
    }
}
