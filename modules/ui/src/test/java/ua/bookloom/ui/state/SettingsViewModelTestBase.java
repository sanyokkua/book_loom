package ua.bookloom.ui.state;

import static ua.bookloom.ui.ThemeTestSupport.onFx;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import javafx.stage.Stage;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.testfx.util.WaitForAsyncUtils;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.StageOutcome;
import ua.bookloom.api.llm.StageStatus;
import ua.bookloom.api.llm.VerificationReport;
import ua.bookloom.api.llm.VerificationStage;
import ua.bookloom.ui.EnglishMessages;
import ua.bookloom.ui.FakeProviderConfigs;
import ua.bookloom.ui.FxTestBase;
import ua.bookloom.ui.MemorySettings;
import ua.bookloom.ui.RecordingErrorPresenter;
import ua.bookloom.ui.RecordingToasts;
import ua.bookloom.ui.ScriptedModelCatalog;
import ua.bookloom.ui.ScriptedModelSettings;
import ua.bookloom.ui.ScriptedProviderVerifier;

/**
 * What the settings view model tests share: the hand-written fakes for the registry, the verifier, the toasts and the
 * error presenter, and the helpers that drive a check. The FX toolkit runs so that publishing back to the FX thread is
 * the real thing.
 */
abstract class SettingsViewModelTestBase extends FxTestBase {

    static final long WAIT_SECONDS = 10;
    static final String MODEL = "gemma3:12b";

    ScriptedModelCatalog catalog;
    RecordingToasts toasts;
    RecordingErrorPresenter errors;
    ExecutorService executor;
    final FakeProviderConfigs configs = new FakeProviderConfigs();
    final MemorySettings settings = new MemorySettings();
    final ActivityTracker activities = new ActivityTracker(new StateMirror());
    final ScriptedModelSettings modelSettings = new ScriptedModelSettings();
    final ModelSpeeds speeds = new ModelSpeeds();
    final MemorySettings detailSettings = new MemorySettings();
    final TechnicalDetails technical = new TechnicalDetails(detailSettings, Runnable::run, Runnable::run);
    private @Nullable SettingsViewModel formOwner;
    private @Nullable ProviderForm formOfOwner;

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
            viewModel.draftModel().set(MODEL);
            viewModel.tests().run(ProviderTest.INFERENCE);
            return null;
        });
    }

    SettingsViewModel viewModel(final ScriptedProviderVerifier verifier) {
        return viewModel(verifier, new StructuredAnswers());
    }

    SettingsViewModel viewModel(final ScriptedProviderVerifier verifier, final StructuredAnswers structured) {
        final SettingsViewModel built = onFx(() -> new SettingsViewModel(
                configs,
                verifier,
                newModelListing(),
                errors,
                executor,
                activities,
                settings,
                EnglishMessages.create(),
                new ModelSpeedRecorder(modelSettings, speeds, executor),
                structured));
        awaitProvidersLoaded();
        form(built);
        return built;
    }

    /** The one Providers form over a view model; the view model's test subject follows it, as in the window. */
    ProviderForm form(final SettingsViewModel viewModel) {
        ProviderForm form = formOfOwner;
        if (formOwner != viewModel || form == null) {
            form = onFx(() -> new ProviderForm(
                    viewModel,
                    configs,
                    modelSettings,
                    settings,
                    name -> "OFFICE_KEY".equals(name),
                    technical,
                    executor));
            formOwner = viewModel;
            formOfOwner = form;
        }
        return form;
    }

    /** The provider read is the first task the pool runs; wait for it so a test counts only its own checks. */
    private void awaitProvidersLoaded() {
        try {
            awaitLoadTask();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(interrupted);
        }
        WaitForAsyncUtils.waitForFxEvents();
    }

    private void awaitLoadTask() throws InterruptedException {
        if (executor instanceof CountingPool pool) {
            pool.awaitFinished(1);
        }
    }

    /** A listing over whatever {@link #catalog} the test scripted before calling this. */
    ModelListing newModelListing() {
        return onFx(() -> new ModelListing(catalog, toasts, errors, executor, activities));
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
            viewModel.draftModel().set(MODEL);
            viewModel.tests().run(ProviderTest.INFERENCE);
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
        return onFx(() -> List.copyOf(viewModel.tests().stages()));
    }

    static Result<VerificationReport> allPassed() {
        return report(
                outcome(VerificationStage.CONNECTION, StageStatus.PASSED),
                outcome(VerificationStage.MODELS, StageStatus.PASSED),
                outcome(VerificationStage.INFERENCE, StageStatus.PASSED));
    }
}
