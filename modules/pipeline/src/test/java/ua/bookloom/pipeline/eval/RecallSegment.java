package ua.bookloom.pipeline.eval;

import java.util.List;
import java.util.Objects;

/**
 * One aligned segment of the detector-recall suite and the detectors that fired on it. Its texts go only to the
 * book directory's {@code segments.jsonl}, outside the repository, never to a report.
 *
 * @param id the segment id both books share
 * @param hash the {@link RecallGold#hash} of the two texts
 * @param source the source's display text
 * @param target the exported book's display text
 * @param fired the detector names that fired, run checks first and then {@code audit:} ones, without repeats
 */
record RecallSegment(String id, String hash, String source, String target, List<String> fired) {

    /** Rejects missing parts and copies the detectors. */
    RecallSegment {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(hash, "hash");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(target, "target");
        fired = List.copyOf(fired);
    }
}
