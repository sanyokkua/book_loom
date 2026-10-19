package ua.bookloom.pipeline.qa;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import ua.bookloom.api.project.QaFinding;
import ua.bookloom.api.project.SegmentRecord;
import ua.bookloom.api.project.Severity;

/**
 * Which stored findings are blocking: a hard gate or a blocking text check raised them at high severity. The export
 * and the consistency pass both ask this, so a defect that kept a segment from being accepted is called blocking in
 * the same way before and after the run.
 */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class BlockingFindings {

    private static final Set<String> RAISERS = Stream.of(CheckName.values())
            .filter(CheckName::isHardGate)
            .map(CheckName::raisedBy)
            .collect(Collectors.toUnmodifiableSet());

    /**
     * Whether one finding blocks acceptance.
     *
     * @param finding the non-null stored finding
     * @return {@code true} if a hard gate raised it at high severity, {@code false} otherwise
     */
    public static boolean isBlocking(final QaFinding finding) {
        Objects.requireNonNull(finding, "finding");
        return finding.severity() == Severity.HIGH && RAISERS.contains(finding.raisedBy());
    }

    /**
     * The blocking findings a record still holds.
     *
     * @param record the non-null stored record
     * @return the blocking findings in stored order; empty when the person's own text replaced the machine's (the
     *     findings describe the machine text) or when none blocks
     */
    public static List<QaFinding> of(final SegmentRecord record) {
        Objects.requireNonNull(record, "record");
        return record.userTarget() != null
                ? List.of()
                : record.findings().stream()
                        .filter(BlockingFindings::isBlocking)
                        .toList();
    }
}
