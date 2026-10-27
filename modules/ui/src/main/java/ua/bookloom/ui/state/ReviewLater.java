package ua.bookloom.ui.state;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

/**
 * The segments the person left for later, per project. A mark of this application run only: it is never stored and
 * changes nothing a run or an export reads, so a segment left for later is still to review. FX thread only.
 */
@Slf4j
final class ReviewLater {

    private final Map<String, Set<String>> byProject = new HashMap<>();

    /** Marks a segment of a project as left for later. */
    void mark(final String projectId, final String segmentId) {
        Objects.requireNonNull(projectId, "projectId");
        Objects.requireNonNull(segmentId, "segmentId");
        byProject.computeIfAbsent(projectId, id -> new HashSet<>()).add(segmentId);
        log.debug("segment {} of project {} is left for later", segmentId, projectId);
    }

    /** A copy of the project's marks, for a list built off the FX thread; empty when none. */
    Set<String> of(final String projectId) {
        return Set.copyOf(byProject.getOrDefault(projectId, Set.of()));
    }
}
