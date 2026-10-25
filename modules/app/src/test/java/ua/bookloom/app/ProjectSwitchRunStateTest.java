package ua.bookloom.app;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import javafx.scene.Node;
import javafx.scene.control.Button;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.pipeline.ReviewMode;
import ua.bookloom.ui.ViewNames;
import ua.bookloom.ui.state.BookGroup;
import ua.bookloom.ui.state.CurrentProject;
import ua.bookloom.ui.state.ImportViewModel;
import ua.bookloom.ui.state.OpenedBook;
import ua.bookloom.ui.state.ProjectRow;
import ua.bookloom.ui.state.ProjectsState;
import ua.bookloom.ui.state.ProjectsViewModel;
import ua.bookloom.ui.state.RunRow;
import ua.bookloom.ui.state.RunState;
import ua.bookloom.ui.state.RunStateSource;
import ua.bookloom.ui.state.StateMirror;
import ua.bookloom.ui.state.TranslatingViewModel;

/**
 * Two stored projects in the real window: the run of one never shows on the other. A stopped run is left behind when
 * the other project opens from Projects; a run still at work is stopped first, after the person agrees, because one
 * translation exists per application run.
 */
// `slow`: two imports and a run in the real window; `test` and the gate run it, `fastTest` leaves it out.
@Tag("slow")
class ProjectSwitchRunStateTest extends WorkspaceTestBase {

    // Each paragraph is long enough for the pseudo model's capitals to be flagged, so Assisted pauses on the first.
    private static final String RUN_TEXT =
            "It was a dark night.\n\nThe rain fell on the old house.\n\nNobody came to the door.\n";
    private static final String OTHER_TEXT = """
            Gravity pulls the falling apple.

            The moon circles the earth.

            Tides follow the moon.

            Nothing escapes the pull.
            """;
    private static final int OTHER_SEGMENTS = 4;

    @Test
    void openFromProjects_otherProjectsRunStopped_showsItsOwnReadyState() throws Exception {
        final OpenedBook other = twoProjectsWithPausedRun();
        onFx(() -> injector.getInstance(TranslatingViewModel.class).stop());
        waitUntil(() -> injector.getInstance(StateMirror.class).runState().get() == RunState.STOPPED);

        openFromProjects(other);

        assertShowsItsOwnReadyState(other);
    }

    @Test
    void openFromProjects_otherProjectsRunPaused_asksThenStopsItAndShowsItsOwnState() throws Exception {
        final OpenedBook other = twoProjectsWithPausedRun();

        openFromProjects(other);
        waitUntil(() -> shell.root().lookup("#confirm-yes") != null);
        assertThat(runState()).as("nothing changes before the answer").isEqualTo(RunState.PAUSED);
        onFx(() -> ((Button) Objects.requireNonNull(shell.root().lookup("#confirm-yes"))).fire());

        assertShowsItsOwnReadyState(other);
    }

    @Test
    void openFromProjects_questionCancelled_keepsTheRunAndItsProject() throws Exception {
        final OpenedBook other = twoProjectsWithPausedRun();
        final String running = onFx(() -> openProjectId());

        openFromProjects(other);
        waitUntil(() -> shell.root().lookup("#confirm-yes") != null);
        onFx(() -> ((Button) Objects.requireNonNull(shell.root().lookup("#confirm-cancel"))).fire());

        assertThat(runState()).isEqualTo(RunState.PAUSED);
        assertThat(onFx(() -> openProjectId())).isEqualTo(running);
    }

    // The other project is imported and briefed first, then the run's project, whose run pauses on its first segment.
    private OpenedBook twoProjectsWithPausedRun() throws Exception {
        final Path otherBook = Files.writeString(booksDir.resolve("Gravity.txt"), OTHER_TEXT, StandardCharsets.UTF_8);
        final Path runBook = Files.writeString(booksDir.resolve("Curtain.txt"), RUN_TEXT, StandardCharsets.UTF_8);
        startWorkspace(ReviewMode.ASSISTED);
        openBook(otherBook, OTHER_SEGMENTS);
        chooseBrief("uk", booksDir.resolve("Gravity.uk.txt"));
        final OpenedBook other = Objects.requireNonNull(
                onFx(() -> injector.getInstance(CurrentProject.class).book().get()));
        onFx(() -> injector.getInstance(ImportViewModel.class).open(runBook));
        waitUntil(() -> !other.projectId().equals(openProjectId()));
        chooseBrief("uk", booksDir.resolve("Curtain.uk.txt"));
        chooseModel();
        onFx(() -> shell.activate(ViewNames.TRANSLATING));
        startRun();
        waitUntil(() -> injector.getInstance(StateMirror.class).runState().get() == RunState.PAUSED);
        return other;
    }

    private void openFromProjects(final OpenedBook other) throws Exception {
        final ProjectsViewModel projects = injector.getInstance(ProjectsViewModel.class);
        onFx(projects::refresh);
        waitUntil(() -> projects.state().get() == ProjectsState.LIST);
        final ProjectRow row = onFx(() -> projects.groups().get().stream()
                .map(BookGroup::rows)
                .flatMap(java.util.List::stream)
                .filter(candidate -> candidate.summary().project().id().equals(other.projectId()))
                .findFirst()
                .orElseThrow());
        onFx(() -> projects.open(row));
    }

    private void assertShowsItsOwnReadyState(final OpenedBook other) throws Exception {
        final RunStateSource runs = injector.getInstance(RunStateSource.class);
        waitUntil(() -> other.projectId().equals(openProjectId())
                && injector.getInstance(StateMirror.class).runState().get() == RunState.IDLE
                && runs.totalSegments().get() == OTHER_SEGMENTS);
        onFx(() -> shell.activate(ViewNames.TRANSLATING));
        assertThat(onFx(() -> runs.view().get().row())).isEqualTo(RunRow.READY);
        assertThat(onFx(() -> runs.decidedSegments().get())).isZero();
        assertThat(onFx(() ->
                        injector.getInstance(StateMirror.class).runFileName().get()))
                .isNull();
        assertThat(onFx(() -> injector.getInstance(TranslatingViewModel.class)
                        .controls()
                        .get()
                        .start()
                        .isEnabled()))
                .isTrue();
        final Node ready = onFx(() -> shell.root().lookup("#translating-ready-card"));
        assertThat(ready).as("the ready card, not the other run's live rows").isNotNull();
        assertThat(onFx(ready::isVisible)).isTrue();
        assertThat(onFx(() ->
                        ((javafx.scene.control.Label) shell.root().lookup("#translating-ready-pending")).getText()))
                .isEqualTo(String.valueOf(OTHER_SEGMENTS));
    }

    private String openProjectId() {
        final OpenedBook open =
                injector.getInstance(CurrentProject.class).book().get();
        return open == null ? "" : open.projectId();
    }
}
