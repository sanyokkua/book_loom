package ua.bookloom.api.pipeline;

import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.project.SegmentPath;

/**
 * The judge score, path and finding kinds behind one segment's decision, carried on {@link SegmentDecided} for a
 * display that needs more than the bare status.
 *
 * @param judgeScore the judge stage's score, or null when not judged
 * @param path how the segment reached its current target
 * @param findingKinds the kinds of QA finding recorded against the segment
 */
public record SegmentDetail(@Nullable Double judgeScore, SegmentPath path, List<String> findingKinds) {

    /** Rejects a detail without a path and defensively copies {@code findingKinds}. */
    public SegmentDetail {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(findingKinds, "findingKinds");
        findingKinds = List.copyOf(findingKinds);
    }
}
