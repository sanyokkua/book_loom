package ua.bookloom.api.pipeline;

import java.time.Duration;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.llm.TokenUsage;

/**
 * Announces that one model call has returned.
 *
 * @param segmentId the stable identifier of the segment the request belongs to, or null for a call not tied to one
 *     segment (a prescan or summary call)
 * @param kind what the call was for
 * @param elapsed the wall-clock time the call took
 * @param usage the provider-reported token usage, or null when none was reported
 * @param outputChars the completion text's character count
 * @param usageEstimated true when the provider reported no usage and the pipeline estimated the completion tokens
 */
public record ModelCallFinished(
        @Nullable String segmentId,
        CallKind kind,
        Duration elapsed,
        @Nullable TokenUsage usage,
        int outputChars,
        boolean usageEstimated)
        implements JobEvent {

    /** Rejects an event without a kind or elapsed duration. */
    public ModelCallFinished {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(elapsed, "elapsed");
    }
}
