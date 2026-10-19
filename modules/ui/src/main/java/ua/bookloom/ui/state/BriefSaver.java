package ua.bookloom.ui.state;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppError;
import ua.bookloom.api.Result;
import ua.bookloom.api.pipeline.ProjectService;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.api.project.Project;

/**
 * Saves the brief through the project service on the background executor, one save at a time and in order.
 *
 * <p>The pool runs tasks on several threads, so two saves sent together could finish in either order and leave the
 * project holding the older brief. A brief handed in while a save is in flight therefore waits, and only the newest
 * waiting one is sent, since it holds every earlier change. Touched on the FX thread only.
 */
@Slf4j
final class BriefSaver {

    private record Save(String projectId, BookBrief brief) {}

    private final ProjectService projects;
    private final ExecutorService executor;
    private final ReadOnlyBooleanWrapper saving = new ReadOnlyBooleanWrapper(false);
    private @Nullable Save waiting;

    BriefSaver(final ProjectService projects, final ExecutorService executor) {
        this.projects = projects;
        this.executor = executor;
    }

    ReadOnlyBooleanProperty saving() {
        return saving.getReadOnlyProperty();
    }

    void enqueue(final String projectId, final BookBrief brief) {
        final Save save = new Save(projectId, brief);
        if (saving.get()) {
            log.debug("a save is in flight; the newest brief waits");
            waiting = save;
            return;
        }
        send(save);
    }

    private void send(final Save save) {
        saving.set(true);
        log.debug("saving the brief of project {}", save.projectId());
        try {
            executor.execute(() -> saveOffThread(save));
        } catch (RejectedExecutionException rejected) {
            log.warn("the brief of project {} could not be saved", save.projectId(), rejected);
            saving.set(false);
        }
    }

    private void saveOffThread(final Save save) {
        try {
            logResult(projects.updateBrief(save.projectId(), save.brief()));
        } catch (RuntimeException thrown) {
            log.error("saving the brief of project {} threw instead of returning a result", save.projectId(), thrown);
        } finally {
            Platform.runLater(this::saved);
        }
    }

    private static void logResult(final Result<Project> saved) {
        final AppError error = saved.error();
        if (error == null) {
            log.debug("the brief was saved");
        } else {
            log.warn("the brief was not saved: code {}", error.code());
        }
    }

    private void saved() {
        final Save next = waiting;
        waiting = null;
        if (next == null) {
            saving.set(false);
            return;
        }
        log.debug("sending the brief that waited");
        send(next);
    }
}
