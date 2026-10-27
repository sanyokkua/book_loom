package ua.bookloom.ui.state;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.ReadOnlyIntegerProperty;
import javafx.beans.property.ReadOnlyIntegerWrapper;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.Result;
import ua.bookloom.api.pipeline.ProjectService;
import ua.bookloom.api.pipeline.ProjectSummary;
import ua.bookloom.api.project.NameOrigin;
import ua.bookloom.ui.BackgroundExecutor;
import ua.bookloom.ui.notify.ToastOccasion;
import ua.bookloom.ui.notify.Toasts;

/**
 * The open project as it is stored: the name it is shown by (the title bar and the Brief read the same one, never a
 * name worked out on a screen), whether that name follows the languages, and how many segments the book holds. It is
 * read from the project service when a book opens, after a saved language change and after a rename, because the
 * service is what names the project. A change of a name already shown is announced once. FX thread only.
 */
@Slf4j
@Singleton
public final class BriefProject {

    private final CurrentProject current;
    private final ProjectService projects;
    private final ProjectActions actions;
    private final Toasts toasts;
    private final OffThread calls;
    private final ReadOnlyStringWrapper name = new ReadOnlyStringWrapper("");
    private final ReadOnlyBooleanWrapper automatic = new ReadOnlyBooleanWrapper(true);
    private final ReadOnlyIntegerWrapper totalSegments = new ReadOnlyIntegerWrapper(0);
    private final ReadOnlyIntegerWrapper decidedSegments = new ReadOnlyIntegerWrapper(0);

    /**
     * Follows the open book.
     *
     * @param current the open project, whose id selects the stored one
     * @param projects the service the stored project is listed from
     * @param actions the rename question the Projects screen asks too
     * @param toasts where a changed name is announced
     * @param executor the daemon executor the service runs on, never the FX thread
     */
    @Inject
    public BriefProject(
            final CurrentProject current,
            final ProjectService projects,
            final ProjectActions actions,
            final Toasts toasts,
            @BackgroundExecutor final ExecutorService executor) {
        this.current = Objects.requireNonNull(current, "current");
        this.projects = Objects.requireNonNull(projects, "projects");
        this.actions = Objects.requireNonNull(actions, "actions");
        this.toasts = Objects.requireNonNull(toasts, "toasts");
        this.calls = new OffThread(Objects.requireNonNull(executor, "executor"));
        current.book().addListener((observed, was, now) -> opened(now));
        opened(current.book().get());
    }

    /** {@return the stored name of the open project; empty while it is not known} */
    public ReadOnlyStringProperty name() {
        return name.getReadOnlyProperty();
    }

    /** {@return whether the name follows the languages, {@code false} when the person typed it} */
    public ReadOnlyBooleanProperty isAutomatic() {
        return automatic.getReadOnlyProperty();
    }

    /** {@return the segments the book holds; zero while not known} */
    public ReadOnlyIntegerProperty totalSegments() {
        return totalSegments.getReadOnlyProperty();
    }

    /** {@return the segments the stored project has decided; zero while not known} */
    public ReadOnlyIntegerProperty decidedSegments() {
        return decidedSegments.getReadOnlyProperty();
    }

    private void opened(final @Nullable OpenedBook book) {
        log.debug("the brief's project is {}", book == null ? "none" : book.projectId());
        name.set("");
        automatic.set(true);
        totalSegments.set(0);
        decidedSegments.set(0);
        refresh();
    }

    /** Reads the stored project again; the answer arrives on the FX thread. */
    public void refresh() {
        final OpenedBook book = current.book().get();
        if (book == null) {
            return;
        }
        final String id = book.projectId();
        calls.call("read the stored name of the open project", projects::list, result -> show(id, result));
    }

    private void show(final String id, final Result<List<ProjectSummary>> result) {
        final OpenedBook book = current.book().get();
        final List<ProjectSummary> listed = result.data();
        if (book == null || !book.projectId().equals(id) || listed == null) {
            return;
        }
        listed.stream()
                .filter(summary -> summary.project().id().equals(id))
                .findFirst()
                .ifPresent(summary -> {
                    final String before = name.get();
                    final String now = summary.project().name();
                    name.set(now);
                    automatic.set(summary.project().nameOrigin() == NameOrigin.AUTO);
                    totalSegments.set(summary.totalSegments());
                    decidedSegments.set(summary.decidedSegments());
                    log.debug(
                            "the open project is named by {}; {} segments",
                            automatic.get() ? "the application" : "you",
                            summary.totalSegments());
                    log.trace("the open project's name is '{}'", now);
                    if (!before.isEmpty() && !before.equals(now)) {
                        toasts.raise(ToastOccasion.PROJECT_NAME_CHANGED, now);
                    }
                });
    }

    /** Asks for a name of the person's own, through the question the Projects screen uses. */
    public void rename() {
        final OpenedBook book = current.book().get();
        if (book == null) {
            return;
        }
        log.debug("rename of the open project {} asked from the brief", book.projectId());
        actions.renameOpen(book.projectId(), this::refresh);
    }
}
