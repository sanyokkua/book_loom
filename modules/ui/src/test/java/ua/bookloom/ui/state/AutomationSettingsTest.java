package ua.bookloom.ui.state;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.pipeline.QualityDial;
import ua.bookloom.api.pipeline.ReviewMode;
import ua.bookloom.api.settings.AppSetting;
import ua.bookloom.ui.MemorySettings;
import ua.bookloom.ui.RecordingReviewModeChoice;
import ua.bookloom.ui.SettingWriter;

/** The Automation choices are read when the tab opens, stored the moment they are made, and a launch value is named. */
class AutomationSettingsTest {

    private final MemorySettings store = new MemorySettings();
    private final RecordingReviewModeChoice choice = new RecordingReviewModeChoice();
    private final KeepAwakeGate gate = new KeepAwakeGate();

    private AutomationSettings direct(final MemorySettings settings) {
        return new AutomationSettings(new SettingWriter(settings, Runnable::run, Runnable::run), choice, gate);
    }

    @Test
    void load_storedValues_published() {
        store.put(AppSetting.REVIEW_MODE, ReviewMode.MANUAL);
        store.put(AppSetting.DEFAULT_DIAL, QualityDial.MAX);
        store.put(AppSetting.OPEN_REVIEW_DESK, false);
        store.put(AppSetting.KEEP_AWAKE, false);
        store.put(AppSetting.REOPEN_LAST_PROJECT, false);
        final AutomationSettings automation = direct(store);

        automation.load();

        assertThat(automation.reviewModeProperty().get()).isEqualTo(ReviewMode.MANUAL);
        assertThat(automation.defaultDialProperty().get()).isEqualTo(QualityDial.MAX);
        assertThat(automation.openReviewDeskProperty().get()).isFalse();
        assertThat(automation.keepAwakeProperty().get()).isFalse();
        assertThat(automation.reopenLastProjectProperty().get()).isFalse();
        assertThat(automation.loadedProperty().get()).isTrue();
    }

    @Test
    void load_nothingStored_defaultsShown() {
        final AutomationSettings automation = direct(store);

        automation.load();

        assertThat(automation.reviewModeProperty().get()).isEqualTo(ReviewMode.UNATTENDED);
        assertThat(automation.defaultDialProperty().get()).isEqualTo(QualityDial.BALANCED);
        assertThat(automation.openReviewDeskProperty().get()).isTrue();
        assertThat(automation.keepAwakeProperty().get()).isTrue();
        assertThat(automation.reopenLastProjectProperty().get()).isTrue();
    }

    @Test
    void load_launchOverride_namesIt() {
        choice.overriddenBy("BOOKLOOM_REVIEW_MODE");
        final AutomationSettings automation = direct(store);

        automation.load();

        assertThat(automation.reviewModeOverrideProperty().get()).isEqualTo("BOOKLOOM_REVIEW_MODE");
    }

    @Test
    void load_lastCheckUnwritten_offAndNotChosen() {
        final AutomationSettings automation = direct(store);

        automation.load();

        assertThat(automation.lastCheckProperty().get()).isFalse();
        assertThat(automation.lastCheckChosenProperty().get()).isFalse();
    }

    @Test
    void load_lastCheckWrittenOn_onAndChosen() {
        store.put(AppSetting.LAST_CHECK, true);
        final AutomationSettings automation = direct(store);

        automation.load();

        assertThat(automation.lastCheckProperty().get()).isTrue();
        assertThat(automation.lastCheckChosenProperty().get()).isTrue();
    }

    @Test
    void chooseLastCheck_on_writesExplicitlyAndChosen() {
        final AutomationSettings automation = direct(store);
        automation.load();

        automation.chooseLastCheck(true);

        assertThat(store.stored(AppSetting.LAST_CHECK)).contains(true);
        assertThat(automation.lastCheckChosenProperty().get()).isTrue();
    }

    @Test
    void choose_assisted_storesAssistedAndTellsChoice() {
        final AutomationSettings automation = direct(store);

        automation.chooseReviewMode(ReviewMode.ASSISTED);

        assertThat(store.stored(AppSetting.REVIEW_MODE)).contains(ReviewMode.ASSISTED);
        assertThat(choice.told()).containsExactly(ReviewMode.ASSISTED);
    }

    @Test
    void choose_reviewModeStoreFails_saveFailedAndChoiceNotTold() {
        final AutomationSettings automation = direct(new MemorySettings().failPuts());

        automation.chooseReviewMode(ReviewMode.MANUAL);

        assertThat(automation.saveFailedProperty().get()).isTrue();
        assertThat(automation.reviewModeProperty().get()).isEqualTo(ReviewMode.MANUAL);
        assertThat(choice.told()).isEmpty();
    }

    @Test
    void choose_keepAwakeOff_gateOffBeforeWrite() {
        final List<Runnable> held = new ArrayList<>();
        final AutomationSettings automation =
                new AutomationSettings(new SettingWriter(store, held::add, Runnable::run), choice, gate);

        automation.chooseKeepAwake(false);

        assertThat(gate.isOn()).isFalse();
        assertThat(store.all().data()).doesNotContainKey(AppSetting.KEEP_AWAKE.key());
        held.forEach(Runnable::run);
        assertThat(store.stored(AppSetting.KEEP_AWAKE)).contains(false);
    }

    @Test
    void choose_beforeLoad_personWins() {
        store.put(AppSetting.REVIEW_MODE, ReviewMode.MANUAL);
        final List<Runnable> held = new ArrayList<>();
        final AutomationSettings automation =
                new AutomationSettings(new SettingWriter(store, held::add, Runnable::run), choice, gate);
        automation.load();

        automation.chooseReviewMode(ReviewMode.ASSISTED);
        held.forEach(Runnable::run);

        assertThat(automation.reviewModeProperty().get()).isEqualTo(ReviewMode.ASSISTED);
    }

    @Test
    void chooseDial_max_storesMax() {
        direct(store).chooseDial(QualityDial.MAX);

        assertThat(store.stored(AppSetting.DEFAULT_DIAL)).contains(QualityDial.MAX);
    }

    @Test
    void chooseOpenDesk_off_storesFalse() {
        direct(store).chooseOpenReviewDesk(false);

        assertThat(store.stored(AppSetting.OPEN_REVIEW_DESK)).contains(false);
    }

    @Test
    void chooseReopen_off_storesFalse() {
        direct(store).chooseReopenLastProject(false);

        assertThat(store.stored(AppSetting.REOPEN_LAST_PROJECT)).contains(false);
    }

    @Test
    void choose_twiceWritesRunNewestFirst_lastChoiceStays() {
        final List<Runnable> held = new ArrayList<>();
        final AutomationSettings automation =
                new AutomationSettings(new SettingWriter(store, held::add, Runnable::run), choice, gate);

        automation.chooseDial(QualityDial.FAST);
        automation.chooseDial(QualityDial.MAX);
        runNewestFirst(held);

        assertThat(store.stored(AppSetting.DEFAULT_DIAL)).contains(QualityDial.MAX);
    }

    // Runs the held writes newest first, including any a write queues while it runs.
    private static void runNewestFirst(final List<Runnable> held) {
        while (!held.isEmpty()) {
            held.remove(held.size() - 1).run();
        }
    }
}
