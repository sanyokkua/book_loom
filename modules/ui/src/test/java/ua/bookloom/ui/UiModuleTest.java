package ua.bookloom.ui;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Injector;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import ua.bookloom.ui.notify.ErrorPresenter;
import ua.bookloom.ui.notify.ToastStack;
import ua.bookloom.ui.notify.Toasts;
import ua.bookloom.ui.state.ImportViewModel;
import ua.bookloom.ui.state.SettingsViewModel;
import ua.bookloom.ui.state.StateMirror;
import ua.bookloom.ui.state.TranslatingViewModel;
import ua.bookloom.ui.state.TranslationRunner;

/** The bindings {@link UiModule} contributes to the composition root. */
class UiModuleTest extends ShellTestBase {

    @Test
    void navigator_requestedTwice_isTheSameInstance() {
        // IF the navigator were not a singleton, THEN the shell and a screen's Continue would navigate different hosts.
        final Injector injector = UiTestInjector.create(Locale.ENGLISH);

        assertThat(injector.getInstance(Navigator.class)).isSameAs(injector.getInstance(Navigator.class));
    }

    @Test
    void controllerFactory_requestedTwice_isTheSameInstance() {
        // IF the factory were rebuilt per request, THEN the "one sanctioned injector consumer" would be many.
        final Injector injector = UiTestInjector.create(Locale.ENGLISH);

        assertThat(injector.getInstance(GuiceControllerFactory.class))
                .isSameAs(injector.getInstance(GuiceControllerFactory.class));
    }

    @Test
    void navigator_twoInjectors_doNotShareState() {
        // IF the navigator were a static singleton, THEN a second application graph would inherit the first's view.
        assertThat(UiTestInjector.create(Locale.ENGLISH).getInstance(Navigator.class))
                .isNotSameAs(UiTestInjector.create(Locale.ENGLISH).getInstance(Navigator.class));
    }

    @Test
    void toasts_requestedTwice_isTheSameInstanceAsTheStack() {
        // IF Toasts were rebuilt per request, THEN a screen would raise messages into a stack no shell displays.
        final Injector injector = UiTestInjector.create(Locale.ENGLISH);

        assertThat(injector.getInstance(Toasts.class))
                .isSameAs(injector.getInstance(Toasts.class))
                .isSameAs(injector.getInstance(ToastStack.class));
    }

    @Test
    void errorPresenter_requestedTwice_isTheSameInstance() {
        // IF the presenter were rebuilt per request, THEN two screens would each hold their own dialog logic.
        final Injector injector = UiTestInjector.create(Locale.ENGLISH);

        assertThat(injector.getInstance(ErrorPresenter.class)).isSameAs(injector.getInstance(ErrorPresenter.class));
    }

    @Test
    void stateMirror_requestedTwice_isTheSameInstance() {
        // IF the mirror were rebuilt per request, THEN the runner would publish into a mirror no screen observes.
        final Injector injector = UiTestInjector.create(Locale.ENGLISH);

        assertThat(injector.getInstance(StateMirror.class)).isSameAs(injector.getInstance(StateMirror.class));
    }

    @Test
    void translationRunner_requestedTwice_isTheSameInstance() {
        // IF the runner were rebuilt per request, THEN a second screen could start a run beside the first one.
        final Injector injector = UiTestInjector.create(Locale.ENGLISH);

        assertThat(injector.getInstance(TranslationRunner.class))
                .isSameAs(injector.getInstance(TranslationRunner.class));
    }

    @Test
    void settingsViewModel_requestedTwice_isTheSameInstance() {
        // IF the view model were rebuilt per request, THEN the chosen provider would be lost on every visit to
        // settings.
        final Injector injector = UiTestInjector.create(Locale.ENGLISH);

        assertThat(injector.getInstance(SettingsViewModel.class))
                .isSameAs(injector.getInstance(SettingsViewModel.class));
    }

    @Test
    void importViewModel_requestedTwice_isTheSameInstance() {
        // IF the view model were rebuilt per request, THEN the open book would be lost every time the import screen
        // is rebuilt, and the screens after it would read nothing.
        final Injector injector = UiTestInjector.create(Locale.ENGLISH);

        assertThat(injector.getInstance(ImportViewModel.class)).isSameAs(injector.getInstance(ImportViewModel.class));
    }

    @Test
    void translatingViewModel_requestedTwice_isTheSameInstance() {
        // IF the view model were rebuilt per request, THEN a second screen visit would register a second completion
        // listener on the mirror and raise every toast twice.
        final Injector injector = UiTestInjector.create(Locale.ENGLISH);

        assertThat(injector.getInstance(TranslatingViewModel.class))
                .isSameAs(injector.getInstance(TranslatingViewModel.class));
    }

    @Test
    void toasts_view_isTheNodeTheShellHolds() {
        // IF the shell built its own toast host, THEN a toast raised through Toasts would never appear on screen.
        // (uses the shell the test base built, which needs the FX toolkit)
        assertThat(shell.root().getChildrenUnmodifiable())
                .contains(injector.getInstance(ToastStack.class).view());
    }
}
