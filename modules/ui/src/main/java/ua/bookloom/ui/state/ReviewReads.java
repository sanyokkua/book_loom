package ua.bookloom.ui.state;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import javafx.application.Platform;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.pipeline.ReviewFilter;
import ua.bookloom.api.pipeline.SegmentView;

/**
 * The review desk's reads, each made on the background executor and answered on the FX thread; a read the desk could
 * not answer answers nothing, so the desk keeps what it shows. Kept apart from {@link ReviewViewModel} so that class
 * stays a readable size.
 */
final class ReviewReads {

    private final ReviewQueries queries;
    private final ExecutorService executor;

    ReviewReads(final ReviewQueries queries, final ExecutorService executor) {
        this.queries = Objects.requireNonNull(queries, "queries");
        this.executor = Objects.requireNonNull(executor, "executor");
    }

    /** Reads a filter's list and builds its rows with the marks of "later". */
    void list(
            final String projectId,
            final ReviewFilter filter,
            final Set<String> later,
            final BiConsumer<List<SegmentView>, List<ReviewRow>> onFx) {
        executor.execute(() -> {
            final List<SegmentView> views = queries.list(projectId, filter);
            if (views != null) {
                final List<ReviewRow> rows = ReviewActs.rowsOf(views, later);
                Platform.runLater(() -> onFx.accept(views, rows));
            }
        });
    }

    /** Reads what the desk opens on ({@link ReviewQueries#opened}) and builds its rows with the marks of "later". */
    void opened(
            final String projectId,
            final ReviewFilter filter,
            final @Nullable String segmentId,
            final boolean widen,
            final Set<String> later,
            final BiConsumer<ReviewQueries.Opened, List<ReviewRow>> onFx) {
        executor.execute(() -> {
            final ReviewQueries.Opened opened = queries.opened(projectId, filter, segmentId, widen);
            if (opened != null) {
                final List<ReviewRow> rows = ReviewActs.rowsOf(opened.views(), later);
                Platform.runLater(() -> onFx.accept(opened, rows));
            }
        });
    }

    /** Reads one segment's current view. */
    void segment(final String projectId, final String segmentId, final Consumer<SegmentView> onFx) {
        executor.execute(() -> {
            final SegmentView view = queries.segment(projectId, segmentId);
            if (view != null) {
                Platform.runLater(() -> onFx.accept(view));
            }
        });
    }
}
