package ua.bookloom.ui.state;

import java.util.List;
import java.util.Objects;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyIntegerProperty;
import javafx.beans.property.ReadOnlyIntegerWrapper;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;

/**
 * The part of the run's state that shows what the model is doing now and how fast: the two live rows, the pace, the
 * kept-as-source count and the flagged queue.
 *
 * <p>Kept apart from {@link StateMirror} so that class stays a readable size. The properties are read-only and the
 * queue unmodifiable; each {@code publish*} method wraps its mutation in {@link Platform#runLater(Runnable)}, the same
 * bridge the mirror uses.
 */
@Slf4j
public final class LiveSection {

    private final ReadOnlyObjectWrapper<LiveRows> liveRows = new ReadOnlyObjectWrapper<>(LiveRows.EMPTY);
    private final ReadOnlyObjectWrapper<Throughput> throughput = new ReadOnlyObjectWrapper<>(Throughput.EMPTY);
    private final ReadOnlyIntegerWrapper sourceKept = new ReadOnlyIntegerWrapper();
    private final ReadOnlyIntegerWrapper suspicious = new ReadOnlyIntegerWrapper();
    private final ReadOnlyObjectWrapper<@Nullable WaitingCall> waitingCall = new ReadOnlyObjectWrapper<>();
    private final ReadOnlyObjectWrapper<ConnectionStatus> connection =
            new ReadOnlyObjectWrapper<>(ConnectionStatus.UNKNOWN);
    private final ObservableList<FlaggedRow> queue = FXCollections.observableArrayList();
    private final ObservableList<FlaggedRow> readOnlyQueue = FXCollections.unmodifiableObservableList(queue);

    /**
     * The segment decided last and the one in progress.
     *
     * @return the read-only rows; read on the FX thread
     */
    public ReadOnlyObjectProperty<LiveRows> liveRows() {
        return liveRows.getReadOnlyProperty();
    }

    /**
     * Tokens per second, time left and elapsed time.
     *
     * @return the read-only figures; read on the FX thread
     */
    public ReadOnlyObjectProperty<Throughput> throughput() {
        return throughput.getReadOnlyProperty();
    }

    /**
     * The model request the run has been waiting on long enough to mention.
     *
     * @return a read-only property holding {@code null} while nothing is worth mentioning; read on the FX thread
     */
    public ReadOnlyObjectProperty<@Nullable WaitingCall> waitingCall() {
        return waitingCall.getReadOnlyProperty();
    }

    /**
     * How the model server has been answering this run.
     *
     * @return the read-only status, {@link ConnectionStatus#UNKNOWN} before a call; read on the FX thread
     */
    public ReadOnlyObjectProperty<ConnectionStatus> connection() {
        return connection.getReadOnlyProperty();
    }

    /**
     * Shows the request the run waits on, or withdraws it. Not logged: called once a second while a wait lasts.
     *
     * @param call the request, or {@code null} to withdraw the notice
     */
    public void publishWaitingCall(final @Nullable WaitingCall call) {
        Platform.runLater(() -> waitingCall.set(call));
    }

    /**
     * Shows how the server has been answering. Not logged: called once a second while a run lives.
     *
     * @param status the status to show
     */
    public void publishConnection(final ConnectionStatus status) {
        Objects.requireNonNull(status, "status");
        Platform.runLater(() -> connection.set(status));
    }

    /**
     * How many auxiliary segments the finished run kept as source by choice.
     *
     * @return the read-only count, zero until a run ends; read on the FX thread
     */
    public ReadOnlyIntegerProperty sourceKept() {
        return sourceKept.getReadOnlyProperty();
    }

    /**
     * How many accepted segments the finished run's final audit doubts.
     *
     * @return the read-only count, zero until a run ends; read on the FX thread
     */
    public ReadOnlyIntegerProperty suspicious() {
        return suspicious.getReadOnlyProperty();
    }

    /**
     * The flagged segments, in document order, as of the last flagged decision.
     *
     * @return an unmodifiable list; read on the FX thread
     */
    public ObservableList<FlaggedRow> flaggedQueue() {
        return readOnlyQueue;
    }

    /**
     * Shows the two live rows.
     *
     * @param rows the rows to show
     */
    public void publishLiveRows(final LiveRows rows) {
        Objects.requireNonNull(rows, "rows");
        Platform.runLater(() -> liveRows.set(rows));
    }

    /**
     * Shows the pace figures.
     *
     * @param figures the figures to show
     */
    public void publishThroughput(final Throughput figures) {
        Objects.requireNonNull(figures, "figures");
        Platform.runLater(() -> throughput.set(figures));
    }

    /**
     * Shows how many segments the run kept as source.
     *
     * @param count the non-negative count
     */
    public void publishSourceKept(final int count) {
        log.debug("publishing {} segments kept as source", count);
        Platform.runLater(() -> sourceKept.set(count));
    }

    /**
     * Shows how many accepted segments the final audit doubts.
     *
     * @param count the non-negative count
     */
    public void publishSuspicious(final int count) {
        log.debug("publishing {} suspicious segments", count);
        Platform.runLater(() -> suspicious.set(count));
    }

    /**
     * Replaces the flagged queue.
     *
     * @param rows the flagged segments in document order
     */
    public void publishFlaggedQueue(final List<FlaggedRow> rows) {
        final List<FlaggedRow> copy = List.copyOf(Objects.requireNonNull(rows, "rows"));
        log.debug("publishing a flagged queue of {} segments", copy.size());
        Platform.runLater(() -> replaceQueue(copy));
    }

    // A run flags at its end of the book, so the new queue is nearly always the shown one plus a few rows: those are
    // appended, and the list's view lays out only them instead of every flagged row of the night again.
    private void replaceQueue(final List<FlaggedRow> rows) {
        final int shown = queue.size();
        if (rows.size() >= shown && rows.subList(0, shown).equals(queue)) {
            queue.addAll(rows.subList(shown, rows.size()));
            return;
        }
        queue.setAll(rows);
    }

    /** Clears every field; runs on the FX thread as part of the mirror's reset for a new run. */
    void reset() {
        liveRows.set(LiveRows.EMPTY);
        throughput.set(Throughput.EMPTY);
        sourceKept.set(0);
        suspicious.set(0);
        waitingCall.set(null);
        connection.set(ConnectionStatus.UNKNOWN);
        queue.clear();
    }
}
