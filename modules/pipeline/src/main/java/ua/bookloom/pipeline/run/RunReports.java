package ua.bookloom.pipeline.run;

import java.util.List;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppError;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.BookFormat;
import ua.bookloom.api.pipeline.FlaggedSegment;
import ua.bookloom.api.pipeline.JobReport;
import ua.bookloom.api.pipeline.JobState;
import ua.bookloom.api.project.SegmentCounts;
import ua.bookloom.api.project.SegmentRecord;

/**
 * Builds the report a run ends with from what the project stores, so a run resumed after a stop reports the whole
 * book and not only the segments it decided itself.
 */
// Checkstyle parses source text before Lombok's annotation processor creates the private constructor,
// so suppress only its source-level utility-constructor false positive.
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@Slf4j
public final class RunReports {

    /**
     * Reads the project's counts and builds the report.
     *
     * @param stores the non-null stores to read from
     * @param projectId the non-null project id
     * @param format the non-null book format
     * @param end the non-null terminal state the run reached
     * @param error the error of a Failed run, or null
     * @return the report; when the counts cannot be read the report is Failed with that read error and no counts
     */
    public static JobReport of(
            final RunStores stores,
            final String projectId,
            final BookFormat format,
            final JobState end,
            @Nullable final AppError error) {
        Objects.requireNonNull(stores, "stores");
        Objects.requireNonNull(projectId, "projectId");
        Objects.requireNonNull(format, "format");
        Objects.requireNonNull(end, "end");
        final Result<SegmentCounts> counts = stores.segments().countsByStatus(projectId, WorkList.KEPT_AS_SOURCE);
        final Result<List<SegmentRecord>> flagged = stores.segments().flagged(projectId, WorkList.KEPT_AS_SOURCE);
        if (counts.isErr() || flagged.isErr()) {
            final AppError unreadable = Objects.requireNonNull(counts.isErr() ? counts.error() : flagged.error());
            log.warn(
                    "Could not read the project's counts for the report project={} code={}",
                    projectId,
                    unreadable.code());
            return new JobReport(format, JobState.FAILED, 0, 0, 0, List.of(), unreadable);
        }
        return build(
                format,
                end,
                error,
                Objects.requireNonNull(counts.data(), "counts"),
                Objects.requireNonNull(flagged.data(), "flagged"));
    }

    private static JobReport build(
            final BookFormat format,
            final JobState end,
            @Nullable final AppError error,
            final SegmentCounts counts,
            final List<SegmentRecord> flagged) {
        final int segments = counts.pending() + counts.accepted() + counts.revised() + counts.flagged();
        final List<FlaggedSegment> reasons = flagged.stream()
                .map(record -> new FlaggedSegment(record.segmentId(), OutcomeRecords.reportCode(record)))
                .toList();
        log.debug(
                "Built job report state={} segments={} accepted={} flagged={} errorCode={}",
                end,
                segments,
                counts.accepted() + counts.revised(),
                counts.flagged(),
                error == null ? null : error.code());
        return new JobReport(
                format, end, segments, counts.accepted() + counts.revised(), counts.flagged(), reasons, error);
    }
}
