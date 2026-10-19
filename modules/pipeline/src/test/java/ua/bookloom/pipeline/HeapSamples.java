package ua.bookloom.pipeline;

import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.List;

/**
 * The heap a soak run retains, read after a full collection: before the run, at each quarter of the book, and after the
 * export. A collection is only a request, so the figures are an upper bound on what is really retained.
 */
final class HeapSamples {

    private static final int QUARTERS = 4;
    private static final long MIB = 1024L * 1024L;

    private long baseline;
    private final List<Long> quarters = new ArrayList<>();
    private long end;

    void baseline() {
        baseline = retained();
    }

    void atFraction(final int done, final int total) {
        if (total > 0 && quarters.size() < QUARTERS && done * QUARTERS >= (quarters.size() + 1) * total) {
            quarters.add(retained());
        }
    }

    void end() {
        end = retained();
    }

    long baselineMib() {
        return baseline / MIB;
    }

    long endMib() {
        return end / MIB;
    }

    /** The retained heap at each quarter reached, in MiB. */
    List<Long> quartersMib() {
        return quarters.stream().map(bytes -> bytes / MIB).toList();
    }

    /** How much the retained heap grew from a quarter of the book to its end, as a fraction of the first. */
    double growthFromFirstQuarter() {
        final long first = quarters.isEmpty() ? end : quarters.getFirst();
        final long last = quarters.isEmpty() ? end : quarters.getLast();
        return first == 0 ? 0 : (double) (last - first) / first;
    }

    /** What the run added to the heap per segment, in bytes, measured at the end against the baseline. */
    long bytesPerSegment(final int segments) {
        return segments == 0 ? 0 : Math.max(0, end - baseline) / segments;
    }

    private static long retained() {
        System.gc();
        return ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed();
    }

    @Override
    public String toString() {
        return "baselineMiB=" + baselineMib() + " quartersMiB=" + quartersMib() + " endMiB=" + endMib();
    }
}
