package ua.bookloom.app;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.List;
import java.util.Objects;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import ua.bookloom.api.persistence.ProjectRepository;
import ua.bookloom.ui.ViewNames;
import ua.bookloom.ui.state.BookGroup;
import ua.bookloom.ui.state.CurrentProject;
import ua.bookloom.ui.state.ProjectRow;
import ua.bookloom.ui.state.ProjectsState;
import ua.bookloom.ui.state.ProjectsViewModel;
import ua.bookloom.ui.state.RunState;
import ua.bookloom.ui.state.StateMirror;

/**
 * Removing the project whose translation is at work, in the real window over the SQLite file: the question says the
 * translation stops first, the run ends before anything is deleted, and no stored write of the run fails afterwards.
 */
// `slow`: an import and a run in the real window.
@Tag("slow")
class ProjectRemoveRunningTest extends WorkspaceTestBase {

    private final ListAppender<ILoggingEvent> errors = new ListAppender<>();

    @AfterEach
    void detach() {
        root().detachAppender(errors);
    }

    @Test
    void remove_projectWhoseRunIsPaused_asksToStopFirstThenRemovesWithoutAFailedWrite() throws Exception {
        final String id = pausedRun(new com.google.inject.AbstractModule() {});
        errors.start();
        root().addAppender(errors);

        removeFromProjects(id);
        waitUntil(() -> shell.root().lookup("#confirm-yes") != null);
        final String question =
                onFx(() -> ((Label) Objects.requireNonNull(shell.root().lookup(".dialog-title"))).getText());
        assertThat(question).startsWith("Stop the translation and remove");
        assertThat(runState()).as("nothing changes before the answer").isEqualTo(RunState.PAUSED);
        onFx(() -> ((Button) Objects.requireNonNull(shell.root().lookup("#confirm-yes"))).fire());

        org.testfx.util.WaitForAsyncUtils.waitFor(
                WAIT_SECONDS,
                java.util.concurrent.TimeUnit.SECONDS,
                () -> Objects.requireNonNull(injector.getInstance(ProjectRepository.class)
                                .find(id)
                                .data())
                        .isEmpty());
        waitUntil(() -> injector.getInstance(StateMirror.class).runState().get().isIdleOrEnded());
        org.testfx.util.WaitForAsyncUtils.waitForFxEvents();
        assertThat(errors.list)
                .as("no write of the run fails once its project is gone")
                .filteredOn(event -> event.getLevel().isGreaterOrEqual(Level.ERROR))
                .isEmpty();
        assertThat(onFx(() -> injector.getInstance(CurrentProject.class).book().get()))
                .isNull();
    }

    private void removeFromProjects(final String id) throws Exception {
        final ProjectsViewModel projects = injector.getInstance(ProjectsViewModel.class);
        onFx(() -> shell.activate(ViewNames.PROJECTS));
        onFx(projects::refresh);
        waitUntil(() -> projects.state().get() == ProjectsState.LIST);
        final ProjectRow row = onFx(() -> projects.groups().get().stream()
                .map(BookGroup::rows)
                .flatMap(List::stream)
                .filter(candidate -> candidate.summary().project().id().equals(id))
                .findFirst()
                .orElseThrow());
        onFx(() -> projects.remove(row));
    }

    private static Logger root() {
        return (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
    }
}
