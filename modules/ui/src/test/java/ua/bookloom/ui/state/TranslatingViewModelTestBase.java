package ua.bookloom.ui.state;

import static ua.bookloom.ui.ThemeTestSupport.onFx;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import org.junit.jupiter.api.BeforeEach;
import org.testfx.util.WaitForAsyncUtils;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.pipeline.ReviewMode;
import ua.bookloom.ui.BookFixtures;
import ua.bookloom.ui.FakeProviderConfigs;
import ua.bookloom.ui.RecordingErrorPresenter;
import ua.bookloom.ui.RecordingToasts;
import ua.bookloom.ui.RecordingToasts.Raised;
import ua.bookloom.ui.ScriptedChatModelFactory;
import ua.bookloom.ui.ScriptedModelCatalog;
import ua.bookloom.ui.ScriptedProjectService;
import ua.bookloom.ui.ScriptedProviderVerifier;
import ua.bookloom.ui.ScriptedTranslationEngine;
import ua.bookloom.ui.i18n.MessageKey;

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
    protected TranslatingViewModel viewModel;

    @BeforeEach
    void setUpTranslatingFixtures() {
        current = new CurrentProject();
        toasts = new RecordingToasts();
        errors = new RecordingErrorPresenter();
        models = ScriptedChatModelFactory.ok();
        engine = ScriptedTranslationEngine.returning(job);
        projects = new ScriptedProjectService();
        queued = new QueuedExecutor();
        prepExecutor = queued;
        imports = onFx(() -> new ImportViewModel(
                projects, current, new RecordingToasts(), new RecordingErrorPresenter(), new DirectExecutor()));
        brief = onFx(() -> new BookBriefViewModel(current, new DirectExecutor()));
        settings = onFx(() -> new SettingsViewModel(
                new FakeProviderConfigs(),
                ScriptedProviderVerifier.idle(),
                new ModelListing(
                        ScriptedModelCatalog.idle(),
                        new RecordingToasts(),
                        new RecordingErrorPresenter(),
                        new DirectExecutor()),
                new RecordingToasts(),
                new RecordingErrorPresenter(),
                new DirectExecutor()));
    }

    /** Builds the view model over the fakes as they are now; call once, after scripting. */
    protected void buildViewModel() {
        final RunStarter starter = new RunStarter(current, models, engine, projects, reviewMode, runner, prepExecutor);
        viewModel =
                onFx(() -> new TranslatingViewModel(mirror, runner, brief, current, settings, starter, toasts, errors));
        WaitForAsyncUtils.waitForFxEvents();
    }

    /** Opens the book and chooses a model, so that {@code start()} has everything it needs. */
    protected void openBookAndChooseModel() {
        projects.on(BOOK, Result.ok(BookFixtures.frankensteinImport()));
        onFx(() -> {
            imports.open(BOOK);
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();
        chooseModel(MODEL);
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
