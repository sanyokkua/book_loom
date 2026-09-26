package ua.bookloom.ui.state;

import static ua.bookloom.ui.ThemeTestSupport.onFx;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.testfx.framework.junit5.ApplicationTest;
import org.testfx.util.WaitForAsyncUtils;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.StageOutcome;
import ua.bookloom.api.llm.StageStatus;
import ua.bookloom.api.llm.VerificationReport;
import ua.bookloom.api.llm.VerificationStage;
import ua.bookloom.ui.FakeProviderConfigs;
import ua.bookloom.ui.RecordingErrorPresenter;
import ua.bookloom.ui.RecordingToasts;
import ua.bookloom.ui.ScriptedModelCatalog;
import ua.bookloom.ui.ScriptedProviderVerifier;

/**
 * What the settings view model tests share: the hand-written fakes for the registry, the verifier, the toasts and the
 * error presenter, and the helpers that drive a check. The FX toolkit runs so that publishing back to the FX thread is
 * the real thing.
 */
abstract class SettingsViewModelTestBase extends ApplicationTest {

    static final long WAIT_SECONDS = 10;
    static final String MODEL = "gemma3:12b";

    ScriptedModelCatalog catalog;
    RecordingToasts toasts;
    RecordingErrorPresenter errors;
    ExecutorService executor;

    @Override
    public final void start(final Stage stage) {
        // The toolkit is all these tests need; the view model has no scene.
    }

    @BeforeEach
    final void setUpFakes() {
        catalog = ScriptedModelCatalog.idle();
        toasts = new RecordingToasts();
        errors = new RecordingErrorPresenter();
        executor = new DirectExecutor();
    }

    @AfterEach
    final void tearDownExecutor() {
        executor.shutdownNow();
    }

    /** A two-thread pool that counts finished tasks; a task finishes after its report was handed to the FX thread. */
    static final class CountingPool extends ThreadPoolExecutor {

        private final Semaphore finished = new Semaphore(0);

        CountingPool() {
            super(2, 2, 1, TimeUnit.MINUTES, new LinkedBlockingQueue<>(), task -> {
                final Thread thread = new Thread(task, "settings-check-test");
                thread.setDaemon(true);
                return thread;
            });
        }

        @Override
        protected void afterExecute(final Runnable task, final Throwable thrown) {
            finished.release();
        }

        void awaitFinished(final int count) throws InterruptedException {
            if (!finished.tryAcquire(count, WAIT_SECONDS, TimeUnit.SECONDS)) {
                throw new IllegalStateException("fewer than " + count + " check(s) finished");
            }
        }
    }

    CountingPool useCountingPool() {
        final CountingPool pool = new CountingPool();
        executor = pool;
        return pool;
    }

    void startCheck(final SettingsViewModel viewModel) {
        onFx(() -> {
            viewModel.model().set(MODEL);
            viewModel.check();
            return null;
        });
    }

    SettingsViewModel viewModel(final ScriptedProviderVerifier verifier) {
        return onFx(() -> new SettingsViewModel(
                new FakeProviderConfigs(), verifier, newModelListing(), toasts, errors, executor));
    }

    /** A listing over whatever {@link #catalog} the test scripted before calling this. */
    ModelListing newModelListing() {
        return onFx(() -> new ModelListing(catalog, toasts, errors, executor));
    }

    /** A view model over the idle verifier and whatever {@link #catalog} the test scripted before calling this. */
    SettingsViewModel viewModel() {
        return viewModel(ScriptedProviderVerifier.idle());
    }

    /** Asks for the selected provider's model list on the FX thread and waits until the answer has been published. */
    void refreshAndAwait(final SettingsViewModel viewModel) {
        onFx(() -> {
            viewModel.refreshModels();
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();
    }

    static List<String> offered(final SettingsViewModel viewModel) {
        return offered(viewModel.modelListing());
    }

    static List<String> offered(final ModelListing listing) {
        return onFx(() -> List.copyOf(listing.offered()));
    }

    void checkWithModel(final SettingsViewModel viewModel) {
        onFx(() -> {
            viewModel.model().set(MODEL);
            viewModel.check();
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();
    }

    static StageOutcome outcome(final VerificationStage stage, final StageStatus status) {
        return new StageOutcome(stage, status, null, null);
    }

    static Result<VerificationReport> report(final StageOutcome... outcomes) {
        return Result.ok(new VerificationReport(List.of(outcomes)));
    }

    static List<StageChip> stagesOf(final SettingsViewModel viewModel) {
        return onFx(() -> List.copyOf(viewModel.stages()));
    }

    static Result<VerificationReport> allPassed() {
        return report(
                outcome(VerificationStage.CONNECTION, StageStatus.PASSED),
                outcome(VerificationStage.MODELS, StageStatus.PASSED),
                outcome(VerificationStage.INFERENCE, StageStatus.PASSED));
    }
}
