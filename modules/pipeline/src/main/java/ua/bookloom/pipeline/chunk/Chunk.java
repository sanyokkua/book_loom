package ua.bookloom.pipeline.chunk;

import java.util.List;
import java.util.Objects;
import ua.bookloom.api.document.Segment;

/**
 * Consecutive segments of one unit translated in one model call.
 *
 * @param unitId the owning unit's id
 * @param segments the segments in document order; never empty
 * @param oversized whether the one segment's estimate alone exceeds the chunk budget
 */
public record Chunk(String unitId, List<Segment> segments, boolean oversized) {

    /** Copies the segments so the chunk cannot change after packing. */
    public Chunk {
        Objects.requireNonNull(unitId, "unitId");
        segments = List.copyOf(segments);
    }
}
