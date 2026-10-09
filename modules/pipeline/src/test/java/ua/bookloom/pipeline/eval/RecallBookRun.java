package ua.bookloom.pipeline.eval;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * What the detector-recall suite found in one book.
 *
 * @param name the book's label
 * @param segments the aligned segments, in the source's order
 * @param unaligned source segments the exported book does not hold
 * @param gold the book's gold lines by hash; empty when it has none yet
 */
record RecallBookRun(String name, List<RecallSegment> segments, int unaligned, Map<String, RecallGold> gold) {

    /** Copies the parts. */
    RecallBookRun {
        Objects.requireNonNull(name, "name");
        segments = List.copyOf(segments);
        gold = Map.copyOf(gold);
    }

    /** Gold lines whose hash no aligned segment has: a line written for another export, or a typo. */
    long staleGold() {
        return gold.keySet().stream()
                .filter(hash ->
                        segments.stream().noneMatch(segment -> segment.hash().equals(hash)))
                .count();
    }
}
