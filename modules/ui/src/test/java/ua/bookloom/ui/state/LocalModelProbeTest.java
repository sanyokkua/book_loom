package ua.bookloom.ui.state;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static ua.bookloom.ui.ThemeTestSupport.onFx;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testfx.util.WaitForAsyncUtils;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ModelSelection;
import ua.bookloom.api.llm.ProviderConfig;
import ua.bookloom.api.llm.ProviderKind;
import ua.bookloom.api.persistence.SettingsRepository;
import ua.bookloom.api.settings.AppSetting;
import ua.bookloom.ui.FxTestBase;
import ua.bookloom.ui.MemorySettings;
import ua.bookloom.ui.RecordingErrorPresenter;
import ua.bookloom.ui.RecordingModelCatalog;
import ua.bookloom.ui.RecordingToasts;
import ua.bookloom.ui.ScriptedModelCatalog;
import ua.bookloom.ui.ScriptedProviderVerifier;
import ua.bookloom.ui.dialog.RecordingModelPickPrompt;

/**
 * The first-launch look at the two local programs and the person's "Try again": which addresses are asked, how often,
 * what is stored and what the outcome says. The catalogue, the stores, the question and the view model are
 * hand-written or real; the look runs on a calling-thread executor unless a test needs it in flight.
 */
@SuppressWarnings("NullAway.Init")
class LocalModelProbeTest extends FxTestBase {

    private static final String OLLAMA = "http://127.0.0.1:11434";
    private static final String LM_STUDIO = "http://127.0.0.1:1234/v1";
    private static final ModelSelection GEMMA = new ModelSelection("ollama", "gemma4:e4b");
    private static final ModelSelection QWEN = new ModelSelection("ollama", "qwen3:8b");
    private static final ModelSelection LM_QWEN = new ModelSelection("lmstudio", "qwen3:8b");

    private RecordingModelCatalog catalog;
    private SavedProviderConfigs configs;
    private MemorySettings settings;
    private RecordingModelPickPrompt pick;
    private SettingsViewModel viewModel;
    private ExecutorService executor;

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

    private LocalModelProbe probe(final SettingsRepository store) {
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
                store));
        return onFx(() -> new LocalModelProbe(catalog, configs, store, executor, viewModel, pick));
    }

    private LocalModelProbe probe() {
        return probe(settings);
    }

    private void firstLaunch(final LocalModelProbe probe) {
        onFx(() -> {
            probe.runFirstLaunch();
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();
    }

    private void tryAgain(final LocalModelProbe probe) {
        onFx(() -> {
            probe.tryAgain();
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();
    }

    private ProbeOutcome outcome(final LocalModelProbe probe) {
        return onFx(() -> probe.outcome().get());
    }

    private Optional<ModelSelection> storedModel() {
        return dataOf(settings.get(AppSetting.CURRENT_MODEL));
    }

    private static <T> Optional<T> dataOf(final Result<Optional<T>> result) {
        return result.data() == null ? Optional.empty() : result.data();
    }

    private boolean flagSet() {
        return dataOf(settings.get(AppSetting.FIRST_LAUNCH_PROBED)).orElse(false);
    }

    // IF the look asked any other address, or one twice, THEN it would reach where ADR-0044 does not allow.
    @Test
    void runFirstLaunch_freshStore_asksExactlyTheTwoLocalAddressesOnceEachWithAOneSecondConnect() {
        firstLaunch(probe());

        assertThat(catalog.drafts())
                .extracting(
                        RecordingModelCatalog.Draft::address,
                        RecordingModelCatalog.Draft::kind,
                        RecordingModelCatalog.Draft::connectTimeout)
                .containsExactly(
                        tuple(OLLAMA, ProviderKind.OLLAMA, Duration.ofSeconds(1)),
                        tuple(LM_STUDIO, ProviderKind.OPENAI_COMPATIBLE, Duration.ofSeconds(1)));
        assertThat(catalog.ids()).isEmpty();
    }

    // IF the look repeated on every launch, THEN the app would reach out by itself more than once.
    @Test
    void runFirstLaunch_flagAlreadySet_makesNoCallAtAll() {
        settings.put(AppSetting.FIRST_LAUNCH_PROBED, true);

        final LocalModelProbe probe = probe();

        firstLaunch(probe);

        assertThat(catalog.drafts()).isEmpty();
        assertThat(outcome(probe)).isEqualTo(new ProbeOutcome.Idle());
    }

    @Test
    void runFirstLaunch_calledTwice_looksOnlyOnce() {
        final LocalModelProbe probe = probe();

        firstLaunch(probe);
        firstLaunch(probe);

        assertThat(catalog.draftAddresses()).containsExactly(OLLAMA, LM_STUDIO);
    }

    // IF the look overwrote a program the person already set up, THEN their name, timeouts and models would be lost.
    @Test
    void runFirstLaunch_providerSavedAtSameAddress_keepsItAndStillSelectsTheModel() {
        final ProviderConfig mine = new ProviderConfig(
                "ollama",
                ProviderKind.OLLAMA,
                URI.create("http://localhost:11434"),
                Duration.ofSeconds(9),
                Duration.ofSeconds(99));
        configs.save(mine);
        catalog.answering(OLLAMA, "gemma4:e4b");

        firstLaunch(probe());

        assertThat(configs.saved()).containsExactly(mine);
        assertThat(settings.get(AppSetting.CURRENT_MODEL).data()).contains(GEMMA);
    }

    // IF "ollama" saved at a remote host were taken for the local program, THEN the chosen model would not exist there.
    @Test
    void runFirstLaunch_providerSavedAtAnotherAddress_ignoresTheAnswerAndSavesNothing() {
        configs.save(new ProviderConfig(
                "ollama",
                ProviderKind.OLLAMA,
                URI.create("http://10.0.0.5:11434"),
                Duration.ofSeconds(9),
                Duration.ofSeconds(99)));
        catalog.answering(OLLAMA, "gemma4:e4b");

        final LocalModelProbe probe = probe();
        firstLaunch(probe);

        assertThat(configs.saved()).hasSize(1);
        assertThat(outcome(probe)).isEqualTo(new ProbeOutcome.NotFound());
    }

    // IF the flag were not written after a look that found nothing, THEN every launch would look again.
    @Test
    void runFirstLaunch_nothingAnswering_storesNothingSetsTheFlagAndSaysNotFound() {
        final LocalModelProbe probe = probe();

        firstLaunch(probe);

        assertThat(configs.saved()).isEmpty();
        assertThat(storedModel()).isEmpty();
        assertThat(flagSet()).isTrue();
        assertThat(outcome(probe)).isEqualTo(new ProbeOutcome.NotFound());
    }

    // IF a program that answers with no models counted as found, THEN the line would say ready for nothing.
    @Test
    void runFirstLaunch_programAnswersWithZeroModels_countsAsNotFoundAndStoresNothing() {
        catalog.answering(OLLAMA);
        final LocalModelProbe probe = probe();

        firstLaunch(probe);

        assertThat(configs.saved()).isEmpty();
        assertThat(storedModel()).isEmpty();
        assertThat(outcome(probe)).isEqualTo(new ProbeOutcome.NotFound());
    }

    // IF the stored provider carried the look's one-second connect timeout, THEN every later call would give up early.
    @Test
    void runFirstLaunch_oneOllamaModel_savesTheProviderWithDefaultTimeoutsAndChoosesTheModel() {
        catalog.answering(OLLAMA, "gemma4:e4b");
        final LocalModelProbe probe = probe();

        firstLaunch(probe);

        assertThat(configs.saved())
                .containsExactly(new ProviderConfig(
                        "ollama",
                        ProviderKind.OLLAMA,
                        URI.create(OLLAMA),
                        ProviderConfig.DEFAULT_CONNECT_TIMEOUT,
                        ProviderConfig.DEFAULT_REQUEST_TIMEOUT));
        assertThat(storedModel()).contains(GEMMA);
        assertThat(onFx(() -> viewModel.selection())).contains(GEMMA);
        assertThat(flagSet()).isTrue();
        assertThat(outcome(probe)).isEqualTo(new ProbeOutcome.Found(GEMMA, true));
        assertThat(pick.asked()).isEmpty();
    }

    @Test
    void runFirstLaunch_onlyLmStudioAnswersWithOneModel_savesItAndChoosesItsModel() {
        catalog.answering(LM_STUDIO, "qwen3:8b");
        final LocalModelProbe probe = probe();

        firstLaunch(probe);

        assertThat(configs.saved())
                .containsExactly(new ProviderConfig(
                        "lmstudio",
                        ProviderKind.OPENAI_COMPATIBLE,
                        URI.create(LM_STUDIO),
                        ProviderConfig.DEFAULT_CONNECT_TIMEOUT,
                        ProviderConfig.DEFAULT_REQUEST_TIMEOUT));
        assertThat(storedModel()).contains(LM_QWEN);
        assertThat(onFx(() -> viewModel.selection())).contains(LM_QWEN);
        assertThat(outcome(probe)).isEqualTo(new ProbeOutcome.Found(LM_QWEN, true));
    }

    // IF both programs answered and only one were kept, THEN Settings would not list the other one the person has.
    @Test
    void runFirstLaunch_bothAnswer_savesBothAndMakesOllamaTheCurrentProgram() {
        catalog.answering(OLLAMA, "gemma4:e4b").answering(LM_STUDIO, "qwen3:8b");
        final LocalModelProbe probe = probe();

        firstLaunch(probe);

        assertThat(configs.saved()).extracting(ProviderConfig::id).containsExactlyInAnyOrder("ollama", "lmstudio");
        assertThat(storedModel()).contains(GEMMA);
        assertThat(outcome(probe)).isEqualTo(new ProbeOutcome.Found(GEMMA, true));
    }

    @Test
    void runFirstLaunch_ollamaSilentLmStudioAnswers_lmStudioIsTheCurrentProgram() {
        catalog.answering(OLLAMA).answering(LM_STUDIO, "qwen3:8b");

        firstLaunch(probe());

        assertThat(configs.saved()).extracting(ProviderConfig::id).containsExactly("lmstudio");
        assertThat(storedModel()).contains(LM_QWEN);
    }

    // IF the first of several models were chosen silently, THEN the person would translate with a model they never
    // picked.
    @Test
    void runFirstLaunch_severalModels_asksWhichAndLeavesTheCurrentModelUnset() {
        catalog.answering(OLLAMA, "gemma4:e4b", "qwen3:8b");
        final LocalModelProbe probe = probe();

        firstLaunch(probe);

        assertThat(pick.asked())
                .containsExactly(new RecordingModelPickPrompt.Asked("ollama", List.of("gemma4:e4b", "qwen3:8b")));
        assertThat(storedModel()).isEmpty();
        assertThat(onFx(() -> viewModel.selection())).isEmpty();
        assertThat(configs.saved()).extracting(ProviderConfig::id).containsExactly("ollama");
        assertThat(flagSet()).isTrue();
        assertThat(outcome(probe)).isEqualTo(new ProbeOutcome.Asking());
    }

    @Test
    void pick_useThisModel_storesAndAdoptsTheChosenModel() {
        catalog.answering(OLLAMA, "gemma4:e4b", "qwen3:8b");
        final LocalModelProbe probe = probe();
        firstLaunch(probe);

        onFx(() -> {
            pick.use("qwen3:8b");
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(storedModel()).contains(QWEN);
        assertThat(onFx(() -> viewModel.selection())).contains(QWEN);
        assertThat(outcome(probe)).isEqualTo(new ProbeOutcome.Found(QWEN, true));
    }

    @Test
    void pick_notNow_leavesTheCurrentModelUnsetAndSaysNotFound() {
        catalog.answering(OLLAMA, "gemma4:e4b", "qwen3:8b");
        final LocalModelProbe probe = probe();
        firstLaunch(probe);

        onFx(() -> {
            pick.notNow();
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(storedModel()).isEmpty();
        assertThat(onFx(() -> viewModel.selection())).isEmpty();
        assertThat(outcome(probe)).isEqualTo(new ProbeOutcome.NotFound());
    }

    // IF the look ignored an unreadable flag and looked anyway, THEN a damaged store would make it repeat each launch.
    @Test
    void runFirstLaunch_flagUnreadable_skipsTheLookAndMakesNoCall() {
        final LocalModelProbe probe = probe(new UnreadableFlagSettings(settings));

        firstLaunch(probe);

        assertThat(catalog.drafts()).isEmpty();
        assertThat(outcome(probe)).isEqualTo(new ProbeOutcome.Idle());
    }

    // IF Try again honoured the flag, THEN pressing it after the first launch would do nothing.
    @Test
    void tryAgain_flagAlreadySet_looksAgainAnyway() {
        settings.put(AppSetting.FIRST_LAUNCH_PROBED, true);
        catalog.answering(OLLAMA, "gemma4:e4b");
        final LocalModelProbe probe = probe();

        tryAgain(probe);

        assertThat(catalog.draftAddresses()).containsExactly(OLLAMA, LM_STUDIO);
        assertThat(storedModel()).contains(GEMMA);
        assertThat(outcome(probe)).isEqualTo(new ProbeOutcome.Found(GEMMA, false));
    }

    @Test
    void tryAgain_afterANotFoundLook_asksBothAddressesAgainOnceEach() {
        final LocalModelProbe probe = probe();
        firstLaunch(probe);

        tryAgain(probe);

        assertThat(catalog.draftAddresses()).containsExactly(OLLAMA, LM_STUDIO, OLLAMA, LM_STUDIO);
        assertThat(outcome(probe)).isEqualTo(new ProbeOutcome.NotFound());
    }

    // IF the look blocked on the calling thread, THEN the window would freeze while a program does not answer.
    @Test
    void runFirstLaunch_programSlowToAnswer_saysLookingUntilTheAnswerComes() throws Exception {
        executor = Executors.newSingleThreadExecutor(task -> {
            final Thread thread = new Thread(task, "probe-test");
            thread.setDaemon(true);
            return thread;
        });
        catalog.holdDrafts();
        catalog.answering(OLLAMA, "gemma4:e4b");
        final LocalModelProbe probe = probe();

        firstLaunch(probe);

        assertThat(outcome(probe)).isEqualTo(new ProbeOutcome.Looking());
        catalog.releaseDrafts();
        WaitForAsyncUtils.waitFor(10, TimeUnit.SECONDS, () -> outcome(probe) instanceof ProbeOutcome.Found);
        assertThat(outcome(probe)).isEqualTo(new ProbeOutcome.Found(GEMMA, true));
    }

    @Test
    void runFirstLaunch_errorReply_isNotFoundWithoutAnyThrownError() {
        catalog.failing(OLLAMA, AppError.of(ErrorCode.upstream, "Bad", "A 503 reply."));
        final LocalModelProbe probe = probe();

        firstLaunch(probe);

        assertThat(outcome(probe)).isEqualTo(new ProbeOutcome.NotFound());
        assertThat(catalog.draftAddresses()).containsExactly(OLLAMA, LM_STUDIO);
    }
}
