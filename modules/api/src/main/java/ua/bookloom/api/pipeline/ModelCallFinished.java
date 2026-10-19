package ua.bookloom.api.pipeline;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.llm.TokenUsage;

/**
 * Announces that one attempt of a model call has returned, with a reply or with a failure.
 *
 * @param segmentId the segment the request belongs to, or null for a call not tied to one segment
 * @param kind what the call was for
 * @param elapsed the wall-clock time the attempt took
 * @param usage the provider-reported token usage, or null when none was reported or the attempt failed
 * @param outputChars the completion text's character count; zero for a failed attempt
 * @param usageEstimated true when the provider reported no usage and the pipeline estimated the completion tokens
 * @param segmentIds every segment the call is about, in document order; empty for a call about none
 * @param attempt the attempt, counted from one
 * @param failure the code the attempt failed with, or null when it was answered
 */
public record ModelCallFinished(
        @Nullable String segmentId,
        CallKind kind,
        Duration elapsed,
        @Nullable TokenUsage usage,
        int outputChars,
        boolean usageEstimated,
        List<String> segmentIds,
        int attempt,
        @Nullable ErrorCode failure)
        implements JobEvent {

    /** Rejects an event without a kind or elapsed duration, or with an attempt below one, and copies the ids. */
    public ModelCallFinished {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(elapsed, "elapsed");
        segmentIds = List.copyOf(Objects.requireNonNull(segmentIds, "segmentIds"));
        if (attempt < 1) {
            throw new IllegalArgumentException("attempt must be positive: " + attempt);
        }
    }

    /**
     * Builds an answered first attempt of a call about at most one segment.
     *
     * @param segmentId the segment the request belongs to, or null for a call not tied to one
     * @param kind what the call was for
     * @param elapsed the wall-clock time the call took
     * @param usage the provider-reported token usage, or null when none was reported
     * @param outputChars the completion text's character count
     * @param usageEstimated true when the pipeline estimated the completion tokens
     */
    public ModelCallFinished(
            @Nullable final String segmentId,
            final CallKind kind,
            final Duration elapsed,
            @Nullable final TokenUsage usage,
            final int outputChars,
            final boolean usageEstimated) {
        this(
                segmentId,
                kind,
                elapsed,
                usage,
                outputChars,
                usageEstimated,
                segmentId == null ? List.of() : List.of(segmentId),
                1,
                null);
    }

    /**
     * Whether the attempt was answered.
     *
     * @return {@code true} if the provider replied, {@code false} if the attempt failed
     */
    public boolean isAnswered() {
        return failure == null;
    }
}
