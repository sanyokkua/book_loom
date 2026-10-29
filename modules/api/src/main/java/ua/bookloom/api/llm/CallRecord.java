package ua.bookloom.api.llm;

import java.time.Duration;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.FailureReason;
import ua.bookloom.api.pipeline.CallKind;

/**
 * What one model call attempt cost, as the provider reported it. A figure the provider did not report stays missing
 * inside {@code usage}, so a sum never counts it as zero; the record holds no text of the request or the reply.
 *
 * @param callKind what the call was for
 * @param usage the figures the provider reported, each null when not reported; null for a failed call or a reply
 *     that reported none
 * @param wallTime how long the attempt took from the request leaving to its answer, the wait for the shared gate
 *     excluded
 * @param timeToFirstToken the measured time before the first token, or null when the call did not measure it
 * @param failureReason why the attempt failed, {@link FailureReason#UNCLASSIFIED} when no catalogue row explains it,
 *     or null when it was answered
 */
public record CallRecord(
        CallKind callKind,
        @Nullable TokenUsage usage,
        Duration wallTime,
        @Nullable Duration timeToFirstToken,
        @Nullable FailureReason failureReason) {

    /** Rejects a record with no kind or no time, which no sum could place. */
    public CallRecord {
        Objects.requireNonNull(callKind, "callKind");
        Objects.requireNonNull(wallTime, "wallTime");
    }

    /**
     * Whether the attempt failed.
     *
     * @return {@code true} if the attempt failed, {@code false} if it was answered
     */
    public boolean isFailed() {
        return failureReason != null;
    }
}
