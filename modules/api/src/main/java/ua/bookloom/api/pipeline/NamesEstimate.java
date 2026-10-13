package ua.bookloom.api.pipeline;

/**
 * What a names pass is expected to cost, known before its first call.
 *
 * @param calls how many term-reading calls the dial's plan makes; never negative
 * @param minutesLow the fewest minutes those calls take, rounded up; never negative
 * @param minutesHigh the most minutes those calls take, rounded up; at least {@code minutesLow}
 */
public record NamesEstimate(int calls, int minutesLow, int minutesHigh) {

    /** Rejects a negative count or a range that ends before it starts. */
    public NamesEstimate {
        if (calls < 0 || minutesLow < 0 || minutesHigh < minutesLow) {
            throw new IllegalArgumentException("estimate out of range");
        }
    }
}
