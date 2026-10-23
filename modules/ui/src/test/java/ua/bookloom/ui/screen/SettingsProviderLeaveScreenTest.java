package ua.bookloom.ui.screen;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.TimeoutException;
import javafx.scene.control.Label;
import org.junit.jupiter.api.Test;
import ua.bookloom.ui.ThemeTestSupport;
import ua.bookloom.ui.ViewNames;

/** Leaving the Providers form with unsaved typing: another provider, another tab, another screen. */
class SettingsProviderLeaveScreenTest extends SettingsScreenTestBase {

    private String selectedTabId() {
        return ThemeTestSupport.onFx(
                () -> tabs().getSelectionModel().getSelectedItem().getId());
    }

    private boolean questionShown() {
        return scene.getRoot().lookup("#unsaved-card") != null;
    }

    private void typeAndOpenAppearance() {
        openSettings();
        setField("settings-form-request-timeout", "240");
        clickOn("#settings-tab-appearance");
    }

    // IF another tab opened over unsaved typing, THEN the typing would be lost without a word.
    @Test
    void tabChange_formDirty_asksAndStaysOnProviders() {
        typeAndOpenAppearance();

        assertThat(questionShown()).isTrue();
        assertThat(selectedTabId()).isEqualTo("settings-tab-providers");
        assertThat(field("settings-form-request-timeout").getText()).isEqualTo("240");
    }

    // IF Discard asked a second time, or left the tab where it was, THEN the person would answer twice for one move.
    @Test
    void tabChange_discardAnswered_opensTheTabOnceWithoutAskingAgain() {
        typeAndOpenAppearance();

        onFx(() -> button("unsaved-discard").fire());

        assertThat(questionShown()).isFalse();
        assertThat(selectedTabId()).isEqualTo("settings-tab-appearance");
        assertThat(form().dirty().get()).isFalse();
    }

    @Test
    void tabChange_keepEditingAnswered_staysWithTheTypingIntact() {
        typeAndOpenAppearance();

        onFx(() -> button("unsaved-keep").fire());

        assertThat(selectedTabId()).isEqualTo("settings-tab-providers");
        assertThat(field("settings-form-request-timeout").getText()).isEqualTo("240");
        assertThat(questionShown()).isFalse();
    }

    @Test
    void tabChange_saveAnswered_savesThenOpensTheTab() throws TimeoutException {
        typeAndOpenAppearance();

        onFx(() -> button("unsaved-save").fire());
        awaitFx(() -> "settings-tab-appearance"
                .equals(tabs().getSelectionModel().getSelectedItem().getId()));

        assertThat(configs.saves()).isEqualTo(1);
        assertThat(questionShown()).isFalse();
    }

    // IF a clean form asked, THEN every tab would cost a click for nothing.
    @Test
    void tabChange_formClean_opensTheTabAtOnce() {
        openSettings();

        clickOn("#settings-tab-appearance");

        assertThat(questionShown()).isFalse();
        assertThat(selectedTabId()).isEqualTo("settings-tab-appearance");
    }

    // IF the question did not name the provider, THEN the person could not tell which typing is at stake.
    @Test
    void rowSwitch_formDirty_asksNamingTheProviderAndKeepsTheOpenOne() {
        openSettings();
        setField("settings-form-request-timeout", "240");

        onFx(() -> required("provider-row-lmstudio").fireEvent(RowClicks.primary()));

        assertThat(((Label) scene.getRoot().lookup("#unsaved-card .dialog-title")).getText())
                .isEqualTo("Save the changes to Ollama?");
        assertThat(ThemeTestSupport.onFx(() -> viewModel().editedProviderId().get()))
                .isEqualTo("ollama");
    }

    @Test
    void rowSwitch_discardAnswered_opensTheOtherProviderClean() {
        openSettings();
        setField("settings-form-request-timeout", "240");
        onFx(() -> required("provider-row-lmstudio").fireEvent(RowClicks.primary()));

        onFx(() -> button("unsaved-discard").fire());

        assertThat(text("settings-form-title")).isEqualTo("LM Studio");
        assertThat(form().dirty().get()).isFalse();
        assertThat(questionShown()).isFalse();
    }

    // IF the Add button skipped the question, THEN a new form would replace typing that was never saved.
    @Test
    void add_formDirty_asksFirst() {
        openSettings();
        setField("settings-form-request-timeout", "240");

        onFx(() -> button("settings-provider-add").fire());

        assertThat(questionShown()).isTrue();
        assertThat(text("settings-form-title")).isEqualTo("Ollama");
    }

    // IF leaving Settings for a workflow screen did not ask, THEN the typing would be lost in the most common move.
    @Test
    void navigation_formDirty_asksKeepDiscardOrSaveAndKeepStaysOnSettings() {
        openSettings();
        setField("settings-form-request-timeout", "240");

        onFx(() -> shell.activate(ViewNames.PROJECTS));

        assertThat(questionShown()).isTrue();
        onFx(() -> button("unsaved-keep").fire());
        assertThat(field("settings-form-request-timeout").getText()).isEqualTo("240");
    }
}
