package ua.bookloom.ui.state;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableSet;
import lombok.extern.slf4j.Slf4j;

/**
 * What one operation of the names and style screen did, exactly, and the means to put each row back: the rows the
 * results dialog lists, which of them are reverted, and the single queue through which reverts run, one at a time.
 * FX thread only.
 */
@Slf4j
public final class ChangeResults {

    /** How the operation ended. */
    public enum Outcome {
        /** It changed at least one row. */
        CHANGED,
        /** It finished and changed nothing. */
        NOTHING,
        /** The person stopped it, so it changed nothing. */
        STOPPED
    }

    /** Puts one row back; given what to run when the change is stored, with {@code true}, or refused, with {@code false}. */
    @FunctionalInterface
    public interface Reversal {

        /**
         * Starts putting the row back.
         *
         * @param done called once on the FX thread with whether the row is back
         */
        void run(Consumer<Boolean> done);
    }

    /**
     * A row with the means to put it back.
     *
     * @param row the row
     * @param reversal how to put it back
     */
    public record Item(ChangeRow row, Reversal reversal) {

        /** Rejects missing parts. */
        public Item {
            Objects.requireNonNull(row, "row");
            Objects.requireNonNull(reversal, "reversal");
        }
    }

    private final ChangeOperation operation;
    private final boolean stopped;
    private final List<ChangeRow> rows;
    private final Map<Long, Reversal> reversals = new HashMap<>();
    private final LiveCalls calls;
    private final ObservableSet<Long> reverted = FXCollections.observableSet();
    // One view for every caller: a listener on a view built per call would be dropped with it.
    private final ObservableSet<Long> revertedView = FXCollections.unmodifiableObservableSet(reverted);
    private final Set<Long> queued = new HashSet<>();
    private final Deque<Long> queue = new ArrayDeque<>();
    private final ReadOnlyBooleanWrapper working = new ReadOnlyBooleanWrapper();

    /**
     * Creates the results of an operation.
     *
     * @param operation what ran
     * @param stopped whether the person stopped it
     * @param items the rows with their reversals, in the order the dialog lists them
     * @param calls the model calls the operation made, for the dialog's folded section
     */
    public ChangeResults(
            final ChangeOperation operation, final boolean stopped, final List<Item> items, final LiveCalls calls) {
        this.operation = Objects.requireNonNull(operation, "operation");
        this.stopped = stopped;
        this.rows = items.stream().map(Item::row).toList();
        items.forEach(item -> reversals.put(item.row().id(), item.reversal()));
        this.calls = Objects.requireNonNull(calls, "calls");
    }

    /**
     * What ran.
     *
     * @return the operation
     */
    public ChangeOperation operation() {
        return operation;
    }

    /**
     * How the operation ended.
     *
     * @return stopped, changed nothing, or changed rows
     */
    public Outcome outcome() {
        if (stopped) {
            return Outcome.STOPPED;
        }
        return rows.isEmpty() ? Outcome.NOTHING : Outcome.CHANGED;
    }

    /**
     * The rows, added first, then removed, then changed.
     *
     * @return the rows; empty when nothing changed
     */
    public List<ChangeRow> rows() {
        return rows;
    }

    /**
     * How many rows the operation did a kind of change to.
     *
     * @param kind the kind
     * @return the count
     */
    public int count(final ChangeKind kind) {
        return (int) rows.stream().filter(row -> row.kind() == kind).count();
    }

    /**
     * The calls the operation made.
     *
     * @return the calls as the live call view shows them
     */
    public LiveCalls calls() {
        return calls;
    }

    /**
     * The numbers of the rows that were put back.
     *
     * @return a live set the dialog follows
     */
    public ObservableSet<Long> reverted() {
        return revertedView;
    }

    /**
     * Whether a revert is under way or waiting.
     *
     * @return a read-only flag; the revert controls wait while it is {@code true}
     */
    public ReadOnlyBooleanProperty working() {
        return working.getReadOnlyProperty();
    }

    /**
     * Whether any row can still be put back.
     *
     * @return {@code true} if a row is neither reverted nor waiting, {@code false} otherwise
     */
    public boolean hasRowsToRevert() {
        return rows.stream().anyMatch(row -> !reverted.contains(row.id()) && !queued.contains(row.id()));
    }

    /**
     * Puts one row back as it was.
     *
     * @param rowId the row's number; one already reverted or waiting is ignored
     */
    public void revert(final long rowId) {
        if (!reversals.containsKey(rowId) || reverted.contains(rowId) || !queued.add(rowId)) {
            log.debug("revert of row {} ignored: unknown, done or waiting", rowId);
            return;
        }
        queue.add(rowId);
        log.debug("revert of row {} queued behind {}", rowId, queue.size() - 1);
        drain();
    }

    /** Puts every row back, one after the other; the first failure stops the rest. */
    public void undoAll() {
        log.info("undo all of the {} results: {} rows", operation, rows.size());
        rows.forEach(row -> revert(row.id()));
    }

    private void drain() {
        if (working.get()) {
            return;
        }
        final Long next = queue.poll();
        if (next == null) {
            return;
        }
        working.set(true);
        Objects.requireNonNull(reversals.get(next), "reversal").run(ok -> {
            queued.remove(next);
            working.set(false);
            if (ok) {
                reverted.add(next);
                drain();
            } else {
                log.warn("revert of row {} failed; the remaining {} are dropped", next, queue.size());
                queued.removeAll(queue);
                queue.clear();
            }
        });
    }
}
