package ua.bookloom.api.pipeline;

import java.util.Objects;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.document.SegmentStatus;

/**
 * Announces the decision and progress snapshot for one segment.
 *
 * @param segmentId the stable segment identifier
 * @param status the resulting segment status
 * @param reason the typed reason when the segment was flagged, or null otherwise
 * @param progress the progress after this decision
 * @param detail the judge score, path and finding kinds behind this decision, or null when not computed
 */
public record SegmentDecided(
        String segmentId,
        SegmentStatus status,
        @Nullable ErrorCode reason,
        JobProgress progress,
        @Nullable SegmentDetail detail)
        implements JobEvent {

    /** Rejects an event without an id, status or progress snapshot. */
    public SegmentDecided {
        Objects.requireNonNull(segmentId, "segmentId");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(progress, "progress");
    }

    /**
     * Builds a decision event with no detail.
     *
     * @param segmentId the stable segment identifier
     * @param status the resulting segment status
     * @param reason the typed reason when the segment was flagged, or null otherwise
     * @param progress the progress after this decision
     */
    public SegmentDecided(
            final String segmentId,
            final SegmentStatus status,
            @Nullable final ErrorCode reason,
            final JobProgress progress) {
        this(segmentId, status, reason, progress, null);
    }
}
