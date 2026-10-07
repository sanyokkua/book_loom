package ua.bookloom.ui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.inject.CreationException;
import com.google.inject.Guice;
import com.google.inject.Injector;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import ua.bookloom.api.pipeline.ExportService;
import ua.bookloom.api.pipeline.GlossaryService;
import ua.bookloom.api.pipeline.ProjectService;
import ua.bookloom.api.pipeline.ReviewDesk;
import ua.bookloom.api.pipeline.ReviewMode;
import ua.bookloom.ui.notify.ErrorPresenter;
import ua.bookloom.ui.notify.ToastStack;
import ua.bookloom.ui.notify.Toasts;
import ua.bookloom.ui.state.CurrentProject;
import ua.bookloom.ui.state.ImportViewModel;
import ua.bookloom.ui.state.SettingsViewModel;
import ua.bookloom.ui.state.StateMirror;
import ua.bookloom.ui.state.TranslatingViewModel;
import ua.bookloom.ui.state.TranslationRunner;

/** The bindings {@link UiModule} contributes to the composition root. */
class UiModuleTest extends ShellTestBase {

    @ParameterizedTest
    @ValueSource(
            classes = {
                Navigator.class,
                GuiceControllerFactory.class,
                ErrorPresenter.class,
                StateMirror.class,
                TranslationRunner.class,
                SettingsViewModel.class,
                CurrentProject.class,
                ImportViewModel.class,
                TranslatingViewModel.class
            })
    void singletonBinding_requestedTwice_isTheSameInstance(final Class<?> type) {
        // IF a type were rebuilt per request, THEN two screens would each hold their own copy of its state.
        final Injector injector = UiTestInjector.create(Locale.ENGLISH);

        assertThat(injector.getInstance(type)).isSameAs(injector.getInstance(type));
    }

    // IF a required port were missing from the test graph, THEN a screen test would fail on first use, not at boot.
    @ParameterizedTest
    @ValueSource(classes = {ProjectService.class, GlossaryService.class, ReviewDesk.class, ExportService.class})
    void uiTestInjector_requiredBindings_resolveTheFakes(final Class<?> port) {
        final Injector injector = UiTestInjector.create(Locale.ENGLISH);

        assertThat(injector.getInstance(port)).isNotNull().isSameAs(injector.getInstance(port));
    }

    // WHEN a test builds the graph, THEN the review mode is the Unattended default no run pauses for.
    @Test
    void uiTestInjector_reviewMode_isUnattended() {
        assertThat(UiTestInjector.create(Locale.ENGLISH).getInstance(ReviewMode.class))
                .isEqualTo(ReviewMode.UNATTENDED);
    }

    // IF the composition root forgot a port, THEN the injector fails to build and names it, instead of the window
    // failing when a screen first asks for it.
    @ParameterizedTest
    @ValueSource(strings = {"ProjectService", "GlossaryService", "ReviewDesk", "ExportService", "ReviewMode"})
    void uiModule_portNotBound_failsWhenTheInjectorIsBuilt(final String port) {
        assertThatThrownBy(() -> Guice.createInjector(new UiModule()))
                .isInstanceOf(CreationException.class)
                .hasMessageContaining(port);
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
    void toasts_view_isTheNodeTheShellHolds() {
        // IF the shell built its own toast host, THEN a toast raised through Toasts would never appear on screen.
        // (uses the shell the test base built, which needs the FX toolkit)
        assertThat(shell.root().lookup("#shell-toast-host"))
                .isSameAs(injector.getInstance(ToastStack.class).view());
    }
}
