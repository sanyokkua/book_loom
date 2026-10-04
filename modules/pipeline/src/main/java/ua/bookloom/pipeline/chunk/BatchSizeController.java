package ua.bookloom.pipeline.chunk;

import lombok.extern.slf4j.Slf4j;

/**
 * Adapts how many items a batched call carries while a run goes on: halve at once when the model loses the plot — a
 * wrong id, an omitted item, a merged pair — and grow back only one item at a time after a clean streak, because a
 * batch that is too large costs a retry while one that is too small only costs speed. Confined to the run's thread.
 */
@Slf4j
public final class BatchSizeController {

    /** What went wrong with a batch, for the log; every kind halves the size. */
    public enum Failure {
        /** The reply named an id the batch did not hold. */
        WRONG_ID,
        /** The reply left an item out. */
        OMISSION,
        /** The reply merged two items into one. */
        MERGE
    }

    private final int min;
    private final int max;
    private final int cleanStreakToGrow;
    private int size;
    private int streak;

    /**
     * Starts at {@code initial}.
     *
     * @param min the smallest batch, at least 1
     * @param max the largest batch, at least {@code min}
     * @param initial the first batch size, within {@code [min, max]}
     * @param cleanStreakToGrow how many clean batches in a row earn one more item; at least 1
     */
    public BatchSizeController(final int min, final int max, final int initial, final int cleanStreakToGrow) {
        if (min < 1 || max < min || initial < min || initial > max || cleanStreakToGrow < 1) {
            throw new IllegalArgumentException("invalid batch size bounds: min=" + min + " max=" + max + " initial="
                    + initial + " streak=" + cleanStreakToGrow);
        }
        this.min = min;
        this.max = max;
        this.size = initial;
        this.cleanStreakToGrow = cleanStreakToGrow;
    }

    /** The items the next batch may carry. */
    public int size() {
        return size;
    }

    /**
     * Records a batch the model got wrong: the size halves, never below the minimum, and the streak starts over.
     *
     * @param failure what went wrong; never null
     */
    public void recordFailure(final Failure failure) {
        final int before = size;
        size = Math.max(min, size / 2);
        streak = 0;
        log.debug("Batch size halved failure={} from={} to={}", failure, before, size);
    }

    /** Records a batch that came back clean; after enough in a row the size grows by one, up to the maximum. */
    public void recordClean() {
        streak++;
        if (streak >= cleanStreakToGrow && size < max) {
            size++;
            streak = 0;
            log.debug("Batch size grown to={} after a clean streak", size);
        }
    }
}
