package ua.bookloom.ui.state;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.pipeline.ReviewCounts;
import ua.bookloom.api.pipeline.ReviewDesk;
import ua.bookloom.ui.i18n.Messages;

/**
 * The lines stating what a book written now would contain, read from the review desk off the FX thread.
 *
 * <p>Each read bumps a counter and an answer is published only if the counter is unchanged, so a slow answer for an
 * earlier question never overwrites a newer one. The list is touched on the FX thread only.
 */
@Slf4j
final class ExportStatement {

    private final ReviewDesk desk;
    private final Messages messages;
    private final ExecutorService executor;
    private final ObservableList<String> lines = FXCollections.observableArrayList();
    private long epoch;

    ExportStatement(final ReviewDesk desk, final Messages messages, final ExecutorService executor) {
        this.desk = Objects.requireNonNull(desk, "desk");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.executor = Objects.requireNonNull(executor, "executor");
    }

    ObservableList<String> lines() {
        return lines;
    }

    /** Asks the desk for the counts of {@code projectId}; a null project clears the statement at once. */
    void refresh(final @Nullable String projectId) {
        epoch++;
        if (projectId == null) {
            log.debug("statement cleared: no book is open");
            lines.clear();
            return;
        }
        final long asked = epoch;
        log.debug("partial-export counts asked for project {} as question {}", projectId, asked);
        try {
            executor.execute(() -> read(projectId, asked));
        } catch (RejectedExecutionException rejected) {
            log.warn("the partial-export counts could not be asked; the statement keeps its last lines", rejected);
        }
    }

    private void read(final String projectId, final long asked) {
        final ReviewCounts counts = desk.counts(projectId).data();
        if (counts == null) {
            log.debug("partial-export counts unavailable for project {}", projectId);
            return;
        }
        final List<String> worded = PartialExportStatement.lines(counts, messages);
        Platform.runLater(() -> publish(asked, worded));
    }

    private void publish(final long asked, final List<String> worded) {
        if (asked != epoch) {
            log.debug("statement {} discarded: the current one is {}", asked, epoch);
            return;
        }
        lines.setAll(worded);
    }
}
