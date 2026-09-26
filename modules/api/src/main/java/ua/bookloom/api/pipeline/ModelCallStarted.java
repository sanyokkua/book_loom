package ua.bookloom.api.pipeline;

import java.util.Objects;

/**
 * Announces that one request is now being sent to the model for a segment.
 *
 * <p>One event is sent per model call the pipeline enters, so a segment can produce several: the draft, a structural
 * repair and a placeholder repair are separate calls. The provider client's own transport retries (a timeout, a
 * {@code Retry-After} wait) stay inside the call they belong to and announce nothing, so a waiting clock keeps
 * counting across them. The event is the only sign of life during a slow request, which is why it exists: no decision
 * follows until the request returns.
 *
 * @param segmentId the stable identifier of the segment the request belongs to
 */
public record ModelCallStarted(String segmentId) implements JobEvent {

    /** Rejects an event without a segment id. */
    public ModelCallStarted {
        Objects.requireNonNull(segmentId, "segmentId");
    }
}
