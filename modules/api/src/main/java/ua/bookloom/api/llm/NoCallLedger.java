package ua.bookloom.api.llm;

import java.util.List;
import java.util.Objects;

/** The ledger {@link CallLedger#none()} returns; an enum so its one instance needs no hand-written constructor. */
enum NoCallLedger implements CallLedger {
    INSTANCE;

    @Override
    public void record(final CallRecord record) {
        Objects.requireNonNull(record, "record");
    }

    @Override
    public long mark() {
        return 0;
    }

    @Override
    public List<CallRecord> since(final long mark) {
        return List.of();
    }
}
