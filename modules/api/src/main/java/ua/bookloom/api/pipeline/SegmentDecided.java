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
 */
public record SegmentDecided(
        String segmentId, SegmentStatus status, @Nullable ErrorCode reason, JobProgress progress) implements JobEvent {

    /** Rejects an event without an id, status or progress snapshot. */
    public SegmentDecided {
        Objects.requireNonNull(segmentId, "segmentId");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(progress, "progress");
    }
}
