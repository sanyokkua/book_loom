package ua.bookloom.pipeline.qa;

import java.util.List;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import ua.bookloom.api.project.QaFinding;
import ua.bookloom.api.project.SegmentRecord;

/**
 * Which stored findings are blocking: those whose level ({@link CheckLevels#of}) is {@link CheckLevel#BLOCK}. The
 * export asks this, and the acceptance rule reads the same level, so a defect that kept a
 * segment from being accepted is called blocking in the same way before and after the run.
 */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class BlockingFindings {

    /**
     * Whether one finding blocks acceptance.
     *
     * @param finding the non-null stored finding
     * @return {@code true} if its level is {@link CheckLevel#BLOCK}, {@code false} otherwise
     */
    public static boolean isBlocking(final QaFinding finding) {
        Objects.requireNonNull(finding, "finding");
        return CheckLevels.of(finding) == CheckLevel.BLOCK;
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
