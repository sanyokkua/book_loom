package ua.bookloom.ui.state;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import javafx.application.Platform;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppError;
import ua.bookloom.api.Result;
import ua.bookloom.api.pipeline.ReviewFilter;
import ua.bookloom.api.pipeline.SegmentView;
import ua.bookloom.api.project.SegmentRecord;

/**
 * Runs a review desk action off the FX thread and reads what the desk looks like after it: the count, the list and
 * the segment to show next. Kept apart from {@link ReviewViewModel} so that class stays a readable size.
 */
@Slf4j
final class ReviewActs {

    /**
     * One action asked for: everything the background call needs, read on the FX thread when it was asked.
     *
     * @param name the action's name, as logged and as {@link ReviewNext} reads it
     * @param projectId the open book's project
     * @param segment the segment acted on
     * @param order the ids the list showed, in order
     * @param filter the list shown
     * @param shownCount the to-review count shown, answered when the desk cannot be read
     * @param later the segments left for later, copied
     */
    record Request(
            String name,
            String projectId,
            SegmentView segment,
            List<String> order,
            ReviewFilter filter,
            int shownCount,
            Set<String> later) {}

    /**
     * What the desk looks like after a confirmed action, handed over on the FX thread.
     *
     * @param request the action
     * @param stored the record the desk answered
     * @param remaining the to-review count after it
     * @param listed the list after it
     * @param rows the list's rows
     * @param next the segment to show next, or {@code null} when nothing is left
     */
    record Done(
            Request request,
            SegmentRecord stored,
            int remaining,
            List<SegmentView> listed,
            List<ReviewRow> rows,
            @Nullable SegmentView next) {}

    private final ReviewQueries queries;
    private final StateMirror mirror;
    private final ReviewRefusals refusals;
    private final ExecutorService executor;

    ReviewActs(
            final ReviewQueries queries,
            final StateMirror mirror,
            final ReviewRefusals refusals,
            final ExecutorService executor) {
        this.queries = Objects.requireNonNull(queries, "queries");
        this.mirror = Objects.requireNonNull(mirror, "mirror");
        this.refusals = Objects.requireNonNull(refusals, "refusals");
        this.executor = Objects.requireNonNull(executor, "executor");
    }

    /** The rows of a list, with the marks of the segments left for later. Logs nothing: it runs per row. */
    static List<ReviewRow> rowsOf(final List<SegmentView> views, final Set<String> later) {
        return views.stream()
                .map(view -> ReviewRow.of(view, later.contains(view.segmentId())))
                .toList();
    }

    /**
     * Sends the action; answers whether it was dispatched, so a caller holding something for it can let go when it
     * was not.
     */
    boolean act(
            final Request request,
            final BiFunction<String, String, Result<SegmentRecord>> call,
            final Consumer<Done> done) {
        try {
            executor.execute(() -> perform(
                    request, call.apply(request.projectId(), request.segment().segmentId()), done));
            return true;
        } catch (RejectedExecutionException rejected) {
            refusals.notStarted(request.name(), request.segment().segmentId(), rejected);
            return false;
        }
    }

    // After a decision the desk moves on (ReviewNext): it never keeps showing a segment that left the list.
    private void perform(final Request request, final Result<SegmentRecord> result, final Consumer<Done> done) {
        final SegmentRecord stored = result.data();
        final String segmentId = request.segment().segmentId();
        if (stored == null) {
            final AppError error = Objects.requireNonNull(result.error(), "error");
            Platform.runLater(() -> refusals.show(request.name(), segmentId, error));
            return;
        }
        log.info("review {} of segment {} left it {}", request.name(), segmentId, stored.status());
        final String projectId = request.projectId();
        final int remaining = queries.refreshed(projectId, request.shownCount(), mirror.live());
        final List<SegmentView> listed =
                Objects.requireNonNullElse(queries.list(projectId, request.filter()), List.of());
        final String nextId = ReviewNext.after(request.name(), segmentId, stored.segmentId(), request.order(), listed);
        final SegmentView next = nextId == null ? null : queries.segment(projectId, nextId);
        final List<ReviewRow> rows = rowsOf(listed, request.later());
        Platform.runLater(() -> done.accept(new Done(request, stored, remaining, listed, rows, next)));
    }
}
