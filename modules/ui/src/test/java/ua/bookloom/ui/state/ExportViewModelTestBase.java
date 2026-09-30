package ua.bookloom.ui.state;

import static ua.bookloom.ui.ThemeTestSupport.onFx;

import java.nio.file.Path;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeEach;
import org.testfx.framework.junit5.ApplicationTest;
import org.testfx.util.WaitForAsyncUtils;
import ua.bookloom.api.Result;
import ua.bookloom.api.pipeline.ImportedBook;
import ua.bookloom.ui.RecordingErrorPresenter;
import ua.bookloom.ui.RecordingToasts;
import ua.bookloom.ui.ScriptedProjectService;

/**
 * What the export view model tests share: a real {@link ImportViewModel} over the scripted project service and a
 * calling-thread executor, through which books are opened for real, a real {@link BookBriefViewModel} that chooses the
 * target the destination follows, and helpers that read and drive the export view model on the FX thread. The existence check runs on a calling-thread
 * executor and its answer is published with {@code Platform.runLater}, so every driver waits for the FX queue to
 * drain. The toolkit runs so that the open's answer is published to the FX thread as it is in the application.
 */
abstract class ExportViewModelTestBase extends ApplicationTest {

    ScriptedProjectService projects;
    CurrentProject current;
    ImportViewModel imports;
    BookBriefViewModel brief;
    ExportViewModel exports;

    @Override
    public final void start(final Stage stage) {
        // The toolkit is all these tests need; the view model has no scene.
    }

    @BeforeEach
    final void setUpViewModels() {
        projects = new ScriptedProjectService();
        current = new CurrentProject();
        imports = onFx(() -> new ImportViewModel(
                projects, current, new RecordingToasts(), new RecordingErrorPresenter(), new DirectExecutor()));
        brief = onFx(() -> new BookBriefViewModel(current, projects, new DirectExecutor()));
        exports = onFx(() -> new ExportViewModel(current, new DirectExecutor()));
        WaitForAsyncUtils.waitForFxEvents();
    }

    /** Replaces the export view model with a new one over the same current project, as a later first visit would. */
    void recreateExports() {
        exports = onFx(() -> new ExportViewModel(current, new DirectExecutor()));
        WaitForAsyncUtils.waitForFxEvents();
    }

    /** Opens {@code imported} and chooses {@code uk} as the target, so that a destination is proposed for it. */
    void openBook(final Path source, final ImportedBook imported) {
        openBookOnly(source, imported);
        selectTarget("uk");
    }

    /** Opens {@code imported} as if it had been read from {@code source} and waits for the answer to be published. */
    void openBookOnly(final Path source, final ImportedBook imported) {
        projects.on(source, Result.ok(imported));
        onFx(() -> {
            imports.open(source);
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();
    }

    void selectTarget(final String code) {
        onFx(() -> {
            brief.setTargetLanguage(code);
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();
    }

    void editDestination(final String text) {
        onFx(() -> {
            exports.editDestination(text);
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();
    }

    void chooseDestination(final Path chosen) {
        onFx(() -> {
            exports.chooseDestination(chosen);
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();
    }

    void setOverwrite(final boolean overwrite) {
        onFx(() -> {
            exports.setOverwrite(overwrite);
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();
    }

    void refresh() {
        onFx(() -> {
            exports.refresh();
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();
    }

    String destination() {
        return onFx(() -> exports.destination().get());
    }

    String target() {
        return onFx(() -> String.valueOf(current.brief().get().targetLanguage()));
    }

    boolean overwrite() {
        return onFx(() -> exports.overwrite().get());
    }

    boolean destinationExists() {
        return onFx(() -> exports.destinationExists().get());
    }
}
