package ua.bookloom.ui.state;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Consumer;
import javafx.application.Platform;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.BookFormat;
import ua.bookloom.api.pipeline.ProjectService;
import ua.bookloom.api.pipeline.ProjectSummary;
import ua.bookloom.api.project.Project;
import ua.bookloom.util.paths.DestinationPath;
import ua.bookloom.util.paths.ProjectFileLabel;

/**
 * Proposes where a book is written: the command line's name at once, then the same name with the label a named project
 * needs once the stored projects have been read. Reading them is I/O, so it runs on the background executor and the
 * improved proposal is delivered on the FX thread. Touched on the FX thread only.
 */
@Slf4j
final class ExportProposals {

    private final ProjectService projects;
    private final ExecutorService executor;
    // The label last found and the project it was found for.
    private @Nullable String label;
    private @Nullable String labelledProject;

    ExportProposals(final ProjectService projects, final ExecutorService executor) {
        this.projects = Objects.requireNonNull(projects, "projects");
        this.executor = Objects.requireNonNull(executor, "executor");
    }

    /**
     * The proposal for a book, with the label already found for it, if any.
     *
     * @param book the open book
     * @param target the chosen target language
     * @return the destination path next to the source
     */
    Path proposal(final OpenedBook book, final String target) {
        final BookFormat format = Objects.requireNonNull(book.inspection().format(), "format");
        final String known = book.projectId().equals(labelledProject) ? label : null;
        final Path proposed = DestinationPath.destinationFor(book.source(), format, target, known);
        log.debug("proposing destination {} for project {} ({})", proposed, book.projectId(), format);
        return proposed;
    }

    /**
     * Looks the label up and delivers the proposal made with it later.
     *
     * @param book the open book
     * @param target the chosen target language
     * @param delivered receives the improved proposal on the FX thread
     */
    void load(final OpenedBook book, final String target, final Consumer<Path> delivered) {
        final String projectId = book.projectId();
        log.debug("looking for the file label of project {}", projectId);
        try {
            executor.execute(() -> {
                final String found = labelOf(projectId);
                Platform.runLater(() -> {
                    label = found;
                    labelledProject = projectId;
                    delivered.accept(proposal(book, target));
                });
            });
        } catch (RejectedExecutionException rejected) {
            log.warn("the file label could not be looked up", rejected);
        }
    }

    private @Nullable String labelOf(final String projectId) {
        final Result<List<ProjectSummary>> listed = projects.list();
        final List<ProjectSummary> summaries = listed.data();
        if (summaries == null) {
            log.debug("no file label: the stored projects could not be listed");
            return null;
        }
        final List<Project> all =
                summaries.stream().map(ProjectSummary::project).toList();
        return all.stream()
                .filter(project -> project.id().equals(projectId))
                .findFirst()
                .flatMap(self -> ProjectFileLabel.of(self, all))
                .orElse(null);
    }
}
