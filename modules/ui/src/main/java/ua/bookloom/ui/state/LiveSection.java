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
     * How many auxiliary segments the finished run kept as source by choice.
     *
     * @return the read-only count, zero until a run ends; read on the FX thread
     */
    public ReadOnlyIntegerProperty sourceKept() {
        return sourceKept.getReadOnlyProperty();
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
     * Replaces the flagged queue.
     *
     * @param rows the flagged segments in document order
     */
    public void publishFlaggedQueue(final List<FlaggedRow> rows) {
        final List<FlaggedRow> copy = List.copyOf(Objects.requireNonNull(rows, "rows"));
        log.debug("publishing a flagged queue of {} segments", copy.size());
        Platform.runLater(() -> queue.setAll(copy));
    }

    /** Clears every field; runs on the FX thread as part of the mirror's reset for a new run. */
    void reset() {
        liveRows.set(LiveRows.EMPTY);
        throughput.set(Throughput.EMPTY);
        sourceKept.set(0);
        queue.clear();
    }
}
