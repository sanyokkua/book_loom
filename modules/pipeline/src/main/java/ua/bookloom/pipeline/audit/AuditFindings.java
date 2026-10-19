package ua.bookloom.pipeline.audit;

import java.util.List;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.project.QaFinding;
import ua.bookloom.api.project.SegmentRecord;

/**
 * How the audit marks a stored segment: an ordinary {@link QaFinding} whose {@code raisedBy} is {@code audit:} and the
 * check's name, so it needs no field of its own and the review desk, the report and the export read it like any other.
 */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class AuditFindings {

    /** Starts the {@code raisedBy} of every finding the audit stores. */
    public static final String PREFIX = "audit:";

    /**
     * The audit's finding for a check.
     *
     * @param finding the non-null finding the check raised
     * @return the same finding raised by {@code audit:} and the check's name
     */
    static QaFinding of(final QaFinding finding) {
        Objects.requireNonNull(finding, "finding");
        return new QaFinding(finding.kind(), finding.severity(), finding.note(), PREFIX + finding.raisedBy());
    }

    /**
     * Whether a finding is one the audit stored.
     *
     * @param finding the non-null finding
     * @return {@code true} if the audit raised it, {@code false} otherwise
     */
    public static boolean isAudit(final QaFinding finding) {
        return finding.raisedBy().startsWith(PREFIX);
    }

    /**
     * The names of the checks that fired on a record, in order and without repeats.
     *
     * @param findings the non-null findings
     * @return the check names with the prefix cut off; empty when the audit raised none
     */
    public static List<String> checksOf(final List<QaFinding> findings) {
        Objects.requireNonNull(findings, "findings");
        return findings.stream()
                .filter(AuditFindings::isAudit)
                .map(finding -> finding.raisedBy().substring(PREFIX.length()))
                .distinct()
                .toList();
    }

    /**
     * Whether the record is one the list and the count call suspicious: accepted, not looked at by a person, and
     * carrying an audit finding. A person who accepts, edits or retries a segment has looked at it, so it leaves.
     *
     * @param record the non-null stored record
     * @return {@code true} if the record is suspicious, {@code false} otherwise
     */
    public static boolean isSuspicious(final SegmentRecord record) {
        Objects.requireNonNull(record, "record");
        return record.status() == SegmentStatus.ACCEPTED
                && !record.reviewed()
                && record.findings().stream().anyMatch(AuditFindings::isAudit);
    }
}
