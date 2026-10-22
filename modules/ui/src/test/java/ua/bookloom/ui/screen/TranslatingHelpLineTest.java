package ua.bookloom.ui.screen;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.testfx.util.WaitForAsyncUtils;
import ua.bookloom.api.persistence.SettingsRepository;
import ua.bookloom.api.settings.AppSetting;
import ua.bookloom.ui.ThemeTestSupport;
import ua.bookloom.ui.TooltipProbe;
import ua.bookloom.ui.state.CurrentProject;
import ua.bookloom.ui.state.RunState;

/** The help line about sleep and keep-awake: shown while a translation runs, closable once for each project. */
class TranslatingHelpLineTest extends TranslatingScreenTestBase {

    private static final String LINE = "translating-help-sleep";
    private static final String CLOSE = "translating-help-sleep-close";
    private static final String AWAKE_TEXT = "BookLoom keeps the computer awake while it translates. Closing a laptop"
            + " lid still puts it to sleep — after sleep, translating goes on by itself. Nothing finished is lost.";
    private static final String NOT_AWAKE_TEXT =
            "The computer may sleep while translating — turn on Keep awake in Settings.";

    private void runningWithABookOpen() throws Exception {
        readyToStart();
        showTranslating();
        mirror().publishRunStarted("Frankenstein.epub", null);
        publish(RunState.RUNNING);
    }

    private SettingsRepository settings() {
        return injector.getInstance(SettingsRepository.class);
    }

    // IF the line were missing while a run goes, THEN a person closing the lid would not learn the computer sleeps.
    @Test
    void helpLine_running_saysTheComputerIsKeptAwakeButALidStillSleeps() throws Exception {
        runningWithABookOpen();

        awaitFx(() -> isShown(LINE));

        assertThat(textOf(LINE)).isEqualTo(AWAKE_TEXT);
    }

    // IF the line showed with no run, THEN a book not yet started would warn about a sleep it does not risk.
    @ParameterizedTest
    @EnumSource(
            value = RunState.class,
            names = {"IDLE", "PAUSED", "STOPPED", "COMPLETED", "FAILED"})
    void helpLine_runNotRunning_isNotShown(final RunState state) throws Exception {
        runningWithABookOpen();

        publish(state);

        assertThat(isShown(LINE)).isFalse();
    }

    // IF the line promised an awake computer while keep-awake is off, THEN the person would trust a promise not kept.
    @Test
    void helpLine_keepAwakeOff_saysTheComputerMaySleepAndWhereToTurnItOn() throws Exception {
        settings().put(AppSetting.KEEP_AWAKE, false);
        runningWithABookOpen();

        awaitFx(() -> isShown(LINE) && textOf(LINE).equals(NOT_AWAKE_TEXT));

        assertThat(textOf(LINE)).isEqualTo(NOT_AWAKE_TEXT);
    }

    // IF closing hid the line only until the next visit, THEN it would nag at every run of the same book.
    @Test
    void close_pressed_hidesTheLineAndStoresItForTheProject() throws Exception {
        runningWithABookOpen();
        awaitFx(() -> isShown(CLOSE));
        assertThat(TooltipProbe.tipText(required(CLOSE))).isEqualTo("Do not show again for this project");

        onFx(() -> button(CLOSE).fire());
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(isShown(LINE)).isFalse();
        final String id = projectId();
        awaitFx(() -> java.util.Objects.requireNonNull(
                        settings().get(AppSetting.sleepLineClosed(id)).data())
                .orElse(false));
    }

    // IF a closed line came back on the next visit, THEN the choice would last only until the screen is rebuilt.
    @Test
    void helpLine_closedThenTheScreenIsVisitedAgain_staysClosed() throws Exception {
        runningWithABookOpen();
        awaitFx(() -> isShown(CLOSE));
        onFx(() -> button(CLOSE).fire());
        WaitForAsyncUtils.waitForFxEvents();

        showTranslating();

        assertThat(isShown(LINE)).isFalse();
    }

    private String projectId() {
        return ThemeTestSupport.onFx(
                () -> injector.getInstance(CurrentProject.class).book().get().projectId());
    }
}
