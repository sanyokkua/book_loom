package ua.bookloom.ui.screen;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.testfx.util.WaitForAsyncUtils;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.ui.ProgressFixtures;
import ua.bookloom.ui.state.RunState;
import ua.bookloom.ui.state.Throughput;
import ua.bookloom.ui.state.TranslatingViewModel;

/** The run in the title bar: what it says for each state of the run, and that its control acts like the screen's. */
class RunStatusBarTest extends TranslatingScreenTestBase {

    private static final String BAR = "shell-run-status";
    private static final String CONTROL = "shell-run-control";

    private void runAt78(final RunState state) {
        mirror().publishRunStarted("Frankenstein.epub");
        mirror().publishProgress(ProgressFixtures.progress(7, 11, 78, 0, 22));
        mirror().live().publishThroughput(new Throughput(null, false, Duration.ofMinutes(80), Duration.ofMinutes(62)));
        mirror().publishRunState(state);
        WaitForAsyncUtils.waitForFxEvents();
    }

    private String barText() {
        return textOf(BAR);
    }

    // IF the bar were drawn with no run, THEN a first launch would show a dead control.
    @Test
    void bar_noRun_isUnmanagedAndTheTitleBarHoldsOnlyTheNameThemeAndAbout() {
        assertThat(required(BAR).isManaged()).isFalse();
        assertThat(required(BAR).isVisible()).isFalse();
        assertThat(textsUnder(required("shell-title-bar")))
                .contains("BookLoom", "Dark", "About")
                .doesNotContain("Pause", "Resume");
    }

    // IF a discarded run left its status behind, THEN the title bar would name a book that is gone.
    @Test
    void bar_afterTheRunIsCleared_isUnmanagedAgain() {
        runAt78(RunState.RUNNING);
        assertThat(required(BAR).isManaged()).isTrue();

        mirror().publishRunCleared();
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(required(BAR).isManaged()).isFalse();
    }

    // IF any part were missing or misordered, THEN the person could not read the run from another screen.
    @Test
    void bar_running_showsNameStateElapsedTimeLeftAndPauseAheadOfTheThemeToggle() {
        runAt78(RunState.RUNNING);

        assertThat(textsUnder(required(BAR)))
                .containsSubsequence("Frankenstein.epub", "Progress 78%", "1h 02m elapsed", "~1h 20m left", "Pause");
        assertThat(barText()).doesNotContain("Resume");
        assertThat(((javafx.scene.layout.HBox) required("shell-title-bar")).getChildren())
                .containsSubsequence(required(BAR), required("shell-theme-toggle"));
    }

    // IF the title bar rounded the share while the card floored it, THEN at 81 of 163 the two would read 50% and 49%.
    @Test
    void bar_andTheProgressCard_atOneShare_showTheSamePercent() {
        showTranslating();
        mirror().publishRunStarted("Frankenstein.epub");
        mirror().publishProgress(ProgressFixtures.progress(8, 12, 81, 0, 82));
        mirror().publishRunState(RunState.RUNNING);
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(textsUnder(required(BAR))).contains("Progress 49%");
        assertThat(labelText("translating-progress-text")).startsWith("49%");
    }

    // IF the time left were shown when the run reports none, THEN the bar would claim an estimate it does not have.
    @Test
    void bar_noTimeLeft_showsNoTimeLeft() {
        mirror().publishRunStarted("Frankenstein.epub");
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(barText()).contains("Progress 0%").doesNotContain("left");
        assertThat(required("shell-run-left").isManaged()).isFalse();
    }

    // IF a state read as another, THEN the person would trust the wrong thing about their run.
    @ParameterizedTest
    @CsvSource({
        "PAUSED,Paused at 78%,Resume",
        "STOPPED,Stopped at 78%,Resume",
        "COMPLETED,Finished,-",
        "FAILED,Failed at 78%,-"
    })
    void bar_eachState_showsItsTextAndTheRightControl(final RunState state, final String text, final String control) {
        runAt78(state);

        assertThat(labelText("shell-run-state")).isEqualTo(text);
        assertThat(button(CONTROL).isManaged()).isEqualTo(!"-".equals(control));
        assertThat("-".equals(control) ? "-" : button(CONTROL).getText()).isEqualTo(control);
    }

    // IF a pause on a refusal read differently from one on an outage, THEN the person could not tell to resume.
    @ParameterizedTest
    @CsvSource({"unreachable", "validation"})
    void bar_pausedOnAProviderError_readsProviderErrorWithResume(final ErrorCode code) {
        runAt78(RunState.PAUSED);
        mirror().review().publishProviderError(AppError.of(code, "Provider", "It failed."));
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(labelText("shell-run-state")).isEqualTo("Provider error");
        assertThat(button(CONTROL).getText()).isEqualTo("Resume");
        assertThat(button(CONTROL).isDisabled()).isFalse();
    }

    // IF the control could be pressed while its request is pending, THEN a second request would pile onto the first.
    @Test
    void bar_pausing_showsPauseUnavailable() {
        runAt78(RunState.PAUSING);

        assertThat(button(CONTROL).getText()).isEqualTo("Pause");
        assertThat(button(CONTROL).isDisabled()).isTrue();
    }

    // IF a stop in flight offered a live control, THEN the person could resume a run that is ending.
    @Test
    void bar_stopping_showsResumeUnavailable() {
        runAt78(RunState.STOPPING);

        assertThat(button(CONTROL).getText()).isEqualTo("Resume");
        assertThat(button(CONTROL).isDisabled()).isTrue();
    }

    // IF Ukrainian text were not translated, THEN the bar would speak English inside a Ukrainian window.
    @Test
    void bar_underUkrainian_readsInUkrainian() {
        useLocale(java.util.Locale.forLanguageTag("uk"));
        runAt78(RunState.PAUSED);

        assertThat(labelText("shell-run-state")).isEqualTo("Пауза на 78%");
        assertThat(labelText("shell-run-elapsed")).isEqualTo("минуло 1 год 02 хв");
        assertThat(button(CONTROL).getText()).isEqualTo("Продовжити");
    }

    private void startRealRun() throws Exception {
        readyToStart();
        onFx(() -> injector.getInstance(TranslatingViewModel.class).start());
        job.awaitRunStarted();
        awaitFx(() -> mirror().runState().get() == RunState.RUNNING);
    }

    // IF the bar's control did not act like the screen's, THEN the two could disagree about the run.
    @Test
    void control_pressedWhileRunning_asksTheJobToPauseOnce() throws Exception {
        startRealRun();

        onFx(() -> button(CONTROL).fire());

        assertThat(job.calls().stream().filter("pause"::equals)).hasSize(1);
    }

    @Test
    void control_pressedWhilePaused_asksTheJobToResumeOnce() throws Exception {
        startRealRun();
        onFx(() -> button(CONTROL).fire());
        publish(RunState.PAUSED);

        onFx(() -> button(CONTROL).fire());

        assertThat(job.calls().stream().filter("resume"::equals)).hasSize(1);
    }

    // IF Resume on a stopped run only flipped a label, THEN no work would continue.
    @Test
    void control_pressedWhileStopped_asksTheEngineForASecondJob() throws Exception {
        startRealRun();
        onFx(() -> injector.getInstance(TranslatingViewModel.class).stop());
        job.finish(Result.ok(cancelledReport()));
        awaitFx(() -> mirror().runState().get() == RunState.STOPPED);
        assertThat(engine.requests()).hasSize(1);

        onFx(() -> button(CONTROL).fire());

        awaitFx(() -> engine.requests().size() == 2);
    }
}
