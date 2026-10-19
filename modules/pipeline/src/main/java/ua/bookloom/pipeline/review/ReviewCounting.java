package ua.bookloom.pipeline.review;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import ua.bookloom.api.document.SegmentKind;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.pipeline.ReviewCounts;
import ua.bookloom.api.project.SegmentPath;
import ua.bookloom.api.project.SegmentRecord;
import ua.bookloom.pipeline.audit.AuditFindings;

/**
 * The one counts rule the review desk and the export report share, so the tiles a person reads before exporting and
 * the report written after it never disagree. A record kept as source by choice is counted only as such, so it is
 * never pending, flagged or reviewed; one kept as it is because nothing in it needed translating is counted apart from
 * the auto-accepted ones, never as kept by choice.
 */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ReviewCounting {

    /**
     * Counts a project's records.
     *
     * @param records the non-null stored records of the project
     * @param keptKinds the non-null auxiliary kinds the brief keeps as source
     * @return the counts; {@code total} is every record, {@code sourceKept} the records kept as source by choice,
     *     and every other field counts the rest
     */
    public static ReviewCounts count(final List<SegmentRecord> records, final Set<SegmentKind> keptKinds) {
        Objects.requireNonNull(records, "records");
        Objects.requireNonNull(keptKinds, "keptKinds");
        final List<SegmentRecord> translated = records.stream()
                .filter(record -> !record.isKeptAsSource(keptKinds))
                .toList();
        return new ReviewCounts(
                records.size(),
                countIf(translated, record -> isUnreviewedAccept(record, SegmentPath.DRAFT, SegmentPath.TM_REUSE)),
                countIf(translated, record -> isUnreviewedAccept(record, SegmentPath.REPAIRED)),
                countIf(translated, record -> record.status() == SegmentStatus.FLAGGED),
                countIf(translated, SegmentRecord::reviewed),
                countIf(translated, record -> record.status() == SegmentStatus.PENDING),
                records.size() - translated.size(),
                countIf(
                        translated,
                        record -> record.status() == SegmentStatus.FLAGGED && record.machineTarget() == null),
                countIf(translated, record -> isUnreviewedAccept(record, SegmentPath.VERBATIM)),
                countIf(translated, AuditFindings::isSuspicious));
    }

    private static boolean isUnreviewedAccept(final SegmentRecord record, final SegmentPath... paths) {
        return record.status() == SegmentStatus.ACCEPTED
                && !record.reviewed()
                && List.of(paths).contains(record.path());
    }

    private static int countIf(final List<SegmentRecord> records, final Predicate<SegmentRecord> condition) {
        return (int) records.stream().filter(condition).count();
    }
}
