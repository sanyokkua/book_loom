package ua.bookloom.app;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import javafx.scene.control.Button;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testfx.util.WaitForAsyncUtils;
import ua.bookloom.api.pipeline.ProjectService;
import ua.bookloom.api.pipeline.ProjectSummary;
import ua.bookloom.ui.state.CurrentProject;

/**
 * Cancel on the Import screen in the real window over the SQLite file: the project the import just stored goes with
 * it, so cancelled imports leave nothing behind in Projects, while a project that was stored before stays.
 */
// `slow`: imports in the real window.
@Tag("slow")
class ImportCancelDiscardTest extends WorkspaceTestBase {

    private static final String TEXT = "It was a dark night.\n\nThe rain fell on the old house.\n";

    @Test
    void cancel_onTheVerdictOfAJustImportedBook_removesItsProject() throws Exception {
        startStoredWindow();
        openBook(book("Night.txt"), 2);
        assertThat(stored()).hasSize(1);

        pressCancel();

        WaitForAsyncUtils.waitFor(WAIT_SECONDS, TimeUnit.SECONDS, () -> stored().isEmpty());
        assertThat(onFx(() -> injector.getInstance(CurrentProject.class).book().get()))
                .isNull();
    }

    @Test
    void cancel_afterAStoredProjectWasOpenedAgain_keepsIt() throws Exception {
        startStoredWindow();
        openBook(book("Night.txt"), 2);
        startStoredWindow();
        final String id = stored().get(0).project().id();
        onFx(() -> injector.getInstance(ua.bookloom.ui.state.ProjectsViewModel.class)
                .refresh());
        openBook(book("Night.txt"), 2);

        pressCancel();
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(stored()).extracting(summary -> summary.project().id()).containsExactly(id);
    }

    private Path book(final String name) throws Exception {
        final Path file = booksDir.resolve(name);
        return Files.exists(file) ? file : Files.writeString(file, TEXT, StandardCharsets.UTF_8);
    }

    private void pressCancel() throws Exception {
        waitUntil(() -> shell.root().lookup("#import-cancel") != null);
        onFx(() -> ((Button) Objects.requireNonNull(shell.root().lookup("#import-cancel"))).fire());
        WaitForAsyncUtils.waitForFxEvents();
    }

    private List<ProjectSummary> stored() {
        return Objects.requireNonNull(
                injector.getInstance(ProjectService.class).list().data());
    }
}
