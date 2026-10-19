package ua.bookloom.pipeline.review;

import java.util.Objects;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.project.ContextSnapshot;
import ua.bookloom.pipeline.heal.SegmentOutcome;

/**
 * A retry's decided draft before anything is stored, for a caller that keeps it only when it is better than the
 * stored text.
 *
 * @param segment the opened book's segment that was drafted again
 * @param outcome the quality loop's decision on the new draft; ACCEPTED or FLAGGED
 * @param snapshot the context the first draft saw, which the retry replayed and a stored record keeps
 */
public record RetryCandidate(Segment segment, SegmentOutcome outcome, ContextSnapshot snapshot) {

    /** Rejects a missing part. */
    public RetryCandidate {
        Objects.requireNonNull(segment, "segment");
        Objects.requireNonNull(outcome, "outcome");
        Objects.requireNonNull(snapshot, "snapshot");
    }
}
