package ua.bookloom.ui;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Injector;
import java.util.Locale;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import ua.bookloom.ui.state.CurrentProject;
import ua.bookloom.ui.state.OpenBookForTest;

/** The navigation column shows a locked step as locked, says why on hover and still answers a click. */
class NavLockingTest extends ShellTestBase {

    private static final String LOCKED = "nav-item-locked";

    @Override
    protected Injector createInjector(final Locale locale) {
        return UiTestInjector.builder(locale).gated().build();
    }

    private void openBook(final String source, final @Nullable String target) {
        final CurrentProject project = injector.getInstance(CurrentProject.class);
        onFx(() -> OpenBookForTest.open(project, source, target));
    }

    @ParameterizedTest
    @ValueSource(strings = {"nav-book-brief", "nav-structure", "nav-names-style", "nav-translating", "nav-export"})
    void navigation_noBook_lockedEntriesCarryTheStyleAndSayOpenABookFirst(final String id) {
        // IF the column did not follow the lock, THEN every step would look available with no book open.
        final Button entry = (Button) required(id);

        assertThat(entry.getStyleClass()).contains(LOCKED);
        assertThat(entry.isDisabled()).isFalse();
        assertThat(TooltipProbe.tipText(entry)).isEqualTo("Open a book first");
    }

    @ParameterizedTest
    @ValueSource(strings = {"nav-import", "nav-settings"})
    void navigation_noBook_importAndSettingsAreNotLocked(final String id) {
        final Button entry = (Button) required(id);

        assertThat(entry.getStyleClass()).doesNotContain(LOCKED);
        assertThat(TooltipProbe.tipText(entry))
                .isNotEqualTo("Open a book first")
                .isNotBlank();
    }

    @Test
    void navigation_noBook_clickingALockedEntryChangesNothing() {
        onFx(() -> ((Button) required("nav-translating")).fire());

        assertThat(navigator.currentView().get()).isNull();
    }

    @Test
    void navigation_bookWithoutLanguages_locksOnlyTheLanguageStepsAndNamesTheBrief() {
        openBook("en", null);

        assertThat(required("nav-book-brief").getStyleClass()).doesNotContain(LOCKED);
        assertThat(required("nav-export").getStyleClass()).doesNotContain(LOCKED);
        assertThat(required("nav-structure").getStyleClass()).contains(LOCKED);
        assertThat(TooltipProbe.tipText(required("nav-translating"))).isEqualTo("Choose the languages in Book Brief");
    }

    @Test
    void navigation_languagesChosen_unlocksAndRestoresTheOrdinaryExplanation() {
        openBook("en", null);
        openBook("en", "uk");

        final Button translating = (Button) required("nav-translating");
        assertThat(translating.getStyleClass()).doesNotContain(LOCKED);
        assertThat(TooltipProbe.tipText(translating)).isNotEqualTo("Choose the languages in Book Brief");
    }

    @Test
    void navigation_underUkrainian_theReasonIsInUkrainian() {
        useLocale(Locale.forLanguageTag("uk"));

        assertThat(TooltipProbe.tipText(required("nav-export"))).isEqualTo("Спершу відкрийте книгу");
    }

    private void show(final ViewNames view) {
        onFx(() -> navigator.navigate(view));
    }

    private void press(final String id) {
        onFx(() -> ((Button) required(id)).fire());
    }

    // IF a footer's Continue could step into a locked screen, THEN it would bypass the lock the navigation column
    // keeps; it stands disabled beside the same reason, pressing it goes nowhere, and the footer's Back always leads
    // out.
    @Test
    void footer_continueIntoALockedStep_isDisabledWithTheReasonAndBackLeadsOut() {
        openBook("en", null);
        show(ViewNames.BOOK_BRIEF);

        press("brief-continue");

        assertThat(navigator.currentView().get()).isEqualTo(ViewNames.BOOK_BRIEF);
        assertThat(required("brief-continue").isDisabled()).isTrue();
        assertThat(((Label) required("brief-continue-hint")).getText()).isEqualTo("Choose the languages in Book Brief");
        press("brief-back");
        assertThat(navigator.currentView().get()).isEqualTo(ViewNames.IMPORT);
    }

    // IF the lock outlived the choice it waited for, THEN Continue would stay refused after the languages were set.
    @Test
    void footer_continueOnceTheLanguagesAreChosen_opensTheNextStepAndItsBackReturns() {
        openBook("en", "uk");
        show(ViewNames.BOOK_BRIEF);

        press("brief-continue");
        assertThat(navigator.currentView().get()).isEqualTo(ViewNames.STRUCTURE);

        press("structure-back");
        assertThat(navigator.currentView().get()).isEqualTo(ViewNames.BOOK_BRIEF);
    }
}
