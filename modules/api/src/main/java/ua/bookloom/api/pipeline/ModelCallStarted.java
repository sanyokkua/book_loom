package ua.bookloom.api.pipeline;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Announces that one attempt of a model call is now being sent.
 *
 * <p>One event is sent per attempt, so a call the provider client sends again after a timeout announces itself again
 * with the next attempt number, and a waiting clock restarts with it. The event is the only sign of life during a slow
 * request, which is why it carries what a person needs to judge a stall: which call, which attempt, how long it may
 * take and how large the request is.
 *
 * @param segmentId the segment the request belongs to, or null for a call not tied to one segment (a prescan or
 *     summary call, or a reviewer call over a chunk of several segments)
 * @param kind what the call is for
 * @param segmentIds every segment the call is about, in document order; empty for a call about none
 * @param attempt the attempt, counted from one
 * @param maxAttempts how many attempts the call may make if each one stalls, at least {@code attempt}
 * @param timeout how long this attempt may wait for its reply, or null when the model sets no bound of its own
 * @param request the size of the request, or null when it is not known
 */
public record ModelCallStarted(
        @Nullable String segmentId,
        CallKind kind,
        List<String> segmentIds,
        int attempt,
        int maxAttempts,
        @Nullable Duration timeout,
        @Nullable RequestSummary request)
        implements JobEvent {

    /** Rejects an event without a kind or with an impossible attempt, and copies the segment ids. */
    public ModelCallStarted {
        Objects.requireNonNull(kind, "kind");
        segmentIds = List.copyOf(Objects.requireNonNull(segmentIds, "segmentIds"));
        if (attempt < 1 || maxAttempts < attempt) {
            throw new IllegalArgumentException("attempt " + attempt + " of " + maxAttempts);
        }
    }

    /**
     * Builds the first attempt of a call about at most one segment, with no timeout or size known.
     *
     * @param segmentId the segment the request belongs to, or null for a call not tied to one
     * @param kind what the call is for
     */
    public ModelCallStarted(@Nullable final String segmentId, final CallKind kind) {
        this(segmentId, kind, segmentId == null ? List.of() : List.of(segmentId), 1, 1, null, null);
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
