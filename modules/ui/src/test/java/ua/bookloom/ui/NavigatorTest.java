package ua.bookloom.ui;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Injector;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import javafx.scene.Parent;
import javafx.scene.control.Label;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.state.ActivityKind;
import ua.bookloom.ui.state.ActivityTracker;
import ua.bookloom.ui.state.CurrentProject;
import ua.bookloom.ui.state.OpenBookForTest;
import ua.bookloom.ui.state.StepLocks;

/**
 * The navigator swaps the content region by enum constant and refuses an inert one. It is built by the real
 * {@link UiModule} graph and loads the real placeholder FXML on the headless toolkit.
 */
class NavigatorTest extends FxTestBase {

    private Navigator navigator;

    @Override
    public void start(final Stage stage) {
        // No window is needed; the toolkit only has to be running for the placeholder nodes to be constructed.
    }

    @BeforeEach
    void createNavigator() {
        navigator = UiTestInjector.create(Locale.ENGLISH).getInstance(Navigator.class);
    }

    private boolean navigateOnFxThread(final Navigator target, final ViewNames view) {
        final AtomicBoolean accepted = new AtomicBoolean();
        interact(() -> accepted.set(target.navigate(view)));
        return accepted.get();
    }

    private static String labelText(final Navigator target, final String id) {
        return ((Label) target.content().get().lookup(id)).getText();
    }

    @Test
    void currentView_beforeAnyNavigation_isEmpty() {
        // IF a view were current before anyone navigated, THEN the shell would mark an entry nobody chose.
        assertThat(navigator.currentView().get()).isNull();
        assertThat(navigator.content().get()).isNull();
    }

    @Test
    void navigate_reachableView_becomesCurrentAndShowsItsLabel() {
        // IF navigation were not wired to the loader, THEN the view would not become current or hold its content.
        final boolean accepted = navigateOnFxThread(navigator, ViewNames.EXPORT);

        assertThat(accepted).isTrue();
        assertThat(navigator.currentView().get()).isEqualTo(ViewNames.EXPORT);
        assertThat(navigator.content().get()).isInstanceOf(Parent.class);
        assertThat(navigator.content().get().lookup("#export-screen")).isNotNull();
    }

    // IF an inert entry could be activated, THEN the current view or the content would change for a screen that
    // does not exist.
    @ParameterizedTest
    @EnumSource(
            value = ViewNames.class,
            names = {"PROJECTS"})
    void navigate_inertView_isRefusedAndNothingChanges(final ViewNames inert) {
        navigateOnFxThread(navigator, ViewNames.IMPORT);
        final Parent before = navigator.content().get();

        final boolean accepted = navigateOnFxThread(navigator, inert);

        assertThat(accepted).isFalse();
        assertThat(navigator.currentView().get()).isEqualTo(ViewNames.IMPORT);
        assertThat(navigator.content().get()).isSameAs(before);
    }

    @Test
    void navigate_inertViewBeforeAnyNavigation_leavesTheCurrentViewEmpty() {
        // IF a refusal recorded its target, THEN the shell would mark an inert entry as current.
        final boolean accepted = navigateOnFxThread(navigator, ViewNames.PROJECTS);

        assertThat(accepted).isFalse();
        assertThat(navigator.currentView().get()).isNull();
        assertThat(navigator.content().get()).isNull();
    }

    @Test
    void navigate_toADifferentReachableView_replacesTheContent() {
        // IF the content region kept the previous screen, THEN the breadcrumb would move but the screen would not.
        navigateOnFxThread(navigator, ViewNames.IMPORT);
        final Parent first = navigator.content().get();

        final boolean accepted = navigateOnFxThread(navigator, ViewNames.EXPORT);

        assertThat(accepted).isTrue();
        assertThat(navigator.content().get()).isNotSameAs(first);
        assertThat(navigator.currentView().get()).isEqualTo(ViewNames.EXPORT);
        assertThat(navigator.content().get().lookup("#export-screen")).isNotNull();
    }

    @Test
    void navigate_theCurrentViewAgain_isRefusedAndKeepsTheSameContent() {
        // IF a repeat reloaded the view, THEN a screen would lose its state when its own entry was clicked twice.
        navigateOnFxThread(navigator, ViewNames.IMPORT);
        final Parent first = navigator.content().get();

        final boolean accepted = navigateOnFxThread(navigator, ViewNames.IMPORT);

        assertThat(accepted).isFalse();
        assertThat(navigator.content().get()).isSameAs(first);
    }

    @Test
    void currentView_listener_firesOncePerAcceptedNavigationOnly() {
        // IF a refusal or a repeat fired the property, THEN the shell would redraw and log a change that did not
        // happen.
        final List<ViewNames> seen = new ArrayList<>();
        navigator.currentView().addListener((observable, previous, current) -> seen.add(current));

        navigateOnFxThread(navigator, ViewNames.IMPORT);
        navigateOnFxThread(navigator, ViewNames.PROJECTS);
        navigateOnFxThread(navigator, ViewNames.IMPORT);
        navigateOnFxThread(navigator, ViewNames.BOOK_BRIEF);

        assertThat(seen).containsExactly(ViewNames.IMPORT, ViewNames.BOOK_BRIEF);
    }

    // IF the loader did not use the active catalogue, THEN a Ukrainian session would show the English label. The
    // export screen opens on its neutral heading, the navigation label, because no run has finished.
    @ParameterizedTest
    @CsvSource({
        "en, Export",
        "uk, Експорт",
    })
    void navigate_underEachLanguage_rendersThatLanguagesExportHeading(final String language, final String expected) {
        final Navigator localised =
                UiTestInjector.create(Locale.forLanguageTag(language)).getInstance(Navigator.class);

        navigateOnFxThread(localised, ViewNames.EXPORT);

        assertThat(labelText(localised, "#export-title")).isEqualTo(expected);
    }

    // IF Continue followed declaration order without skipping inert entries, THEN it would land on a screen that
    // does not exist.
    @ParameterizedTest
    @CsvSource({
        "IMPORT, BOOK_BRIEF",
        "BOOK_BRIEF, STRUCTURE",
        "STRUCTURE, NAMES_STYLE",
        "NAMES_STYLE, TRANSLATING",
        "TRANSLATING, EXPORT",
    })
    void nextAvailableStep_workflowEntry_skipsInertEntries(final ViewNames after, final ViewNames expected) {
        assertThat(navigator.nextAvailableStep(after)).isEqualTo(Optional.of(expected));
    }

    // IF Continue offered a next step past the end, or from outside the workflow, THEN it would leave the workflow.
    @ParameterizedTest
    @EnumSource(
            value = ViewNames.class,
            names = {"EXPORT", "SETTINGS"})
    void nextAvailableStep_lastStepOrNonWorkflowEntry_isEmpty(final ViewNames after) {
        assertThat(navigator.nextAvailableStep(after)).isEmpty();
    }

    // IF a click could leave the screen while blocking work runs, THEN the work's result would land on a screen nobody
    // is looking at; the refusal changes nothing, and navigation resumes when the work ends.
    @Test
    void navigate_blockingWorkRunning_isRefusedUntilItEnds() {
        final Injector graph = UiTestInjector.create(Locale.ENGLISH);
        final ActivityTracker tracker = graph.getInstance(ActivityTracker.class);
        final Navigator guarded = graph.getInstance(Navigator.class);
        final AtomicReference<ActivityTracker.Handle> handle = new AtomicReference<>();
        interact(() -> handle.set(tracker.begin(ActivityKind.EXPORT, null)));

        final boolean whileBusy = navigateOnFxThread(guarded, ViewNames.EXPORT);
        interact(() -> Objects.requireNonNull(handle.get()).end());
        final boolean afterwards = navigateOnFxThread(guarded, ViewNames.EXPORT);

        assertThat(whileBusy).isFalse();
        assertThat(afterwards).isTrue();
    }

    private Navigator guardedNavigator(final Injector graph, final RecordingToasts toasts) {
        return new Navigator(
                graph.getInstance(GuiceControllerFactory.class),
                graph.getInstance(Messages.class),
                graph.getInstance(LeaveGuard.class),
                graph.getInstance(ActivityTracker.class),
                graph.getInstance(StepLocks.class),
                toasts);
    }

    // IF a locked entry opened, THEN a screen would show a book that is not there; the toast is the click's feedback.
    @ParameterizedTest
    @EnumSource(
            value = ViewNames.class,
            names = {"BOOK_BRIEF", "STRUCTURE", "NAMES_STYLE", "TRANSLATING", "EXPORT"})
    void navigate_noBook_isRefusedWithAnInfoToastNamingTheReason(final ViewNames locked) {
        final Injector graph = UiTestInjector.builder(Locale.ENGLISH).gated().build();
        final RecordingToasts toasts = new RecordingToasts();
        final Navigator guarded = guardedNavigator(graph, toasts);

        final boolean accepted = navigateOnFxThread(guarded, locked);

        assertThat(accepted).isFalse();
        assertThat(guarded.currentView().get()).isNull();
        assertThat(toasts.raised())
                .containsExactly(new RecordingToasts.Raised("info", MessageKey.NAV_LOCKED_NO_BOOK, List.of()));
    }

    @Test
    void navigate_bookWithoutLanguages_refusesTranslatingButAllowsTheBrief() {
        final Injector graph = UiTestInjector.builder(Locale.ENGLISH).gated().build();
        final RecordingToasts toasts = new RecordingToasts();
        final Navigator guarded = guardedNavigator(graph, toasts);
        interact(() -> OpenBookForTest.open(graph.getInstance(CurrentProject.class), "en", null));

        final boolean translating = navigateOnFxThread(guarded, ViewNames.TRANSLATING);
        final boolean brief = navigateOnFxThread(guarded, ViewNames.BOOK_BRIEF);

        assertThat(translating).isFalse();
        assertThat(brief).isTrue();
        assertThat(toasts.raised())
                .containsExactly(new RecordingToasts.Raised("info", MessageKey.NAV_LOCKED_NO_LANGUAGES, List.of()));
    }

    @Test
    void navigate_importAndSettingsWithNoBook_areNeverLocked() {
        final Injector graph = UiTestInjector.builder(Locale.ENGLISH).gated().build();
        final RecordingToasts toasts = new RecordingToasts();
        final Navigator guarded = guardedNavigator(graph, toasts);

        assertThat(navigateOnFxThread(guarded, ViewNames.IMPORT)).isTrue();
        assertThat(navigateOnFxThread(guarded, ViewNames.SETTINGS)).isTrue();
        assertThat(toasts.raised()).isEmpty();
    }
}
