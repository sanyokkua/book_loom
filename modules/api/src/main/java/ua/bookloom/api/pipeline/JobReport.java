package ua.bookloom.api.pipeline;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppError;
import ua.bookloom.api.document.BookFormat;

/**
 * The terminal outcome and counts returned by a translation job.
 *
 * @param format the source book format
 * @param end the terminal job state
 * @param segments total number of segments in the job
 * @param accepted number of accepted segments
 * @param flagged number of flagged segments
 * @param flaggedSegments flagged segment identities and reasons, defensively copied
 * @param written the output path for a completed job, or null otherwise
 * @param error the terminal error for a failed job, or null otherwise
 */
public record JobReport(
        BookFormat format,
        JobState end,
        int segments,
        int accepted,
        int flagged,
        List<FlaggedSegment> flaggedSegments,
        @Nullable Path written,
        @Nullable AppError error) {

    /**
     * Enforces that reports describe only terminal states and pair conditional data with the matching outcome.
     */
    public JobReport {
        Objects.requireNonNull(format, "format");
        Objects.requireNonNull(end, "end");
        Objects.requireNonNull(flaggedSegments, "flaggedSegments");
        flaggedSegments = List.copyOf(flaggedSegments);
        if (!isTerminal(end)) {
            throw new IllegalArgumentException("end must be terminal, but was " + end);
        }
        if ((end == JobState.FAILED) != (error != null)) {
            throw new IllegalArgumentException("error must be present exactly when end is FAILED");
        }
        if ((end == JobState.COMPLETED) != (written != null)) {
            throw new IllegalArgumentException("written must be present exactly when end is COMPLETED");
        }
    }

    private static boolean isTerminal(final JobState state) {
        return switch (state) {
            case COMPLETED, CANCELLED, FAILED -> true;
            case NEW, RUNNING, PAUSED -> false;
        };
    }
}
