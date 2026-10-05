package ua.bookloom.api.pipeline;

import java.util.List;
import java.util.Objects;

/**
 * An accepted segment the final audit doubts: a cheap deterministic check still fires on its stored target, so a leak
 * that slipped through while the run went on cannot pass unseen.
 *
 * @param segmentId the segment's stable id
 * @param locator where a person finds it, such as {@code ch12 · p02}
 * @param checks the names of the checks that fired, in the order they fired and without repeats, such as
 *     {@code language-identity} or {@code name-missing}; never empty
 */
public record SuspiciousSegment(String segmentId, String locator, List<String> checks) {

    /** Rejects a missing component or an empty check list, and copies the list. */
    public SuspiciousSegment {
        Objects.requireNonNull(segmentId, "segmentId");
        Objects.requireNonNull(locator, "locator");
        Objects.requireNonNull(checks, "checks");
        if (checks.isEmpty()) {
            throw new IllegalArgumentException("a suspicious segment names at least one check");
        }
        checks = List.copyOf(checks);
    }
}
