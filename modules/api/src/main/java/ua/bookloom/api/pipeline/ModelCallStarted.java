package ua.bookloom.api.pipeline;

import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Announces that one request is now being sent to the model for a segment.
 *
 * <p>One event is sent per model call the pipeline enters, so a segment can produce several: the draft, a structural
 * repair and a placeholder repair are separate calls. The provider client's own transport retries (a timeout, a
 * {@code Retry-After} wait) stay inside the call they belong to and announce nothing, so a waiting clock keeps
 * counting across them. The event is the only sign of life during a slow request, which is why it exists: no decision
 * follows until the request returns.
 *
 * @param segmentId the stable identifier of the segment the request belongs to, or null for a call not tied to one
 *     segment (a prescan or summary call)
 * @param kind what the call is for
 */
public record ModelCallStarted(@Nullable String segmentId, CallKind kind) implements JobEvent {

    /** Rejects an event without a kind. */
    public ModelCallStarted {
        Objects.requireNonNull(kind, "kind");
    }

    /**
     * Builds a draft-call event for the given segment.
     *
     * @param segmentId the stable identifier of the segment the request belongs to
     */
    public ModelCallStarted(final String segmentId) {
        this(Objects.requireNonNull(segmentId, "segmentId"), CallKind.DRAFT);
    }
}
