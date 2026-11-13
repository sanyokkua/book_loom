package ua.bookloom.api.pipeline;

import java.util.Map;
import java.util.Objects;

/**
 * What a names pass is expected to cost, known before its first call.
 *
 * @param calls how many model calls the dial's plan makes; never negative
 * @param callsByKind how many of those calls are of each kind; never null, a kind with no call absent
 * @param minutesLow the fewest minutes those calls take, rounded up; never negative
 * @param minutesHigh the most minutes those calls take, rounded up; at least {@code minutesLow}
 * @param measured {@code true} when every kind of call has seconds measured on the chosen model, so the minutes are the
 *     calls times those seconds ({@code minutesLow == minutesHigh}); {@code false} when they are the built-in figures,
 *     scaled by the model's measured seconds per segment when there are some, and shown as an estimate
 */
public record NamesEstimate(
        int calls, Map<CallKind, Integer> callsByKind, int minutesLow, int minutesHigh, boolean measured) {

    /** Rejects a negative count or a range that ends before it starts, and copies the counts. */
    public NamesEstimate {
        callsByKind = Map.copyOf(Objects.requireNonNull(callsByKind, "callsByKind"));
        if (calls < 0 || minutesLow < 0 || minutesHigh < minutesLow) {
            throw new IllegalArgumentException("estimate out of range");
        }
    }
}
