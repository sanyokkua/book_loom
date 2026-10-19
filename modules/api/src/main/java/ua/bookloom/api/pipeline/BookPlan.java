package ua.bookloom.api.pipeline;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * What {@link ProjectService#plan} computed a run would do to a project's units before it starts.
 *
 * @param chunksPerUnit the number of chunks each unit, keyed by unit id, will be split into
 * @param oversizedSegmentIds the ids of segments too large to fit within one chunk on their own
 */
public record BookPlan(Map<String, Integer> chunksPerUnit, List<String> oversizedSegmentIds) {

    /** Rejects a plan without its maps/lists and defensively copies them. */
    public BookPlan {
        Objects.requireNonNull(chunksPerUnit, "chunksPerUnit");
        Objects.requireNonNull(oversizedSegmentIds, "oversizedSegmentIds");
        chunksPerUnit = Map.copyOf(chunksPerUnit);
        oversizedSegmentIds = List.copyOf(oversizedSegmentIds);
    }
}
