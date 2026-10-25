package ua.bookloom.ui.state;

import static ua.bookloom.ui.ThemeTestSupport.onFx;

import java.nio.file.Path;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import javafx.stage.Stage;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.testfx.util.WaitForAsyncUtils;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ContextLength;
import ua.bookloom.api.llm.ModelCapabilities;
import ua.bookloom.api.pipeline.ImportedBook;
import ua.bookloom.ui.FakeProviderConfigs;
import ua.bookloom.ui.FxTestBase;
import ua.bookloom.ui.MemorySettings;
import ua.bookloom.ui.ModalHost;
import ua.bookloom.ui.RecordingErrorPresenter;
import ua.bookloom.ui.RecordingProjectRepository;
import ua.bookloom.ui.RecordingToasts;
import ua.bookloom.ui.ScriptedChatModelFactory;
import ua.bookloom.ui.ScriptedExportService;
import ua.bookloom.ui.ScriptedModelCatalog;
import ua.bookloom.ui.ScriptedModelSettings;
import ua.bookloom.ui.ScriptedProjectService;
import ua.bookloom.ui.ScriptedProviderVerifier;
import ua.bookloom.ui.ScriptedReviewDesk;
import ua.bookloom.ui.UiTestInjector;
import ua.bookloom.ui.dialog.ConfirmDialog;
import ua.bookloom.ui.i18n.LocaleProvider;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.notify.DialogFeedback;

/**
 * What the export view model tests share: a real {@link ImportViewModel} over the scripted project service and a
 * calling-thread executor, through which books are opened for real, a real {@link BookBriefViewModel} that chooses the
 * target the destination follows, and helpers that read and drive the export view model on the FX thread. The existence check runs on a calling-thread
 * executor and its answer is published with {@code Platform.runLater}, so every driver waits for the FX queue to
 * drain. The toolkit runs so that the open's answer is published to the FX thread as it is in the application.
 */
abstract class ExportViewModelTestBase extends FxTestBase {

    ScriptedProjectService projects;
    CurrentProject current;
    ImportViewModel imports;
    BookBriefViewModel brief;
    ExportViewModel exports;
    StateMirror mirror;
    ActivityTracker activities;
    ScriptedReviewDesk desk;
    Messages messages;
    ScriptedExportService exportService;
    ScriptedChatModelFactory models;
    SettingsViewModel settings;
    WorkflowProgress progress;
    final MemorySettings storedSettings = new MemorySettings();
    final RecordingProjectRepository store = new RecordingProjectRepository();

    /** A provider that never says what window its model has. */
    final ModelCapabilities capabilities = (providerId, modelId) -> Result.ok(ContextLength.unknown());

    @Override
    public final void start(final Stage stage) {
        // The toolkit is all these tests need; the view model has no scene.
    }

    @BeforeEach
    final void setUpViewModels() {
        projects = new ScriptedProjectService();
        current = new CurrentProject();
        imports = onFx(() -> new ImportViewModel(
                projects,
                current,
                new RecordingToasts(),
                new RecordingErrorPresenter(),
                new DirectExecutor(),
                new ActivityTracker(new StateMirror())));
        brief = onFx(() -> new BookBriefViewModel(current, projects, new DirectExecutor(), tag -> true));
        mirror = onFx(StateMirror::new);
        activities = onFx(() -> new ActivityTracker(mirror));
        desk = new ScriptedReviewDesk();
        messages = new Messages((LocaleProvider) () -> Locale.ENGLISH);
        exportService = new ScriptedExportService();
        models = ScriptedChatModelFactory.ok();
        settings = onFx(this::newSettings);
        progress = onFx(() -> new WorkflowProgress(current, mirror, desk, new DirectExecutor()));
        exports = onFx(() -> newExports(new DirectExecutor()));
        WaitForAsyncUtils.waitForFxEvents();
    }

    private SettingsViewModel newSettings() {
        return new SettingsViewModel(
                new FakeProviderConfigs(),
                ScriptedProviderVerifier.idle(),
                new ModelListing(
                        ScriptedModelCatalog.idle(),
                        new RecordingToasts(),
                        new RecordingErrorPresenter(),
                        new DirectExecutor(),
                        activities,
                        messages),
                new RecordingErrorPresenter(),
                new DirectExecutor(),
                activities,
                new MemorySettings(),
                messages,
                new ModelSpeedRecorder(new ScriptedModelSettings(), new ModelSpeeds(), new DirectExecutor()),
                new StructuredAnswers());
    }

    /** Builds an export view model over this test's collaborators and the given executor. */
    ExportViewModel newExports(final ExecutorService executor) {
        return newExports(executor, null);
    }

    /** Builds an export view model with a setup helper, or none. FX thread only (the question card is a node). */
    ExportViewModel newExports(final ExecutorService executor, final @Nullable SetupHelper setup) {
        return new ExportViewModel(
                current,
                desk,
                projects,
                messages,
                exportService,
                models,
                settings,
                progress,
                executor,
                activities,
                setup,
                storedSettings,
                store,
                new ConfirmDialog(new ModalHost(new WorkVisibility(), new DialogFeedback()), messages),
                (ExportOutcome outcome) -> {},
                Clock.fixed(UiTestInjector.NOW, ZoneOffset.UTC));
    }

    /** Replaces the export view model with a new one over the same current project, as a later first visit would. */
    void recreateExports() {
        exports = onFx(() -> newExports(new DirectExecutor()));
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

    boolean destinationExists() {
        return onFx(() -> exports.destinationExists().get());
    }
}
