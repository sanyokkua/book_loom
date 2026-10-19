package ua.bookloom.pipeline.run;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import ua.bookloom.pipeline.heal.DraftOutcome;

/**
 * What a chunk's batch calls have made so far: the drafts a batch answered correctly, waiting for their segment's turn,
 * and the segments a batch has already tried, which are drafted singly from then on whatever the batch made of them.
 * Kept across a pause with the chunk, so a resume never repeats a batch call that answered. A stop drops it.
 *
 * <p>Used from the job thread only, which is why the state is plain collections.
 */
final class ChunkBatches {

    private final Map<String, DraftOutcome> ready = new HashMap<>();
    private final Set<String> tried = new HashSet<>();

    /** Whether a batch has dealt with the segment, so no new batch starts at it. */
    boolean hasTried(final String segmentId) {
        return tried.contains(segmentId);
    }

    void tried(final String segmentId) {
        tried.add(segmentId);
    }

    void ready(final String segmentId, final DraftOutcome outcome) {
        ready.put(segmentId, outcome);
    }

    /** The draft a batch made for the segment, handed over once; null when the segment is drafted on its own. */
    @Nullable
    DraftOutcome take(final String segmentId) {
        return ready.remove(segmentId);
    }
}
