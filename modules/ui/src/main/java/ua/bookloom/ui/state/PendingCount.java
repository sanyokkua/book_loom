package ua.bookloom.ui.state;

import java.util.Objects;
import java.util.concurrent.Executor;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.ReadOnlyIntegerProperty;
import javafx.beans.property.ReadOnlyIntegerWrapper;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.Result;
import ua.bookloom.api.pipeline.ReviewCounts;
import ua.bookloom.api.pipeline.ReviewDesk;

/**
 * How many segments of the open project no run has decided, read from the review desk off the FX thread and published
 * as read-only properties.
 *
 * <p>A failed read reports none, so a screen never offers a start on a count the desk did not give; the desk logged the
 * failure where it built the error.
 */
@Slf4j
final class PendingCount {

    private final ReviewDesk desk;
    private final Executor executor;
    private final ReadOnlyIntegerWrapper count = new ReadOnlyIntegerWrapper();
    private final ReadOnlyBooleanWrapper remains = new ReadOnlyBooleanWrapper();

    PendingCount(final ReviewDesk desk, final Executor executor) {
        this.desk = Objects.requireNonNull(desk, "desk");
        this.executor = Objects.requireNonNull(executor, "executor");
    }

    ReadOnlyIntegerProperty count() {
        return count.getReadOnlyProperty();
    }

    ReadOnlyBooleanProperty remains() {
        return remains.getReadOnlyProperty();
    }

    /** Reads the count for {@code projectId} in the background and publishes it on the FX thread. */
    void refresh(final String projectId) {
        Objects.requireNonNull(projectId, "projectId");
        log.debug("reading the pending count of project {}", projectId);
        executor.execute(() -> read(projectId));
    }

    /** Forgets the count, as when no book is open. */
    void clear() {
        log.debug("no project open: the pending count is cleared");
        count.set(0);
        remains.set(false);
    }

    private void read(final String projectId) {
        final Result<ReviewCounts> counts = desk.counts(projectId);
        final ReviewCounts data = counts.data();
        final int pending = data == null ? 0 : data.pending();
        log.debug("project {} has {} pending segments (read succeeded: {})", projectId, pending, data != null);
        Platform.runLater(() -> {
            count.set(pending);
            remains.set(pending > 0);
        });
    }
}
