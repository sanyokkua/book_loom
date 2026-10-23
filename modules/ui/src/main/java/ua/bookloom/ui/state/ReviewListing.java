package ua.bookloom.ui.state;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.pipeline.ReviewFilter;
import ua.bookloom.api.pipeline.SegmentView;

/**
 * The review desk's list: the filter shown, the views the desk listed for it, their rows, and the marks of "later".
 * Kept apart from {@link ReviewViewModel} so that class stays a readable size. FX thread only.
 */
@Slf4j
final class ReviewListing {

    private final ReviewLater later = new ReviewLater();
    private final ObservableList<ReviewRow> rows = FXCollections.observableArrayList();
    private final ObservableList<ReviewRow> readOnlyRows = FXCollections.unmodifiableObservableList(rows);
    private final ReadOnlyObjectWrapper<ReviewFilter> filter = new ReadOnlyObjectWrapper<>(ReviewFilter.ALL_FLAGGED);
    private List<SegmentView> listed = List.of();

    ObservableList<ReviewRow> rows() {
        return readOnlyRows;
    }

    ReadOnlyObjectWrapper<ReviewFilter> filter() {
        return filter;
    }

    /** The ids the list shows, in order. */
    List<String> order() {
        return rows.stream().map(ReviewRow::segmentId).toList();
    }

    /** A copy of the project's marks of "later", for rows built off the FX thread. */
    Set<String> marks(final String projectId) {
        return later.of(projectId);
    }

    void publish(final List<SegmentView> views, final List<ReviewRow> built) {
        log.debug("listing {} segments", views.size());
        listed = views;
        rows.setAll(built);
    }

    void clear() {
        listed = List.of();
        rows.clear();
    }

    /**
     * The row {@code delta} places from {@code selectedId}, stopping at either end.
     *
     * @return the id to select, or {@code null} when the selection stays
     */
    @Nullable
    String moved(final @Nullable String selectedId, final int delta) {
        final List<String> order = order();
        final int at = selectedId == null ? -1 : order.indexOf(selectedId);
        final int to = Math.clamp((long) at + delta, 0, Math.max(0, order.size() - 1));
        log.debug("move {} from row {} to row {} of {}", delta, at, to, order.size());
        return order.isEmpty() || to == at ? null : order.get(to);
    }

    /**
     * Marks a segment "later", redraws its row, and answers the segment to show next.
     *
     * @return the next listed segment after it, or {@code null} when the selection stays
     */
    @Nullable
    String leaveForLater(final String projectId, final String segmentId) {
        final List<String> order = order();
        later.mark(projectId, segmentId);
        rows.setAll(ReviewActs.rowsOf(listed, later.of(projectId)));
        final String next = ReviewNext.after("later", segmentId, segmentId, order, listed);
        return Objects.equals(next, segmentId) ? null : next;
    }
}
