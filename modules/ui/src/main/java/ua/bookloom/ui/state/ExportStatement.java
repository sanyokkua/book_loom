package ua.bookloom.ui.state;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.persistence.ProjectRepository;
import ua.bookloom.api.pipeline.ReviewCounts;
import ua.bookloom.api.pipeline.ReviewDesk;
import ua.bookloom.api.project.ExportRecord;
import ua.bookloom.ui.i18n.Messages;

/**
 * The lines stating what a book written now would contain, and the figures the tiles and banners read: the review
 * desk's counts and the project's last written export, read off the FX thread.
 *
 * <p>Each read bumps a counter and an answer is published only if the counter is unchanged, so a slow answer for an
 * earlier question never overwrites a newer one. The list and the figures are touched on the FX thread only.
 */
@Slf4j
final class ExportStatement {

    private final ReviewDesk desk;
    private final ProjectRepository projects;
    private final Messages messages;
    private final ExecutorService executor;
    private final ObservableList<String> lines = FXCollections.observableArrayList();
    private final ObjectProperty<@Nullable ExportFigures> figures = new SimpleObjectProperty<>();
    private long epoch;

    ExportStatement(
            final ReviewDesk desk,
            final ProjectRepository projects,
            final Messages messages,
            final ExecutorService executor) {
        this.desk = Objects.requireNonNull(desk, "desk");
        this.projects = Objects.requireNonNull(projects, "projects");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.executor = Objects.requireNonNull(executor, "executor");
    }

    ObservableList<String> lines() {
        return lines;
    }

    /** The counts and the last written export, or null while they are not known. */
    ObjectProperty<@Nullable ExportFigures> figures() {
        return figures;
    }

    /** Asks the desk and the store about {@code projectId}; a null project clears the statement at once. */
    void refresh(final @Nullable String projectId) {
        epoch++;
        if (projectId == null) {
            log.debug("statement cleared: no book is open");
            lines.clear();
            figures.set(null);
            return;
        }
        final long asked = epoch;
        log.debug("export counts asked for project {} as question {}", projectId, asked);
        try {
            executor.execute(() -> read(projectId, asked));
        } catch (RejectedExecutionException rejected) {
            log.warn("the export counts could not be asked; the statement keeps its last lines", rejected);
        }
    }

    /** Shows an export just written as the last one at once, before the store is read again. FX thread only. */
    void written(final ExportRecord record) {
        final ExportFigures now = figures.get();
        if (now != null) {
            log.debug("the tiles read the export just written: {} written", record.written());
            figures.set(new ExportFigures(now.counts(), record));
        }
    }

    private void read(final String projectId, final long asked) {
        final ReviewCounts counts = desk.counts(projectId).data();
        final ExportRecord last = lastExport(projectId);
        if (counts == null) {
            log.debug("export counts unavailable for project {}", projectId);
            return;
        }
        log.debug(
                "export figures for project {}: total {}, last export stored {}",
                projectId,
                counts.total(),
                last != null);
        final List<String> worded = PartialExportStatement.lines(counts, messages);
        final ExportFigures read = new ExportFigures(counts, last);
        Platform.runLater(() -> publish(asked, worded, read));
    }

    private @Nullable ExportRecord lastExport(final String projectId) {
        final Optional<ExportRecord> stored = projects.lastExport(projectId).data();
        return stored == null ? null : stored.orElse(null);
    }

    private void publish(final long asked, final List<String> worded, final ExportFigures read) {
        if (asked != epoch) {
            log.debug("statement {} discarded: the current one is {}", asked, epoch);
            return;
        }
        lines.setAll(worded);
        figures.set(read);
    }
}
