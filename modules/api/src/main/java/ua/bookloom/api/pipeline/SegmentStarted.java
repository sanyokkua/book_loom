package ua.bookloom.api.pipeline;

import java.util.Objects;

/**
 * Announces that one segment's translation has begun.
 *
 * @param segmentId the segment's stable id
 * @param locator the segment's human-readable {@code SegmentLocator} text
 * @param displaySource the segment's source text as shown to a person
 * @param position the segment's chunk position within its section
 */
public record SegmentStarted(String segmentId, String locator, String displaySource, ChunkPosition position)
        implements JobEvent {

    /** Rejects an event missing any of its non-null components. */
    public SegmentStarted {
        Objects.requireNonNull(segmentId, "segmentId");
        Objects.requireNonNull(locator, "locator");
        Objects.requireNonNull(displaySource, "displaySource");
        Objects.requireNonNull(position, "position");
    }
}
