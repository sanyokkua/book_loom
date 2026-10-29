package ua.bookloom.api.llm;

import java.util.List;

/**
 * Every model call of the application, as each call reported it, so a run, the names pass, the brief suggestion and
 * the export count their cost from the same records instead of from estimates. A port, so a caller holds no
 * {@code :llm} class; the job and the interactive callers append from different threads.
 */
public interface CallLedger {

    /**
     * A ledger that keeps nothing, for a caller built outside the application's injector.
     *
     * @return the ledger whose {@link #mark()} is always 0 and whose {@link #since(long)} is always empty
     */
    static CallLedger none() {
        return NoCallLedger.INSTANCE;
    }

    /**
     * Keeps one call's record.
     *
     * @param record the non-null record
     */
    void record(CallRecord record);

    /**
     * Where the ledger stands now, to count from later.
     *
     * @return a mark that {@link #since(long)} counts the records after
     */
    long mark();

    /**
     * The records kept after a mark, oldest first.
     *
     * @param mark a value {@link #mark()} returned; 0 reads every record still kept
     * @return never null; empty if no record was kept after the mark
     */
    List<CallRecord> since(long mark);
}
