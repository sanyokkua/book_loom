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
import org.testfx.framework.junit5.ApplicationTest;
import org.testfx.util.WaitForAsyncUtils;
import ua.bookloom.ui.RecordingErrorPresenter;
import ua.bookloom.ui.RecordingToasts;
import ua.bookloom.ui.ScriptedDocumentPort;

/**
 * What the import view model tests share: the hand-written fakes for the document port, the toasts and the error
 * presenter, an executor that either runs on the calling thread or on one real background thread, and the helpers that
 * drive an open and read the view model on the FX thread. The FX toolkit runs so that publishing back to the FX thread
 * is the real thing.
 */
abstract class ImportViewModelTestBase extends ApplicationTest {

    static final long WAIT_SECONDS = 10;

    ScriptedDocumentPort port;
    RecordingToasts toasts;
    RecordingErrorPresenter errors;
    ExecutorService executor;

    @Override
    public final void start(final Stage stage) {
        // The toolkit is all these tests need; the view model has no scene.
    }

    @BeforeEach
    final void setUpFakes() {
        port = ScriptedDocumentPort.idle();
        toasts = new RecordingToasts();
        errors = new RecordingErrorPresenter();
        executor = new DirectExecutor();
    }

    @AfterEach
    final void tearDownExecutor() {
        executor.shutdownNow();
    }

    /** From now on the port is called on one real daemon thread instead of the calling thread. */
    void useBackgroundThread() {
        executor = Executors.newSingleThreadExecutor(task -> {
            final Thread thread = new Thread(task, "import-test-background");
            thread.setDaemon(true);
            return thread;
        });
    }

    /** A view model over whatever {@link #port} and {@link #executor} the test set up before calling this. */
    ImportViewModel viewModel() {
        return onFx(() -> new ImportViewModel(port, toasts, errors, executor));
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

    static @Nullable OpenedBook openedBookOf(final ImportViewModel viewModel) {
        return onFx(() -> viewModel.openedBook().get());
    }

    static boolean isOpening(final ImportViewModel viewModel) {
        return onFx(() -> viewModel.opening().get());
    }
}
