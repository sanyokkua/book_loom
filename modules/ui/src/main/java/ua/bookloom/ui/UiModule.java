package ua.bookloom.ui;

import com.google.inject.AbstractModule;
import com.google.inject.Key;
import java.util.concurrent.ExecutorService;
import ua.bookloom.api.document.DocumentPort;
import ua.bookloom.api.llm.ChatModelFactory;
import ua.bookloom.api.llm.ModelCatalog;
import ua.bookloom.api.llm.ProviderConfigs;
import ua.bookloom.api.llm.ProviderVerifier;
import ua.bookloom.api.pipeline.TranslationEngine;
import ua.bookloom.ui.i18n.LocaleProvider;
import ua.bookloom.ui.i18n.OsLocaleProvider;
import ua.bookloom.ui.notify.ErrorPresenter;
import ua.bookloom.ui.notify.ModalErrorPresenter;
import ua.bookloom.ui.notify.ToastStack;
import ua.bookloom.ui.notify.Toasts;
import ua.bookloom.ui.state.BookBriefViewModel;
import ua.bookloom.ui.state.FileRevealer;
import ua.bookloom.ui.state.ImportViewModel;
import ua.bookloom.ui.state.ModelListing;
import ua.bookloom.ui.state.PlatformFileRevealer;
import ua.bookloom.ui.state.SettingsViewModel;
import ua.bookloom.ui.state.StateMirror;
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
 * modal error dialog), the {@link AppShellView}, the {@link StateMirror} with the {@link TranslationRunner} that
 * feeds it, the {@link SettingsViewModel} and the {@link ModelListing} it owns, the {@link ImportViewModel} that
 * holds the open book, the {@link BookBriefViewModel} that holds the choices made about it and the
 * {@link TranslatingViewModel} that starts, controls and announces a run are bound so far, as is the
 * {@link FileRevealer} that shows a written book in the file manager.
 * The singletons are bound explicitly rather than left to JIT so the composition root's graph lists everything the
 * window depends on. The {@link BuildVersion} value, the
 * {@link BackgroundExecutor} pool and the {@link DocumentPort}, {@link ProviderConfigs}, {@link ProviderVerifier},
 * {@link ModelCatalog}, {@link ChatModelFactory} and {@link TranslationEngine} ports are deliberately absent: the
 * composition root owns the first two, the {@code :document} and {@code :llm} modules implement the next five and
 * {@code :pipeline} the last, so this module requires all eight and a root that forgets one fails at injector-build
 * time rather than when About is first opened, a book is first imported, a run is started or the settings screen is
 * first shown.
 */
public final class UiModule extends AbstractModule {

    /** Installed by the composition root in {@code :app}. */
    public UiModule() {
        // Guice modules are constructed, not injected.
    }

    @Override
    protected void configure() {
        bind(ColorSchemeProvider.class).to(PlatformColorSchemeProvider.class);
        bind(LocaleProvider.class).to(OsLocaleProvider.class);
        bind(GuiceControllerFactory.class);
        bind(Navigator.class);
        bind(ModalHost.class);
        bind(ToastStack.class);
        bind(Toasts.class).to(ToastStack.class);
        bind(ModalErrorPresenter.class);
        bind(ErrorPresenter.class).to(ModalErrorPresenter.class);
        bind(AppShellView.class);
        bind(StateMirror.class);
        bind(TranslationRunner.class);
        bind(ModelListing.class);
        bind(SettingsViewModel.class);
        bind(ImportViewModel.class);
        bind(BookBriefViewModel.class);
        bind(TranslatingViewModel.class);
        bind(FileRevealer.class).to(PlatformFileRevealer.class);
        requireBinding(DocumentPort.class);
        requireBinding(ProviderConfigs.class);
        requireBinding(ProviderVerifier.class);
        requireBinding(ModelCatalog.class);
        requireBinding(ChatModelFactory.class);
        requireBinding(TranslationEngine.class);
        requireBinding(Key.get(String.class, BuildVersion.class));
        requireBinding(Key.get(ExecutorService.class, BackgroundExecutor.class));
    }
}
