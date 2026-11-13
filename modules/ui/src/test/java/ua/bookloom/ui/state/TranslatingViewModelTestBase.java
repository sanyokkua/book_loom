package ua.bookloom.ui.state;

import static ua.bookloom.ui.ThemeTestSupport.onFx;

import com.google.inject.Injector;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.testfx.util.WaitForAsyncUtils;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ContextLength;
import ua.bookloom.api.llm.ModelCapabilities;
import ua.bookloom.api.llm.ModelSelection;
import ua.bookloom.api.pipeline.ProofreadProgress;
import ua.bookloom.api.pipeline.ProofreadTally;
import ua.bookloom.api.pipeline.ReviewMode;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.ui.BookFixtures;
import ua.bookloom.ui.EnglishMessages;
import ua.bookloom.ui.FakeProviderConfigs;
import ua.bookloom.ui.MemorySettings;
import ua.bookloom.ui.RecordingErrorPresenter;
import ua.bookloom.ui.RecordingToasts;
import ua.bookloom.ui.RecordingToasts.Raised;
import ua.bookloom.ui.ScriptedChatModelFactory;
import ua.bookloom.ui.ScriptedModelCatalog;
import ua.bookloom.ui.ScriptedModelSettings;
import ua.bookloom.ui.ScriptedProjectService;
import ua.bookloom.ui.ScriptedProviderVerifier;
import ua.bookloom.ui.ScriptedTranslationEngine;
import ua.bookloom.ui.SessionInfo;
import ua.bookloom.ui.UiTestInjector;
import ua.bookloom.ui.dialog.RecordingContinuePrompt;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.notify.Reporting;
import ua.bookloom.ui.notify.Voice;

/**
 * What the translating view model tests share, on top of the runner fixtures: a real {@link TranslationRunner}
 * running the recording job on its own thread, and a view model whose preparation goes to a <em>separate</em> queued
 * executor. The separation is deliberate: a calling-thread executor would run the job inline on the FX thread and
 * deadlock the test, and a queued one lets a test hold the preparation and look at the state before it runs.
 *
 * <p>The view model registers a listener on the shared mirror, so a test builds exactly one, by calling
 * {@link #buildViewModel()} after it has replaced whichever fake it wants to script.
 */
// The view model is built by each test after it has scripted its fakes, which NullAway cannot see.
@SuppressWarnings("NullAway.Init")
abstract class TranslatingViewModelTestBase extends RunnerTestBase {

    static final Path BOOK = Path.of("Frankenstein.epub");
    static final String MODEL = "gemma3:12b";
    static final String PROVIDER = "ProviderError";
    static final String NONE = "none";

    protected Injector graph;
    protected RecordingContinuePrompt continuePrompt;
    protected ScriptedModelCatalog catalog;
    protected CurrentProject current;
    protected RecordingToasts toasts;
    protected RecordingErrorPresenter errors;
    protected ScriptedChatModelFactory models;
    protected ScriptedTranslationEngine engine;
    protected ScriptedProjectService projects;
    protected ExecutorService prepExecutor;
    protected ReviewMode reviewMode = ReviewMode.UNATTENDED;
    protected QueuedExecutor queued;
    protected ImportViewModel imports;
    protected BookBriefViewModel brief;
    protected SettingsViewModel settings;

    /** The verifier behind {@link #settings}; it answers every check with an empty report and records the calls. */
    protected final ScriptedProviderVerifier settingsVerifier = ScriptedProviderVerifier.idle();

    protected TranslatingViewModel viewModel;

    /** Where the stored project's Proofread stage stands as the next read gives it; none left unless a test says. */
    protected volatile @Nullable ProofreadTally proofreadLeft;

    /** Whether the stored project's Proofread code part has work left, as the next read gives it; none unless said. */
    protected volatile boolean codeWorkLeft;

    /** The glossary rows as stored, whose change reads the Proofread standing again. */
    protected final ObservableList<GlossaryEntry> glossaryRows = FXCollections.observableArrayList();

    /** What the server says when a run starts: whether it loads the model with settings of its own (LM Studio). */
    protected volatile boolean modelLoadsWithOwnSettings;

    @BeforeEach
    void setUpTranslatingFixtures() {
        toasts = new RecordingToasts();
        errors = new RecordingErrorPresenter();
        models = ScriptedChatModelFactory.ok();
        engine = ScriptedTranslationEngine.returning(job);
        projects = new ScriptedProjectService();
        continuePrompt = new RecordingContinuePrompt();
        catalog = ScriptedModelCatalog.idle();
        // The real guard, over the real navigator and project holder of a window graph; its background work runs
        // inline.
        graph = UiTestInjector.builder(Locale.ENGLISH)
                .projects(projects)
                .catalog(catalog)
                .continuePrompt(continuePrompt)
                .backgroundExecutor(new DirectExecutor())
                .build();
        current = graph.getInstance(CurrentProject.class);
        queued = new QueuedExecutor();
        prepExecutor = queued;
        imports = onFx(() -> new ImportViewModel(
                projects,
                current,
                new RecordingToasts(),
                new RecordingErrorPresenter(),
                new DirectExecutor(),
                new ActivityTracker(new StateMirror())));
        brief = onFx(() -> new BookBriefViewModel(current, projects, new DirectExecutor(), tag -> true));
        settings = onFx(() -> newSettings(activities, settingsVerifier));
    }

    private static SettingsViewModel newSettings(
            final ActivityTracker activities, final ScriptedProviderVerifier verifier) {
        return new SettingsViewModel(
                new ProviderStore(new FakeProviderConfigs(), new MemorySettings()),
                new ProviderChecks(
                        verifier,
                        new ModelListing(
                                ScriptedModelCatalog.idle(),
                                new RecordingToasts(),
                                new RecordingErrorPresenter(),
                                new DirectExecutor(),
                                activities,
                                EnglishMessages.create()),
                        QuantChipsFixture.idle()),
                new Voice(EnglishMessages.create(), new RecordingErrorPresenter()),
                new DirectExecutor(),
                activities,
                new InferenceMemory(
                        new ModelSpeedRecorder(new ScriptedModelSettings(), new ModelSpeeds(), new DirectExecutor()),
                        new StructuredAnswers()),
                java.time.Clock.systemUTC());
    }

    // The model's window is unknown, and whether the server loads the model by itself is what the test scripted.
    private ModelCapabilities capabilities() {
        return new ModelCapabilities() {
            @Override
            public Result<ContextLength> contextLength(final String providerId, final String modelId) {
                return Result.ok(ContextLength.unknown());
            }

            @Override
            public boolean loadsWithOwnSettings(final ModelSelection selection) {
                return modelLoadsWithOwnSettings;
            }
        };
    }

    /** Builds the view model over the fakes as they are now; call once, after scripting. */
    protected void buildViewModel() {
        final RunStarter starter = new RunStarter(
                current,
                new RunPorts(models, engine, ScriptedProviderVerifier.idle(), capabilities()),
                () -> reviewMode,
                runner,
                new SessionReporter(new SessionInfo(), current, new FakeProviderConfigs()),
                prepExecutor,
                activities);
        viewModel = onFx(() -> new TranslatingViewModel(
                new RunAccess(mirror, runner),
                new RunSubject(current, settings, new ReadyQuantChip(settings), standing()),
                new RunLaunch(starter, graph.getInstance(ContinueGuard.class)),
                new Reporting(toasts, errors),
                desk,
                new DirectExecutor(),
                activities));
        WaitForAsyncUtils.waitForFxEvents();
    }

    private ProofreadStanding standing() {
        return new ProofreadStanding(
                new ProofreadStanding.Ports(progress(), new ModelBatchSize(models, settings)),
                settings,
                current,
                mirror,
                new DirectExecutor(),
                Runnable::run,
                glossaryRows);
    }

    private ProofreadProgress progress() {
        return new ProofreadProgress() {
            @Override
            public Result<Optional<ProofreadTally>> tally(final String projectId, final int batchSize) {
                return Result.ok(Optional.ofNullable(proofreadLeft));
            }

            @Override
            public Result<Boolean> codeWorkLeft(final String projectId) {
                return Result.ok(codeWorkLeft);
            }
        };
    }

    /** Opens the book (declaring English) and chooses a target and a model, so that {@code start()} has all it needs. */
    protected void openBookAndChooseModel() {
        openBook();
        onFx(() -> {
            brief.setTargetLanguage("uk");
            return null;
        });
        chooseModel(MODEL);
    }

    protected void openBook() {
        projects.on(BOOK, Result.ok(BookFixtures.frankensteinImport()));
        onFx(() -> {
            imports.open(BOOK);
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();
    }

    protected void chooseModel(final String model) {
        onFx(() -> {
            settings.model().set(model);
            return null;
        });
    }

    protected void press(final Runnable control) {
        onFx(() -> {
            control.run();
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();
    }

    /** Presses start and runs the held preparation on the calling (JUnit) thread, which is not the FX thread. */
    protected void startAndPrepare() throws InterruptedException {
        press(viewModel::start);
        queued.runAll();
        job.awaitRunStarted();
        WaitForAsyncUtils.waitForFxEvents();
    }

    protected Controls controls() {
        return onFx(() -> viewModel.controls().get());
    }

    /** The notice the dashboard is showing instead of the plain state banner, if any. */
    protected Optional<RunNotice> notice() {
        return onFx(() -> Optional.ofNullable(viewModel.notice().get()));
    }

    /** The messages raised other than the one that announces a run has begun. */
    protected List<Raised> outcomeToasts() {
        return toasts.raised().stream()
                .filter(raised -> raised.key() != MessageKey.TOAST_RUN_STARTED)
                .toList();
    }

    protected boolean preparing() {
        return onFx(() -> viewModel.preparing().get());
    }

    protected static AppError failureOf(final ErrorCode code) {
        return AppError.of(code, "A title", "A message about " + code.name());
    }

    /** The simple class name of the notice being shown, or {@code none}. */
    protected String noticeKind() {
        return notice().map(shown -> shown.getClass().getSimpleName()).orElse(NONE);
    }
}
