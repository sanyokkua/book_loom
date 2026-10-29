package ua.bookloom.llm;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.llm.CallLedger;
import ua.bookloom.api.llm.CallRecord;

/**
 * The application's one call ledger: an append-only queue numbered by a sequence, so a mark is a number and a reader
 * takes the records after it. The job and the interactive callers append from different threads, so nothing here
 * locks; the size is counted beside the queue because a concurrent queue's own size walks every element.
 */
@Slf4j
public final class CallLedgerImpl implements CallLedger {

    /** The most records kept; a night's run makes tens of thousands of calls, and the oldest go first past this. */
    static final int MAX_RECORDS = 100_000;

    private final Queue<Entry> entries = new ConcurrentLinkedQueue<>();
    private final AtomicLong sequence = new AtomicLong();
    private final AtomicInteger size = new AtomicInteger();
    private final AtomicBoolean capReached = new AtomicBoolean();

    /** One kept record and the number it was appended under. */
    private record Entry(long sequence, CallRecord record) {}

    @Override
    public void record(final CallRecord record) {
        Objects.requireNonNull(record, "record");
        entries.add(new Entry(sequence.incrementAndGet(), record));
        if (size.incrementAndGet() > MAX_RECORDS && entries.poll() != null) {
            size.decrementAndGet();
            if (capReached.compareAndSet(false, true)) {
                log.info("Call ledger holds {} records; the oldest are dropped from now on", MAX_RECORDS);
            }
        }
    }

    @Override
    public long mark() {
        return sequence.get();
    }

    @Override
    public List<CallRecord> since(final long mark) {
        final List<CallRecord> after = entries.stream()
                .filter(entry -> entry.sequence() > mark)
                .sorted(Comparator.comparingLong(Entry::sequence))
                .map(Entry::record)
                .toList();
        log.debug("Call ledger read mark={} records={}", mark, after.size());
        return after;
    }
}
