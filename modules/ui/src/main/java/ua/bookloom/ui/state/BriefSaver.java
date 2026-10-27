package ua.bookloom.ui.state;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.UnaryOperator;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppError;
import ua.bookloom.api.Result;
import ua.bookloom.api.pipeline.ProjectChange;
import ua.bookloom.api.pipeline.ProjectService;
import ua.bookloom.api.project.BookBrief;

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
    private final BriefSaveOutcomes outcomes;
    private final ReadOnlyBooleanWrapper saving = new ReadOnlyBooleanWrapper(false);
    private @Nullable Save waiting;

    BriefSaver(final ProjectService projects, final ExecutorService executor, final BriefSaveOutcomes outcomes) {
        this.projects = projects;
        this.executor = executor;
        this.outcomes = outcomes;
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
        @Nullable Result<ProjectChange> answer = null;
        try {
            answer = projects.updateBrief(save.projectId(), save.brief());
        } catch (RuntimeException thrown) {
            log.error("saving the brief of project {} threw instead of returning a result", save.projectId(), thrown);
        }
        final @Nullable Result<ProjectChange> answered = answer;
        Platform.runLater(() -> saved(save, answered));
    }

    // What the service answered, once, and the fix a refusal asks of the save that waited behind it.
    private UnaryOperator<BookBrief> logResult(final Save save, final @Nullable Result<ProjectChange> saved) {
        final @Nullable AppError error = saved == null ? null : saved.error();
        final @Nullable ProjectChange change = saved == null ? null : saved.data();
        if (saved == null) {
            return waiting -> waiting;
        }
        if (error != null) {
            log.warn("the brief was not saved: code {}", error.code());
        } else if (change instanceof ProjectChange.Saved) {
            log.debug("the brief was saved");
            outcomes.stored(save.projectId(), save.brief());
        } else if (change != null) {
            log.warn(
                    "the brief of project {} was refused: {}",
                    save.projectId(),
                    change.getClass().getSimpleName());
            return outcomes.refused(save.projectId(), save.brief(), change);
        }
        return waiting -> waiting;
    }

    private void saved(final Save save, final @Nullable Result<ProjectChange> answer) {
        final UnaryOperator<BookBrief> fix = logResult(save, answer);
        final Save next = waiting;
        waiting = null;
        if (next == null) {
            saving.set(false);
            return;
        }
        log.debug("sending the brief that waited");
        send(new Save(next.projectId(), fix.apply(next.brief())));
    }
}
