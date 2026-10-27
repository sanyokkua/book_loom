package ua.bookloom.ui.screen;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.ComboBoxBase;
import javafx.scene.control.Labeled;
import javafx.scene.control.Tab;
import javafx.scene.control.TextInputControl;
import javafx.scene.control.ToggleButton;
import org.controlsfx.control.ToggleSwitch;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.testfx.util.WaitForAsyncUtils;
import ua.bookloom.api.settings.AppSetting;
import ua.bookloom.api.settings.GenerationOverrides;
import ua.bookloom.api.settings.ReasoningSetting;
import ua.bookloom.api.settings.SamplingSetting;
import ua.bookloom.ui.MemorySettings;
import ua.bookloom.ui.ScriptedGenerationOverrides;
import ua.bookloom.ui.ThemeTestSupport;
import ua.bookloom.ui.UiTestInjector;
import ua.bookloom.ui.state.GenerationSettings;
import ua.bookloom.ui.state.RunState;
import ua.bookloom.ui.state.StateMirror;
import ua.bookloom.ui.state.TechnicalDetails;

/** Settings › Generation as the window builds it: the quality table, the two stored choices and the fixed limits. */
class SettingsGenerationScreenTest extends SettingsScreenTestBase {

    private final MemorySettings store = new MemorySettings();
    private GenerationOverrides overrides = ScriptedGenerationOverrides.none();

    @Override
    void customize(final UiTestInjector.Builder builder) {
        builder.settings(store).overrides(overrides);
    }

    private void openGeneration() throws TimeoutException {
        openSettings();
        final Tab tab = tabs().getTabs().stream()
                .filter(each -> "settings-tab-generation".equals(each.getId()))
                .findFirst()
                .orElseThrow();
        onFx(() -> tabs().getSelectionModel().select(tab));
        awaitFx(() ->
                injector.getInstance(GenerationSettings.class).loadedProperty().get());
    }

    private void reopenWith(final GenerationOverrides scripted) {
        overrides = scripted;
        useLocale(java.util.Locale.ENGLISH);
    }

    private ToggleButton toggle(final String id) {
        return (ToggleButton) required(id);
    }

    private void awaitStored(final AppSetting<?> setting) throws TimeoutException {
        WaitForAsyncUtils.waitFor(
                WAIT_SECONDS, TimeUnit.SECONDS, () -> store.stored(setting).isPresent());
    }

    private static boolean holdsInput(final Node node) {
        if (node instanceof Parent parent
                && parent.getChildrenUnmodifiable().stream().anyMatch(SettingsGenerationScreenTest::holdsInput)) {
            return true;
        }
        return node instanceof TextInputControl
                || node instanceof ComboBoxBase
                || node instanceof ButtonBase
                || node instanceof ToggleSwitch;
    }

    // IF the tab did not say when a change takes effect, THEN a person would expect a running translation to change.
    @Test
    void generationTab_opened_saysChangesReachSegmentsNotTranslatedYet() throws TimeoutException {
        openGeneration();

        assertThat(text("generation-instant"))
                .isEqualTo("✓ Changes apply at once, to the segments not translated yet.");
    }

    // IF a cell of the table could be edited, THEN the table would offer a choice the program does not have.
    @Test
    void qualityTable_opened_listsFastBalancedMaxAndNoEditableCell() throws TimeoutException {
        openGeneration();

        assertThat(List.of(
                        text("generation-quality-head-fast"),
                        text("generation-quality-head-balanced"),
                        text("generation-quality-head-max")))
                .containsExactly("Fast", "Balanced", "Max quality");
        assertThat(text("generation-quality-batch-max")).isEqualTo("up to 2");
        assertThat(text("generation-quality-reviews-fast")).isEqualTo("0");
        assertThat(text("generation-quality-backward-fast")).isEqualTo("—");
        assertThat(text("generation-quality-summary-max")).isEqualTo("✓");
        assertThat(ThemeTestSupport.onFx(() -> holdsInput(required("generation-quality"))))
                .isFalse();
    }

    // IF the stored choice were not selected when the tab opens, THEN it would claim a different thinking level.
    @Test
    void thinking_lowStored_lowSelectedWhenOpened() throws TimeoutException {
        store.put(AppSetting.REASONING, ReasoningSetting.LOW);

        openGeneration();

        assertThat(toggle("generation-thinking-low").isSelected()).isTrue();
        assertThat(toggle("generation-thinking-off").isSelected()).isFalse();
    }

    // IF a choice were not stored the moment it is made, THEN the next run would not use it.
    @Test
    void thinking_lowChosen_storedLow() throws TimeoutException {
        openGeneration();

        onFx(() -> toggle("generation-thinking-low").fire());

        awaitStored(AppSetting.REASONING);
        assertThat(store.stored(AppSetting.REASONING)).contains(ReasoningSetting.LOW);
    }

    // IF clicking the selected option again left nothing selected, THEN the control would stop saying what is in force.
    @Test
    void thinking_selectedOptionClickedAgain_staysSelected() throws TimeoutException {
        openGeneration();

        onFx(() -> toggle("generation-thinking-off").fire());

        assertThat(toggle("generation-thinking-off").isSelected()).isTrue();
    }

    // IF an override were silent, THEN the choice made here would look ignored for no reason.
    @Test
    void thinking_envOverride_noteNamesBookloomReasoning() throws TimeoutException {
        reopenWith(ScriptedGenerationOverrides.reasoningFrom("BOOKLOOM_REASONING"));

        openGeneration();

        assertThat(isShown("generation-thinking-override")).isTrue();
        assertThat(text("generation-thinking-override")).startsWith("BOOKLOOM_REASONING is set for this launch");
        assertThat(isShown("generation-sampling-override")).isFalse();
    }

    @Test
    void sampling_tunedChosen_storedTuned() throws TimeoutException {
        openGeneration();

        onFx(() -> toggle("generation-sampling-tuned").fire());

        awaitStored(AppSetting.SAMPLING);
        assertThat(store.stored(AppSetting.SAMPLING)).contains(SamplingSetting.TUNED);
    }

    // IF the limits shown differed from the ones the client enforces, THEN a person would be told something untrue.
    @Test
    void timeLimits_opened_show90s120s10minAnd3Tries() throws TimeoutException {
        openGeneration();

        assertThat(text("generation-limit-check")).isEqualTo("90 s");
        assertThat(text("generation-limit-helper")).isEqualTo("120 s");
        assertThat(text("generation-limit-draft")).isEqualTo("by length, up to 10 min");
        assertThat(text("generation-limit-tries")).startsWith("up to 3 tries");
    }

    // IF a running translation looked affected by the change, THEN a person would expect it to switch mid-run.
    @ParameterizedTest
    @CsvSource({"RUNNING,true", "PAUSING,true", "PAUSED,true", "STOPPING,true", "IDLE,false", "COMPLETED,false"})
    void thinking_runUnderWay_runningNoteShown(final RunState state, final boolean expected) throws TimeoutException {
        openGeneration();

        onFx(() -> injector.getInstance(StateMirror.class).publishRunState(state));

        assertThat(isShown("generation-running")).isEqualTo(expected);
    }

    // IF a failed save were silent, THEN the choice would be lost at the next launch without a word.
    @Test
    void thinking_storeFails_saveFailedShown() throws TimeoutException {
        openGeneration();
        store.failPuts();

        onFx(() -> toggle("generation-thinking-low").fire());

        WaitForAsyncUtils.waitFor(WAIT_SECONDS, TimeUnit.SECONDS, () -> isShown("generation-save-error"));
        assertThat(text("generation-save-error"))
                .isEqualTo("The choice could not be saved. It holds until BookLoom closes.");
    }

    // IF technical names showed while the switch is off, THEN the plain screen would carry developer words.
    @Test
    void technicalOn_thinkingTechShown() throws TimeoutException {
        openGeneration();
        assertThat(isShown("generation-thinking-tech")).isFalse();

        onFx(() -> injector.getInstance(TechnicalDetails.class).property().set(true));

        assertThat(isShown("generation-thinking-tech")).isTrue();
        assertThat(((Labeled) required("generation-thinking-tech")).getText()).isEqualTo("reasoning_effort · think");
    }
}
