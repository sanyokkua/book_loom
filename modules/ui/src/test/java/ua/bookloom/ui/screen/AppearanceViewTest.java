package ua.bookloom.ui.screen;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Injector;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Labeled;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.ToggleButton;
import org.controlsfx.control.SegmentedButton;
import org.controlsfx.control.ToggleSwitch;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.testfx.util.WaitForAsyncUtils;
import ua.bookloom.api.Result;
import ua.bookloom.api.settings.AppSetting;
import ua.bookloom.api.settings.ThemeSetting;
import ua.bookloom.ui.MemorySettings;
import ua.bookloom.ui.RecordingAppRestart;
import ua.bookloom.ui.ShellTestBase;
import ua.bookloom.ui.ThemeTestSupport;
import ua.bookloom.ui.UiTestInjector;
import ua.bookloom.ui.ViewNames;
import ua.bookloom.ui.i18n.LanguageChoice;
import ua.bookloom.ui.state.RunState;
import ua.bookloom.ui.state.StateMirror;
import ua.bookloom.ui.theme.ThemeMode;

/**
 * The appearance area of the settings screen as the application builds it: the light / dark / system selector, the
 * language choice with its restart, the technical-details switch, and what must not be there. The shell's operating
 * system always reports the light scheme, so "system" resolves to light.
 */
class AppearanceViewTest extends ShellTestBase {

    private static final long WAIT_SECONDS = 10;

    private final MemorySettings store = new MemorySettings();
    private final RecordingAppRestart restart = new RecordingAppRestart();
    private final Locale osLocale = Locale.ENGLISH;
    private boolean restartFails;

    @Override
    protected Injector createInjector(final Locale locale) {
        return UiTestInjector.builder(locale)
                .settings(store)
                .restart(restartFails ? new RecordingAppRestart(Result.ok(false)) : restart)
                .osLocale(osLocale)
                .build();
    }

    private void showAppearance() {
        onFx(() -> shell.activate(ViewNames.SETTINGS));
        final TabPane tabs = (TabPane) required("settings-tabs");
        final Tab appearance = tabs.getTabs().stream()
                .filter(tab -> "settings-tab-appearance".equals(tab.getId()))
                .findFirst()
                .orElseThrow();
        onFx(() -> tabs.getSelectionModel().select(appearance));
    }

    private ToggleButton toggle(final String id) {
        return (ToggleButton) required(id);
    }

    // IF the selector were missing an option, or offered them in another order or under other names, THEN a person
    // could not reach a theme the specification promises, or would meet a different vocabulary than the reference.
    @Test
    void themeSelector_appearanceShown_offersLightDarkAndMatchSystemInOrder() {
        showAppearance();

        assertThat(required("settings-appearance-host").lookup(".segmented-button"))
                .isInstanceOf(SegmentedButton.class);
        assertThat(List.of(toggle("appearance-light"), toggle("appearance-dark"), toggle("appearance-system")))
                .extracting(ToggleButton::getText)
                .containsExactly("Light", "Dark", "Match the computer");
        assertThat(((SegmentedButton) required("settings-appearance-host").lookup(".segmented-button")).getButtons())
                .extracting(Node::getId)
                .containsExactly("appearance-light", "appearance-dark", "appearance-system");
    }

    // IF the selector did not show the theme already in force, THEN it would claim a choice the window is not
    // wearing.
    @Test
    void themeSelector_lightModeInForce_onlyLightIsSelected() {
        showAppearance();

        assertThat(toggle("appearance-light").isSelected()).isTrue();
        assertThat(toggle("appearance-dark").isSelected()).isFalse();
        assertThat(toggle("appearance-system").isSelected()).isFalse();
    }

    // IF choosing an option did not set the mode, or the scene did not repaint with the matching block, THEN the
    // selector would be decoration; "system" must follow the operating system, which here reports light.
    @ParameterizedTest
    @CsvSource({
        "appearance-dark, LIGHT, DARK, #283237",
        "appearance-light, DARK, LIGHT, #f4f1ea",
        "appearance-system, DARK, SYSTEM, #f4f1ea",
    })
    void themeSelector_optionChosen_setsModeRepaintsAndSelectsIt(
            final String optionId,
            final ThemeMode startMode,
            final ThemeMode expectedMode,
            final String backgroundHex) {
        onFx(() -> themeController.setMode(startMode));
        showAppearance();

        onFx(() -> toggle(optionId).fire());

        assertThat(ThemeTestSupport.onFx(themeController::mode)).isEqualTo(expectedMode);
        assertThat(toggle(optionId).isSelected()).isTrue();
        ThemeTestSupport.assertSameColour(ThemeTestSupport.resolveRole(scene, "bg"), backgroundHex, "bg");
    }

    // IF two options could read as selected at once, THEN the selector would not say which theme is in force.
    @Test
    void themeSelector_darkChosen_deselectsTheOthers() {
        showAppearance();

        onFx(() -> toggle("appearance-dark").fire());

        assertThat(toggle("appearance-light").isSelected()).isFalse();
        assertThat(toggle("appearance-system").isSelected()).isFalse();
    }

    // IF the title-bar toggle changed the theme without the selector following, THEN the two places the specification
    // gives for choosing a theme would disagree with each other.
    @Test
    void themeSelector_titleBarToggleUsed_selectionFollows() {
        showAppearance();
        assertThat(toggle("appearance-light").isSelected()).isTrue();

        onFx(themeController::toggle);
        assertThat(toggle("appearance-dark").isSelected()).isTrue();
        assertThat(toggle("appearance-light").isSelected()).isFalse();

        onFx(themeController::toggle);
        assertThat(toggle("appearance-light").isSelected()).isTrue();
        assertThat(toggle("appearance-dark").isSelected()).isFalse();
    }

    // IF clicking the selected option again left the selector with nothing selected, THEN it would stop saying which
    // theme is in force while the mode had not changed at all.
    @Test
    void themeSelector_selectedOptionClickedAgain_staysSelectedAndModeUnchanged() {
        showAppearance();

        onFx(() -> toggle("appearance-light").fire());

        assertThat(Stream.of("appearance-light", "appearance-dark", "appearance-system")
                        .filter(id -> toggle(id).isSelected()))
                .containsExactly("appearance-light");
        assertThat(ThemeTestSupport.onFx(themeController::mode)).isEqualTo(ThemeMode.LIGHT);
    }

    // IF a mode set from elsewhere, such as follow-the-system, did not move the selection, THEN the selector would
    // show a stale choice.
    @Test
    void themeSelector_modeSetToSystemElsewhere_systemIsSelected() {
        showAppearance();

        onFx(() -> themeController.setMode(ThemeMode.SYSTEM));

        assertThat(toggle("appearance-system").isSelected()).isTrue();
        assertThat(toggle("appearance-light").isSelected()).isFalse();
    }

    private void awaitStored(final AppSetting<?> setting, final boolean present) throws TimeoutException {
        WaitForAsyncUtils.waitFor(
                WAIT_SECONDS, TimeUnit.SECONDS, () -> store.stored(setting).isPresent() == present);
    }

    private void awaitDarkStored() throws TimeoutException {
        WaitForAsyncUtils.waitFor(
                WAIT_SECONDS,
                TimeUnit.SECONDS,
                () -> store.stored(AppSetting.THEME)
                        .filter(ThemeSetting.DARK::equals)
                        .isPresent());
    }

    @SuppressWarnings("unchecked")
    private ComboBox<LanguageChoice> languageBox() {
        return (ComboBox<LanguageChoice>) required("appearance-language");
    }

    private void pickLanguage(final LanguageChoice choice) {
        onFx(() -> languageBox().getSelectionModel().select(choice));
    }

    private boolean shown(final String id) {
        final Node node = scene.getRoot().lookup("#" + id);
        return node != null && node.isVisible() && node.isManaged();
    }

    private String textOf(final String id) {
        return ThemeTestSupport.onFx(() -> ((Labeled) required(id)).getText());
    }

    // IF the accent row stayed, THEN a person would meet a colour "choice" that this version does not offer.
    @Test
    void appearance_opened_hasNoAccentRow() {
        showAppearance();

        assertThat(scene.getRoot().lookup("#appearance-accent-row")).isNull();
        assertThat(textsUnder(required("settings-appearance-host")))
                .noneMatch(text -> text.toLowerCase(Locale.ROOT).contains("accent"));
    }

    // IF the tab did not say its changes apply at once, THEN a person would look for a Save button that is not there.
    @Test
    void appearance_opened_saysChangesApplyAtOnce() {
        showAppearance();

        assertThat(textOf("appearance-instant")).isEqualTo("\u2713 Changes apply at once.");
    }

    // IF a chosen theme repainted but was not stored, THEN the next launch would open in the old theme.
    @Test
    void theme_darkChosen_darkAtOnceAndStoredDark() throws TimeoutException {
        showAppearance();

        onFx(() -> toggle("appearance-dark").fire());

        ThemeTestSupport.assertSameColour(ThemeTestSupport.resolveRole(scene, "bg"), "#283237", "bg");
        awaitDarkStored();
        assertThat(store.stored(AppSetting.THEME)).contains(ThemeSetting.DARK);
    }

    // IF the title-bar toggle did not store the theme, THEN the two ways of choosing one would not agree on restart.
    @Test
    void titleBarToggle_used_storedDark() throws TimeoutException {
        showAppearance();

        onFx(themeController::toggle);

        awaitDarkStored();
        assertThat(store.stored(AppSetting.THEME)).contains(ThemeSetting.DARK);
    }

    // IF a language change repainted the window now, THEN it would mix two languages on one screen; it applies after a
    // restart, and the screen must say so and offer the restart.
    @Test
    void language_ukChosenInEnglishWindow_staysEnglishNoteAndRestartNowShown() {
        showAppearance();
        assertThat(shown("appearance-restart")).isFalse();

        pickLanguage(LanguageChoice.UKRAINIAN);

        assertThat(textOf("appearance-language-note")).isEqualTo("Applies after BookLoom restarts.");
        assertThat(shown("appearance-restart")).isTrue();
        assertThat(textOf("appearance-restart")).isEqualTo("Restart now");
        assertThat(textOf("appearance-instant")).isEqualTo("\u2713 Changes apply at once.");
    }

    // IF Restart now showed for the language already on screen, THEN it would restart for nothing.
    @Test
    void language_englishInEnglishWindow_restartNowHidden() {
        showAppearance();
        pickLanguage(LanguageChoice.UKRAINIAN);

        pickLanguage(LanguageChoice.ENGLISH);

        assertThat(shown("appearance-restart")).isFalse();
    }

    // IF following the computer kept a stored language, THEN the computer's language could never win again.
    @Test
    void language_systemChosen_storedRemoved() throws TimeoutException {
        showAppearance();
        pickLanguage(LanguageChoice.UKRAINIAN);
        awaitStored(AppSetting.LANGUAGE, true);

        pickLanguage(LanguageChoice.SYSTEM);

        awaitStored(AppSetting.LANGUAGE, false);
    }

    // IF the follow-the-computer option did not name the language it would give, THEN a person could not tell it from
    // the other two.
    @Test
    void language_systemOption_namesComputerLanguage() {
        showAppearance();

        final String label = ThemeTestSupport.onFx(() -> {
            final ComboBox<LanguageChoice> box = languageBox();
            return box.getConverter().toString(LanguageChoice.SYSTEM);
        });

        assertThat(label).isEqualTo("Match the computer (English)");
    }

    // IF Restart now ran before the choice was stored, THEN the new copy would open in the old language.
    @Test
    void restartNow_clicked_restartCalledAfterWrite() throws TimeoutException {
        showAppearance();
        pickLanguage(LanguageChoice.UKRAINIAN);

        onFx(() -> ((Button) required("appearance-restart")).fire());

        WaitForAsyncUtils.waitFor(WAIT_SECONDS, TimeUnit.SECONDS, () -> restart.calls() == 1);
        assertThat(store.stored(AppSetting.LANGUAGE)).contains("uk");
    }

    @Test
    void restartNow_clickedTwice_restartCalledOnce() throws TimeoutException {
        showAppearance();
        pickLanguage(LanguageChoice.UKRAINIAN);

        onFx(() -> ((Button) required("appearance-restart")).fire());
        WaitForAsyncUtils.waitFor(WAIT_SECONDS, TimeUnit.SECONDS, () -> restart.calls() == 1);
        onFx(() -> ((Button) required("appearance-restart")).fire());
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(restart.calls()).isEqualTo(1);
        assertThat(ThemeTestSupport.onFx(() -> required("appearance-restart").isDisabled()))
                .isTrue();
    }

    // IF the button kept saying "Restart now" while the window closed, THEN the seconds before it closes would look
    // like nothing happened.
    @Test
    void restartNow_underWay_saysRestarting() throws TimeoutException {
        showAppearance();
        pickLanguage(LanguageChoice.UKRAINIAN);

        onFx(() -> ((Button) required("appearance-restart")).fire());
        WaitForAsyncUtils.waitFor(WAIT_SECONDS, TimeUnit.SECONDS, () -> restart.calls() == 1);
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(textOf("appearance-restart")).isEqualTo("Restarting...");
    }

    @Test
    void theme_storeRefuses_saveErrorShownAndThemeHolds() throws TimeoutException {
        store.failPuts();
        showAppearance();

        onFx(() -> toggle("appearance-dark").fire());

        WaitForAsyncUtils.waitFor(
                WAIT_SECONDS, TimeUnit.SECONDS, () -> ThemeTestSupport.onFx(() -> shown("appearance-save-error")));
        assertThat(ThemeTestSupport.onFx(themeController::mode)).isEqualTo(ThemeMode.DARK);
    }

    // IF a restart skipped the quit question, THEN a running translation would be killed without a word.
    @Test
    void restartNow_runUnderWay_asksQuitAndRestartsOnlyAfterConfirm() throws TimeoutException {
        showAppearance();
        pickLanguage(LanguageChoice.UKRAINIAN);
        onFx(() -> injector.getInstance(StateMirror.class).publishRunState(RunState.RUNNING));

        onFx(() -> ((Button) required("appearance-restart")).fire());

        assertThat(scene.getRoot().lookup("#confirm-card")).isNotNull();
        assertThat(restart.calls()).isZero();
        onFx(() -> ((Button) required("confirm-yes")).fire());
        WaitForAsyncUtils.waitFor(WAIT_SECONDS, TimeUnit.SECONDS, () -> restart.calls() == 1);
    }

    // IF a restart that could not start were silent, THEN the person would wait for a window that never comes.
    @Test
    void restartNow_restartFails_errorShown() throws TimeoutException {
        restartFails = true;
        useLocale(Locale.ENGLISH);
        showAppearance();
        pickLanguage(LanguageChoice.UKRAINIAN);

        onFx(() -> ((Button) required("appearance-restart")).fire());

        WaitForAsyncUtils.waitFor(
                WAIT_SECONDS, TimeUnit.SECONDS, () -> ThemeTestSupport.onFx(() -> shown("appearance-restart-error")));
        assertThat(textOf("appearance-restart-error"))
                .isEqualTo("BookLoom could not start again by itself. Close it and open it again.");
    }

    // IF the switch did not store, THEN technical details would be off again after the next launch.
    @Test
    void technical_switchedOn_storedTrue() throws TimeoutException {
        showAppearance();

        onFx(() -> ((ToggleSwitch) required("appearance-technical")).setSelected(true));

        WaitForAsyncUtils.waitFor(
                WAIT_SECONDS,
                TimeUnit.SECONDS,
                () -> store.stored(AppSetting.TECHNICAL_DETAILS).isPresent());
        assertThat(store.stored(AppSetting.TECHNICAL_DETAILS)).contains(true);
    }

    // IF the switch ignored the stored value, THEN it would show off while every screen shows technical names.
    @Test
    void technical_storedOn_switchOnWhenOpened() throws TimeoutException {
        store.put(AppSetting.TECHNICAL_DETAILS, true);
        useLocale(Locale.ENGLISH);
        showAppearance();

        WaitForAsyncUtils.waitFor(
                WAIT_SECONDS,
                TimeUnit.SECONDS,
                () -> ThemeTestSupport.onFx(() -> ((ToggleSwitch) required("appearance-technical")).isSelected()));
    }
}
