package ua.bookloom.ui;

import com.google.inject.AbstractModule;
import com.google.inject.Key;
import com.google.inject.multibindings.Multibinder;
import java.util.concurrent.ExecutorService;
import ua.bookloom.api.llm.ChatModelFactory;
import ua.bookloom.api.llm.ModelCatalog;
import ua.bookloom.api.llm.ProviderConfigs;
import ua.bookloom.api.llm.ProviderVerifier;
import ua.bookloom.api.pipeline.ExportService;
import ua.bookloom.api.pipeline.GlossaryService;
import ua.bookloom.api.pipeline.LexiconService;
import ua.bookloom.api.pipeline.NamesPass;
import ua.bookloom.api.pipeline.ProjectService;
import ua.bookloom.api.pipeline.ReviewDesk;
import ua.bookloom.api.pipeline.ReviewMode;
import ua.bookloom.api.pipeline.TranslationEngine;
import ua.bookloom.ui.control.RunStatusBar;
import ua.bookloom.ui.dialog.ContinueDialog;
import ua.bookloom.ui.dialog.ContinuePrompt;
import ua.bookloom.ui.dialog.FindBookDialog;
import ua.bookloom.ui.dialog.FindBookPrompt;
import ua.bookloom.ui.dialog.ModelPickDialog;
import ua.bookloom.ui.dialog.ModelPickPrompt;
import ua.bookloom.ui.dialog.OpenAnotherBookDialog;
import ua.bookloom.ui.dialog.OpenAnotherBookPrompt;
import ua.bookloom.ui.dialog.RenameDialog;
import ua.bookloom.ui.dialog.RenamePrompt;
import ua.bookloom.ui.dialog.SamePairDialog;
import ua.bookloom.ui.dialog.SamePairPrompt;
import ua.bookloom.ui.dialog.TranslateIntoDialog;
import ua.bookloom.ui.dialog.TranslateIntoPrompt;
import ua.bookloom.ui.i18n.LanguageNames;
import ua.bookloom.ui.i18n.LocaleProvider;
import ua.bookloom.ui.i18n.OsLocaleProvider;
import ua.bookloom.ui.notify.DialogFeedback;
import ua.bookloom.ui.notify.ErrorPresenter;
import ua.bookloom.ui.notify.ModalErrorPresenter;
import ua.bookloom.ui.notify.ToastStack;
import ua.bookloom.ui.notify.Toasts;
import ua.bookloom.ui.state.BookBriefViewModel;
import ua.bookloom.ui.state.BookFileChooser;
import ua.bookloom.ui.state.ContinueGuard;
import ua.bookloom.ui.state.CsvFileChooser;
import ua.bookloom.ui.state.CurrentProject;
import ua.bookloom.ui.state.DestinationChooser;
import ua.bookloom.ui.state.DiagnosticActions;
import ua.bookloom.ui.state.DirtySource;
import ua.bookloom.ui.state.ExportViewModel;
import ua.bookloom.ui.state.FileRevealer;
import ua.bookloom.ui.state.HiddenResultsAnnouncer;
import ua.bookloom.ui.state.ImportGuard;
import ua.bookloom.ui.state.ImportViewModel;
import ua.bookloom.ui.state.LastOpened;
import ua.bookloom.ui.state.LastProjectRecorder;
import ua.bookloom.ui.state.LaunchProject;
import ua.bookloom.ui.state.LedgerFigures;
import ua.bookloom.ui.state.LocalModelProbe;
import ua.bookloom.ui.state.ModelListing;
import ua.bookloom.ui.state.PlatformFileRevealer;
import ua.bookloom.ui.state.ProgressFigures;
import ua.bookloom.ui.state.ProjectActions;
import ua.bookloom.ui.state.ProjectActionsImpl;
import ua.bookloom.ui.state.ProjectOpener;
import ua.bookloom.ui.state.ProjectsViewModel;
import ua.bookloom.ui.state.ReviewPauseFollower;
import ua.bookloom.ui.state.RowOpener;
import ua.bookloom.ui.state.RunInterventions;
import ua.bookloom.ui.state.RunStarter;
import ua.bookloom.ui.state.RunStateSource;
import ua.bookloom.ui.state.SessionReporter;
import ua.bookloom.ui.state.SettingsViewModel;
import ua.bookloom.ui.state.StateMirror;
import ua.bookloom.ui.state.StepAvailability;
import ua.bookloom.ui.state.StepLocks;
import ua.bookloom.ui.state.StoredNarratorFollower;
import ua.bookloom.ui.state.SystemBookFileChooser;
import ua.bookloom.ui.state.SystemCsvFileChooser;
import ua.bookloom.ui.state.SystemDestinationChooser;
import ua.bookloom.ui.state.TranslatingViewModel;
import ua.bookloom.ui.state.TranslationRunner;
import ua.bookloom.ui.theme.ColorSchemeProvider;
import ua.bookloom.ui.theme.PlatformColorSchemeProvider;

/**
 * Guice bindings owned by {@code :ui} — the navigation host, the shell chrome, the FXML controller factory and the
 * run state.
 *
 * <p>The theme and display-locale seams, the {@link Navigator}, the {@link GuiceControllerFactory}, the
 * {@link ModalHost}, the notification surfaces ({@link Toasts} over the toast stack, {@link ErrorPresenter} over the
 * modal error dialog), the {@link AppShellView} with the {@link RunStatusBar} it places in the title bar, the {@link StateMirror} with the {@link TranslationRunner} that
 * feeds it, the {@link SettingsViewModel} and the {@link ModelListing} it owns, the {@link CurrentProject} holding the
 * open book, the {@link ImportViewModel} that fills it, the {@link BookBriefViewModel} that holds the choices made about it, the {@link ExportViewModel} that holds where the translation is written, the
 * {@link ImportGuard} that asks before an import replaces a run, the {@link RunStarter} that builds a run on it and the {@link TranslatingViewModel} that starts, controls and announces a run are bound so far, as is the
 * {@link FileRevealer} that shows a written book in the file manager.
 * The singletons are bound explicitly rather than left to JIT so the composition root's graph lists everything the
 * window depends on. The {@link DiagnosticLog} value, the {@link BuildVersion} value, the {@link BackgroundExecutor} pool, the ports ({@link
 * ProviderConfigs}, {@link ProviderVerifier}, {@link ModelCatalog}, {@link ChatModelFactory},
 * {@link TranslationEngine}, {@link ProjectService}, {@link GlossaryService}, {@link LexiconService}, {@link NamesPass}, {@link ReviewDesk} and
 * {@link ExportService}) and the {@link ReviewMode} are deliberately absent: the composition root owns the value, the
 * pool and the mode, and {@code :document}, {@code :llm} and {@code :pipeline} implement the ports. This module
 * requires all of them, so a root that forgets one fails at injector-build time rather than when About is first
 * opened, a book is first imported, a run is started or the settings screen is first shown.
 */
public final class UiModule extends AbstractModule {

    /** Installed by the composition root in {@code :app}. */
    public UiModule() {
        // Guice modules are constructed, not injected.
    }

    private void bindDialogs() {
        bind(ModalErrorPresenter.class);
        bind(ErrorPresenter.class).to(ModalErrorPresenter.class);
        bind(OpenAnotherBookPrompt.class).to(OpenAnotherBookDialog.class);
        bind(ContinuePrompt.class).to(ContinueDialog.class);
        bind(ModelPickPrompt.class).to(ModelPickDialog.class);
        bind(RenamePrompt.class).to(RenameDialog.class);
        bind(SamePairPrompt.class).to(SamePairDialog.class);
        bind(TranslateIntoPrompt.class).to(TranslateIntoDialog.class);
        bind(FindBookPrompt.class).to(FindBookDialog.class);
    }

    private void bindState() {
        bind(StateMirror.class);
        bind(TranslationRunner.class);
        bind(RunInterventions.class);
        bind(ModelListing.class);
        bind(SettingsViewModel.class);
        bind(CurrentProject.class);
        bind(ImportViewModel.class);
        bind(ImportGuard.class);
        bind(BookBriefViewModel.class);
        bind(ExportViewModel.class);
        bind(RunStarter.class);
        bind(TranslatingViewModel.class);
        bind(RunStateSource.class);
        bind(ReviewPauseFollower.class);
        bind(DiagnosticActions.class);
        bind(LastOpened.class);
        bind(ProjectsViewModel.class);
        bind(ProjectOpener.class);
        bind(LaunchProject.class);
        bind(LocalModelProbe.class);
        bind(ContinueGuard.class);
        bind(ProjectActions.class).to(ProjectActionsImpl.class);
        bind(RowOpener.class).to(ProjectOpener.class);
        // Eager, so a project opened from any screen is remembered for the next launch.
        bind(LastProjectRecorder.class).asEagerSingleton();
        // Eager, so the first book opened is told to the detailed log before any screen asks for the reporter.
        bind(SessionReporter.class).asEagerSingleton();
        // Eager, so a narrator the run inferred reaches the brief even when no brief screen exists when the run ends.
        bind(StoredNarratorFollower.class).asEagerSingleton();
        // Eager, so a names pass that ends in the background is announced while any screen shows.
        bind(HiddenResultsAnnouncer.class).asEagerSingleton();
    }

    private void bindShell() {
        bind(RunStatusBar.class);
        bind(AppShellView.class);
    }

    @Override
    protected void configure() {
        bind(ColorSchemeProvider.class).to(PlatformColorSchemeProvider.class);
        bind(LocaleProvider.class).to(OsLocaleProvider.class);
        bind(GuiceControllerFactory.class);
        bind(Navigator.class);
        bind(WindowLaunch.class);
        bind(ModalHost.class);
        bind(LanguageNames.class);
        bind(DialogFeedback.class);
        bind(ToastStack.class);
        bind(Toasts.class).to(ToastStack.class);
        bind(StepLocks.class).to(StepAvailability.class);
        // No form guards its unsaved changes yet; the Settings forms register here (tasks 7.1a, 7.2).
        Multibinder.newSetBinder(binder(), DirtySource.class);
        bind(ProgressFigures.Factory.class).toInstance(LedgerFigures::new);
        bindDialogs();
        bindShell();
        bindState();
        bind(FileRevealer.class).to(PlatformFileRevealer.class);
        bind(DestinationChooser.class).to(SystemDestinationChooser.class);
        bind(CsvFileChooser.class).to(SystemCsvFileChooser.class);
        bind(BookFileChooser.class).to(SystemBookFileChooser.class);
        requireBinding(ProviderConfigs.class);
        requireBinding(ProviderVerifier.class);
        requireBinding(ModelCatalog.class);
        requireBinding(ChatModelFactory.class);
        requireBinding(Key.get(String.class, BuildVersion.class));
        requireBinding(DiagnosticLog.class);
        requireBinding(Key.get(ExecutorService.class, BackgroundExecutor.class));
        requirePipelineBindings();
    }

    private void requirePipelineBindings() {
        requireBinding(TranslationEngine.class);
        requireBinding(ProjectService.class);
        requireBinding(GlossaryService.class);
        requireBinding(LexiconService.class);
        requireBinding(NamesPass.class);
        requireBinding(ReviewDesk.class);
        requireBinding(ExportService.class);
        requireBinding(ReviewMode.class);
    }
}
