package ua.bookloom.api.llm;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import ua.bookloom.api.pipeline.CallKind;

/**
 * The seconds one call of each kind took, read from the ledger's records, which a run or a names pass stores on its
 * model so the next estimate multiplies planned calls by them. {@code DRAFT} is left out: its stored figure is seconds
 * per drafted segment, which a batched draft call is not. An interface only because {@code ..api..} may hold nothing
 * but records, enums and interfaces.
 */
public interface CallSeconds {

    /** The fewest seconds a figure is stored as, so a cached instant reply never stores zero. */
    double MIN_SECONDS = 0.1;

    /** Milliseconds in a tenth of a second, the precision a figure is kept to. */
    double MILLIS_PER_TENTH = 100.0;

    /** Tenths in a second. */
    double TENTHS_PER_SECOND = 10.0;

    /**
     * The mean wall time of the answered calls of each kind.
     *
     * @param records the ledger's records of one run or pass; never null
     * @return the seconds per call of each kind with at least one answered call, rounded to a tenth and at least
     *     {@link #MIN_SECONDS}; never null, without {@code DRAFT} and without a kind that had no answered call
     */
    static Map<CallKind, Double> of(final List<CallRecord> records) {
        Objects.requireNonNull(records, "records");
        final Map<CallKind, long[]> sums = new EnumMap<>(CallKind.class);
        records.stream()
                .filter(record -> !record.isFailed() && record.callKind() != CallKind.DRAFT)
                .forEach(record -> {
                    final long[] sum = sums.computeIfAbsent(record.callKind(), _ -> new long[2]);
                    sum[0] += record.wallTime().toMillis();
                    sum[1]++;
                });
        final Map<CallKind, Double> seconds = new EnumMap<>(CallKind.class);
        sums.forEach((kind, sum) -> seconds.put(kind, rounded(sum[0] / (double) sum[1])));
        return Map.copyOf(seconds);
    }

    private static double rounded(final double millis) {
        final double tenths = Math.round(millis / MILLIS_PER_TENTH);
        return Math.max(MIN_SECONDS, tenths / TENTHS_PER_SECOND);
    }
}
