package ua.bookloom.pipeline.heal;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.project.QaFinding;
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
     * The concrete findings that send a round to a directed fix: a failed hard gate or a soft check failed outright
     * ({@code specs/quality-gates/spec.md} "Repair a failing segment within the dial's repair budget before
     * flagging it").
     */
    static List<QaFinding> concrete(final QaResult qa) {
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
     * The findings a round repairs when the previous round's reply was refused by a gate: {@code carried} replaces
     * the finding the same check raised before, or joins the list when none did.
     *
     * @param findings the concrete findings of the state the round starts from
     * @param carried the finding the previous round's gate raised, or {@code null} when it raised none
     * @return the merged findings, in the original order
     */
    static List<QaFinding> withCarried(final List<QaFinding> findings, @Nullable final QaFinding carried) {
        if (carried == null) {
            return findings;
        }
        final List<QaFinding> merged = new ArrayList<>(findings);
        for (int index = 0; index < merged.size(); index++) {
            if (merged.get(index).raisedBy().equals(carried.raisedBy())) {
                merged.set(index, carried);
                return List.copyOf(merged);
            }
        }
        merged.add(carried);
        return List.copyOf(merged);
    }

    /**
     * A decided segment's recorded findings: its last evaluation's findings plus the reviewer's — each applied edit,
     * each note, each unresolved issue — de-duplicated ({@code specs/quality-gates/spec.md} "Record each segment's
     * findings for review and repair").
     */
    static List<QaFinding> recorded(final QaResult qa, final List<QaFinding> reviewerFindings) {
        final LinkedHashSet<QaFinding> combined = new LinkedHashSet<>(qa.findings());
        combined.addAll(reviewerFindings);
        return List.copyOf(combined);
    }

    /** The kinds of a segment's findings, for a WARN log line that never carries the findings' text. */
    static List<String> kindsOf(final List<QaFinding> findings) {
        return findings.stream().map(QaFinding::kind).toList();
    }
}
