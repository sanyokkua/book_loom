package ua.bookloom.ui.state;

import static ua.bookloom.ui.ThemeTestSupport.onFx;

import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javafx.stage.Stage;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.testfx.util.WaitForAsyncUtils;
import ua.bookloom.ui.FxTestBase;
import ua.bookloom.ui.RecordingErrorPresenter;
import ua.bookloom.ui.RecordingToasts;
import ua.bookloom.ui.ScriptedProjectService;

/**
 * What the import view model tests share: the hand-written fakes for the project service, the toasts and the error
 * presenter, an executor that either runs on the calling thread or on one real background thread, and the helpers that
 * drive an open and read the view model on the FX thread. The FX toolkit runs so that publishing back to the FX thread
 * is the real thing.
 */
abstract class ImportViewModelTestBase extends FxTestBase {

    static final long WAIT_SECONDS = 10;

    ScriptedProjectService projects;
    CurrentProject current;
    RecordingToasts toasts;
    RecordingErrorPresenter errors;
    ExecutorService executor;
    ActivityTracker activities;

    @Override
    public final void start(final Stage stage) {
        // The toolkit is all these tests need; the view model has no scene.
    }

    @BeforeEach
    final void setUpFakes() {
        projects = new ScriptedProjectService();
        current = new CurrentProject();
        toasts = new RecordingToasts();
        errors = new RecordingErrorPresenter();
        executor = new DirectExecutor();
        activities = onFx(() -> new ActivityTracker(new StateMirror()));
    }

    @AfterEach
    final void tearDownExecutor() {
        executor.shutdownNow();
    }

    /** From now on the service is called on one real daemon thread instead of the calling thread. */
    void useBackgroundThread() {
        executor = Executors.newSingleThreadExecutor(task -> {
            final Thread thread = new Thread(task, "import-test-background");
            thread.setDaemon(true);
            return thread;
        });
    }

    /** A view model over whatever {@link #projects} and {@link #executor} the test set up before calling this. */
    ImportViewModel viewModel() {
        return onFx(() -> new ImportViewModel(projects, current, toasts, errors, executor, activities));
    }

    /** Opens {@code source} on the FX thread and waits until everything published back to it has run. */
    void open(final ImportViewModel viewModel, final Path source) {
        onFx(() -> {
            viewModel.open(source);
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();
    }

    /** Waits, polling on the FX thread, until the view model has left the opening state. */
    void awaitAnswered(final ImportViewModel viewModel) throws TimeoutException {
        WaitForAsyncUtils.waitFor(
                WAIT_SECONDS,
                TimeUnit.SECONDS,
                () -> !onFx(() -> viewModel.opening().get()));
        WaitForAsyncUtils.waitForFxEvents();
    }

    static ImportState stateOf(final ImportViewModel viewModel) {
        return onFx(() -> viewModel.state().get());
    }

    @Nullable
    OpenedBook openedBook() {
        return onFx(() -> current.book().get());
    }

    static boolean isOpening(final ImportViewModel viewModel) {
        return onFx(() -> viewModel.opening().get());
    }
}
