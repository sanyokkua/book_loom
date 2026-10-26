package ua.bookloom.ui.screen;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Labeled;
import javafx.scene.control.Tab;
import javafx.scene.control.ToggleButton;
import org.controlsfx.control.ToggleSwitch;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.testfx.util.WaitForAsyncUtils;
import ua.bookloom.api.pipeline.QualityDial;
import ua.bookloom.api.pipeline.ReviewMode;
import ua.bookloom.api.settings.AppSetting;
import ua.bookloom.ui.MemorySettings;
import ua.bookloom.ui.RecordingReviewModeChoice;
import ua.bookloom.ui.ThemeTestSupport;
import ua.bookloom.ui.UiTestInjector;
import ua.bookloom.ui.state.AutomationSettings;
import ua.bookloom.ui.state.KeepAwakeGate;
import ua.bookloom.ui.state.RunState;
import ua.bookloom.ui.state.StateMirror;

/** Settings › Automation as the window builds it: the stored choices, their words and the notes around them. */
class SettingsAutomationScreenTest extends SettingsScreenTestBase {

    private final MemorySettings store = new MemorySettings();
    private final RecordingReviewModeChoice choice = new RecordingReviewModeChoice();

    @Override
    void customize(final UiTestInjector.Builder builder) {
        builder.settings(store).reviewModeChoice(choice);
    }

    private void openAutomation() throws TimeoutException {
        openSettings();
        final Tab tab = tabs().getTabs().stream()
                .filter(each -> "settings-tab-automation".equals(each.getId()))
                .findFirst()
                .orElseThrow();
        onFx(() -> tabs().getSelectionModel().select(tab));
        awaitFx(() ->
                injector.getInstance(AutomationSettings.class).loadedProperty().get());
    }

    private ToggleButton toggle(final String id) {
        return (ToggleButton) required(id);
    }

    private ToggleSwitch switchOf(final String id) {
        return (ToggleSwitch) required(id);
    }

    private void awaitStored(final AppSetting<?> setting) throws TimeoutException {
        WaitForAsyncUtils.waitFor(
                WAIT_SECONDS, TimeUnit.SECONDS, () -> store.stored(setting).isPresent());
    }

    private static void collectSwitches(final Node node, final List<ToggleSwitch> found) {
        if (node instanceof ToggleSwitch each) {
            found.add(each);
        }
        if (node instanceof Parent parent) {
            parent.getChildrenUnmodifiable().forEach(child -> collectSwitches(child, found));
        }
    }

    // IF the tab did not say when a change takes effect, THEN a person would expect a running translation to change.
    @Test
    void automation_opened_saysChangesApplyAtOnce() throws TimeoutException {
        openAutomation();

        assertThat(text("automation-instant"))
                .isEqualTo("✓ Changes apply at once. When to stop and ask you reaches the next translation that"
                        + " starts.");
    }

    // IF the choices were worded as the short mode names, THEN the tab would not say what each one does.
    @Test
    void reviewMode_choices_areTheThreeLongPhrases() throws TimeoutException {
        openAutomation();

        assertThat(List.of(
                        toggle("automation-review-unattended").getText(),
                        toggle("automation-review-assisted").getText(),
                        toggle("automation-review-manual").getText()))
                .containsExactly("Never — translate unattended", "When something needs review", "After every segment");
    }

    // IF a choice were not stored the moment it is made, THEN the next run would not use it.
    @Test
    void reviewMode_assistedChosen_storedAssisted() throws TimeoutException {
        openAutomation();

        onFx(() -> toggle("automation-review-assisted").fire());

        awaitStored(AppSetting.REVIEW_MODE);
        assertThat(store.stored(AppSetting.REVIEW_MODE)).contains(ReviewMode.ASSISTED);
    }

    // IF the stored mode were not shown when the tab opens, THEN it would claim a different way of stopping.
    @Test
    void reviewMode_manualStored_manualSelectedWhenOpened() throws TimeoutException {
        store.put(AppSetting.REVIEW_MODE, ReviewMode.MANUAL);

        openAutomation();

        assertThat(toggle("automation-review-manual").isSelected()).isTrue();
        assertThat(toggle("automation-review-unattended").isSelected()).isFalse();
    }

    // IF a launch value decided silently, THEN the choice made here would look ignored for no reason.
    @Test
    void reviewMode_launchOverride_noteNamesBookloomReviewMode() throws TimeoutException {
        choice.overriddenBy("BOOKLOOM_REVIEW_MODE");

        openAutomation();

        assertThat(isShown("automation-review-override")).isTrue();
        assertThat(text("automation-review-override")).startsWith("BOOKLOOM_REVIEW_MODE is set for this launch");
    }

    @Test
    void quality_maxChosen_storedMax() throws TimeoutException {
        openAutomation();

        onFx(() -> toggle("automation-quality-max").fire());

        awaitStored(AppSetting.DEFAULT_DIAL);
        assertThat(store.stored(AppSetting.DEFAULT_DIAL)).contains(QualityDial.MAX);
    }

    @Test
    void openDesk_switchedOff_storedFalse() throws TimeoutException {
        openAutomation();

        onFx(() -> switchOf("automation-open-desk").setSelected(false));

        awaitStored(AppSetting.OPEN_REVIEW_DESK);
        assertThat(store.stored(AppSetting.OPEN_REVIEW_DESK)).contains(false);
    }

    // IF an unwritten default looked like a made choice, THEN a person could not tell it still follows the quality.
    @Test
    void lastCheck_unwritten_offWithFollowsDialHint() throws TimeoutException {
        openAutomation();

        assertThat(switchOf("automation-last-check").isSelected()).isFalse();
        assertThat(isShown("automation-last-check-hint")).isTrue();
        assertThat(text("automation-last-check-hint"))
                .isEqualTo("Not chosen yet: on for books at Max quality, off otherwise.");
    }

    @Test
    void lastCheck_switchedOn_storedTrueHintHidden() throws TimeoutException {
        openAutomation();

        onFx(() -> switchOf("automation-last-check").setSelected(true));

        awaitStored(AppSetting.LAST_CHECK);
        assertThat(store.stored(AppSetting.LAST_CHECK)).contains(true);
        assertThat(isShown("automation-last-check-hint")).isFalse();
    }

    // IF the switch only changed the stored value, THEN the computer would stay awake until the next launch.
    @Test
    void keepAwake_switchedOff_storedFalseAndGateOff() throws TimeoutException {
        openAutomation();

        onFx(() -> switchOf("automation-keep-awake").setSelected(false));

        awaitStored(AppSetting.KEEP_AWAKE);
        assertThat(store.stored(AppSetting.KEEP_AWAKE)).contains(false);
        assertThat(injector.getInstance(KeepAwakeGate.class).isOn()).isFalse();
    }

    @Test
    void reopen_switchedOff_storedFalse() throws TimeoutException {
        openAutomation();

        onFx(() -> switchOf("automation-reopen").setSelected(false));

        awaitStored(AppSetting.REOPEN_LAST_PROJECT);
        assertThat(store.stored(AppSetting.REOPEN_LAST_PROJECT)).contains(false);
    }

    // IF a notification switch were offered, THEN the tab would promise something the program does not do.
    @Test
    void automation_opened_noNotificationSwitch() throws TimeoutException {
        openAutomation();

        final List<ToggleSwitch> switches = ThemeTestSupport.onFx(() -> {
            final List<ToggleSwitch> found = new java.util.ArrayList<>();
            collectSwitches(required("settings-automation-host"), found);
            return found;
        });
        final List<String> words = ThemeTestSupport.onFx(() -> {
            final List<String> all = new java.util.ArrayList<>(textsUnder(required("settings-automation-host")));
            switches.forEach(each -> all.add(each.getText()));
            return all;
        });
        assertThat(switches).hasSize(4);
        assertThat(words).noneMatch(word -> word.matches("(?is).*(notif|сповіщ).*"));
    }

    // IF a running translation looked affected by the change, THEN a person would expect it to switch mid-run.
    @ParameterizedTest
    @CsvSource({"RUNNING,true", "PAUSING,true", "PAUSED,true", "STOPPING,true", "IDLE,false", "COMPLETED,false"})
    void automation_runUnderWay_runningNoteShown(final RunState state, final boolean expected) throws TimeoutException {
        openAutomation();

        onFx(() -> injector.getInstance(StateMirror.class).publishRunState(state));

        assertThat(isShown("automation-running")).isEqualTo(expected);
    }

    // IF a failed save were silent, THEN the choice would be lost at the next launch without a word.
    @Test
    void automation_storeFails_saveFailedShown() throws TimeoutException {
        openAutomation();
        store.failPuts();

        onFx(() -> switchOf("automation-reopen").setSelected(false));

        WaitForAsyncUtils.waitFor(WAIT_SECONDS, TimeUnit.SECONDS, () -> isShown("automation-save-error"));
        assertThat(((Labeled) required("automation-save-error")).getText())
                .isEqualTo("The choice could not be saved. It holds until BookLoom closes.");
    }
}
