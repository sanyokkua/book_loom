package ua.bookloom.ui.screen;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.TimeoutException;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.llm.TemperatureChoice;
import ua.bookloom.ui.ThemeTestSupport;
import ua.bookloom.ui.ViewNames;

/** Changing, saving and leaving the Models tab with unsaved changes. */
class SettingsModelsLeaveScreenTest extends SettingsModelsScreenTestBase {

    private String selectedTabId() {
        return ThemeTestSupport.onFx(
                () -> tabs().getSelectionModel().getSelectedItem().getId());
    }

    private boolean questionShown() {
        return scene.getRoot().lookup("#unsaved-card") != null;
    }

    private void changeTemperature() {
        onFx(() -> ((javafx.scene.control.ToggleButton) required("models-temperature-omit")).fire());
    }

    private String storedChoice(final String model) {
        return String.valueOf(modelSettings
                .stored("ollama", model)
                .map(s -> s.temperatureChoice())
                .orElse(null));
    }

    // IF a change did not mark the footer, THEN a person could not tell it was not yet kept.
    @Test
    void change_temperatureOmit_footerDirtyAndStoreUntouched() throws TimeoutException {
        openModels();

        changeTemperature();

        assertThat(visible("models-form-dirty")).isTrue();
        assertThat(((Button) required("models-form-save")).isDisabled()).isFalse();
        assertThat(modelSettings.updateCount()).isZero();
    }

    @Test
    void save_afterChange_storesAndClearsTheMark() throws TimeoutException {
        openModels();
        changeTemperature();

        press("models-form-save");
        awaitFx(() -> !modelForm().saving().get());

        assertThat(storedChoice(GEMMA)).isEqualTo(TemperatureChoice.OMIT.name());
        assertThat(visible("models-form-dirty")).isFalse();
        assertThat(visible("models-form-saved")).isTrue();
    }

    @Test
    void cancel_afterChange_restoresWhatIsSaved() throws TimeoutException {
        openModels();
        changeTemperature();

        press("models-form-cancel");

        assertThat(visible("models-form-dirty")).isFalse();
        assertThat(modelSettings.updateCount()).isZero();
        assertThat(shown("models-temperature-hint")).startsWith("Each task uses its own tuned value");
    }

    // IF another tab opened over unsaved changes, THEN they would be lost without a word.
    @Test
    void tabChange_modelDirty_asksAndStaysUntilDiscard() throws TimeoutException {
        openModels();
        changeTemperature();

        clickOn("#settings-tab-appearance");

        assertThat(questionShown()).isTrue();
        assertThat(selectedTabId()).isEqualTo("settings-tab-models");
        onFx(() -> button("unsaved-discard").fire());
        assertThat(questionShown()).isFalse();
        assertThat(selectedTabId()).isEqualTo("settings-tab-appearance");
        assertThat(modelForm().dirty().get()).isFalse();
    }

    // IF the question named no model, THEN the person could not tell which changes were at stake.
    @Test
    void modelSwitch_modelDirty_asksNamingTheModelAndKeepsIt() throws TimeoutException {
        openModels();
        changeTemperature();

        onFx(() -> modelsCombo().getSelectionModel().select(1));

        assertThat(((Label) scene.getRoot().lookup("#unsaved-card .dialog-title")).getText())
                .isEqualTo("Save the settings of gemma4:e4b · Ollama?");
        assertThat(ThemeTestSupport.onFx(() -> modelsCombo().getValue().modelId()))
                .isEqualTo(GEMMA);
    }

    @Test
    void modelSwitch_discardAnswered_showsTheOtherModelCleanAndAsksOnce() throws TimeoutException {
        openModels();
        changeTemperature();
        onFx(() -> modelsCombo().getSelectionModel().select(1));

        onFx(() -> button("unsaved-discard").fire());
        awaitFx(() -> GPT_OSS.equals(modelForm().selected().get().modelId())
                && !modelForm().facts().get().loading());

        assertThat(questionShown()).isFalse();
        assertThat(modelForm().dirty().get()).isFalse();
        assertThat(ThemeTestSupport.onFx(() -> modelsCombo().getValue().modelId()))
                .isEqualTo(GPT_OSS);
    }

    // IF Providers did not guard the Models draft the other way, THEN switching tabs back would lose it.
    @Test
    void tabChange_toProvidersWhileDirty_asksFirst() throws TimeoutException {
        openModels();
        changeTemperature();

        clickOn("#settings-tab-providers");

        assertThat(questionShown()).isTrue();
        assertThat(selectedTabId()).isEqualTo("settings-tab-models");
    }

    @Test
    void navigation_modelDirty_asksKeepDiscardOrSave() throws TimeoutException {
        openModels();
        changeTemperature();

        onFx(() -> shell.activate(ViewNames.PROJECTS));

        assertThat(questionShown()).isTrue();
        assertThat(button("unsaved-keep")).isNotNull();
        assertThat(button("unsaved-discard")).isNotNull();
        assertThat(button("unsaved-save")).isNotNull();
        onFx(() -> button("unsaved-keep").fire());
        assertThat(modelForm().dirty().get()).isTrue();
    }

    // IF Save in the model question stored nothing or opened nothing, THEN the answer would do neither thing asked.
    @Test
    void modelSwitch_saveAnswered_storesGemmasChangeThenOpensTheOtherModel() throws TimeoutException {
        openModels();
        changeTemperature();
        onFx(() -> modelsCombo().getSelectionModel().select(1));

        onFx(() -> button("unsaved-save").fire());
        awaitFx(() -> GPT_OSS.equals(modelForm().selected().get().modelId())
                && !modelForm().facts().get().loading());

        assertThat(storedChoice(GEMMA)).isEqualTo(TemperatureChoice.OMIT.name());
        assertThat(questionShown()).isFalse();
        assertThat(ThemeTestSupport.onFx(() -> modelsCombo().getValue().modelId()))
                .isEqualTo(GPT_OSS);
    }

    // IF a refused save let the tab go, THEN the typing would be lost with the reason it was refused.
    @Test
    void tabChange_saveAnsweredWithBlockingFinding_staysAndShowsWhy() throws TimeoutException {
        openModels();
        onFx(() -> modelForm().edit(draft -> draft.withContext(true, "abc", null)));

        clickOn("#settings-tab-appearance");
        onFx(() -> button("unsaved-save").fire());

        assertThat(selectedTabId()).isEqualTo("settings-tab-models");
        assertThat(visible("models-errors")).isTrue();
        assertThat(shown("models-errors-text")).startsWith("Enter a whole number");
        assertThat(modelSettings.updateCount()).isZero();
    }

    // IF the footer sat inside the scrolled body, THEN Save would vanish on a short window.
    @Test
    void footer_smallestWindow_saveIsVisibleOutsideTheScroll() throws TimeoutException {
        resizeScene(960, 640);
        openModels();

        final Button save = (Button) required("models-form-save");
        final ScrollPane scroll = (ScrollPane) required("models-scroll");

        assertThat(ThemeTestSupport.onFx(
                        () -> save.localToScene(save.getBoundsInLocal()).getMaxY()))
                .isLessThanOrEqualTo(640.0);
        assertThat(ThemeTestSupport.onFx(() -> scroll.lookup("#models-form-save")))
                .isNull();
    }
}
