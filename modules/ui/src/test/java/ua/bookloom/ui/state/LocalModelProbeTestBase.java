package ua.bookloom.ui.state;

import static ua.bookloom.ui.ThemeTestSupport.onFx;

import java.util.Optional;
import java.util.concurrent.ExecutorService;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.testfx.util.WaitForAsyncUtils;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ModelSelection;
import ua.bookloom.api.persistence.SettingsRepository;
import ua.bookloom.api.settings.AppSetting;
import ua.bookloom.ui.EnglishMessages;
import ua.bookloom.ui.FxTestBase;
import ua.bookloom.ui.MemorySettings;
import ua.bookloom.ui.RecordingErrorPresenter;
import ua.bookloom.ui.RecordingModelCatalog;
import ua.bookloom.ui.RecordingToasts;
import ua.bookloom.ui.ScriptedModelCatalog;
import ua.bookloom.ui.ScriptedModelSettings;
import ua.bookloom.ui.ScriptedProviderVerifier;
import ua.bookloom.ui.dialog.RecordingModelPickPrompt;

/**
 * The fixtures of the first-launch look tests. The catalogue, the stores, the question and the view model are
 * hand-written or real; the look runs on a calling-thread executor unless a test needs it in flight.
 */
@SuppressWarnings("NullAway.Init")
abstract class LocalModelProbeTestBase extends FxTestBase {

    static final String OLLAMA = "http://127.0.0.1:11434";
    static final String LM_STUDIO = "http://127.0.0.1:1234/v1";
    static final ModelSelection GEMMA = new ModelSelection("ollama", "gemma4:e4b");
    static final ModelSelection QWEN = new ModelSelection("ollama", "qwen3:8b");
    static final ModelSelection LM_QWEN = new ModelSelection("lmstudio", "qwen3:8b");

    RecordingModelCatalog catalog;
    SavedProviderConfigs configs;
    MemorySettings settings;
    RecordingModelPickPrompt pick;
    SettingsViewModel viewModel;
    ExecutorService executor;

    @Override
    public void start(final Stage stage) {
        // The toolkit is all these tests need; nothing here has a scene.
    }

    @BeforeEach
    void setUpFakes() {
        catalog = RecordingModelCatalog.nothingAnswering();
        configs = new SavedProviderConfigs();
        settings = new MemorySettings();
        pick = new RecordingModelPickPrompt();
        executor = new DirectExecutor();
    }

    @AfterEach
    void tearDownExecutor() {
        executor.shutdownNow();
    }

    LocalModelProbe probe(final SettingsRepository store) {
        final ActivityTracker activities = new ActivityTracker(new StateMirror());
        viewModel = onFx(() -> new SettingsViewModel(
                configs,
                ScriptedProviderVerifier.idle(),
                new ModelListing(
                        ScriptedModelCatalog.idle(),
                        new RecordingToasts(),
                        new RecordingErrorPresenter(),
                        new DirectExecutor(),
                        activities),
                new RecordingErrorPresenter(),
                new DirectExecutor(),
                activities,
                store,
                EnglishMessages.create(),
                new ModelSpeedRecorder(new ScriptedModelSettings(), new ModelSpeeds(), new DirectExecutor())));
        return onFx(() -> new LocalModelProbe(catalog, configs, store, executor, viewModel, pick));
    }

    LocalModelProbe probe() {
        return probe(settings);
    }

    void firstLaunch(final LocalModelProbe probe) {
        onFx(() -> {
            probe.runFirstLaunch();
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();
    }

    void tryAgain(final LocalModelProbe probe) {
        onFx(() -> {
            probe.tryAgain();
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();
    }

    ProbeOutcome outcome(final LocalModelProbe probe) {
        return onFx(() -> probe.outcome().get());
    }

    Optional<ModelSelection> storedModel() {
        return dataOf(settings.get(AppSetting.CURRENT_MODEL));
    }

    static <T> Optional<T> dataOf(final Result<Optional<T>> result) {
        return result.data() == null ? Optional.empty() : result.data();
    }

    boolean flagSet() {
        return dataOf(settings.get(AppSetting.FIRST_LAUNCH_PROBED)).orElse(false);
    }
}
