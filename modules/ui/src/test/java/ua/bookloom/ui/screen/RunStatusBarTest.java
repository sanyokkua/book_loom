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
import ua.bookloom.api.pipeline.QualityDial;
import ua.bookloom.api.pipeline.ReviewMode;
import ua.bookloom.ui.ProgressFixtures;
import ua.bookloom.ui.ViewNames;
import ua.bookloom.ui.control.Tips;
import ua.bookloom.ui.state.RunMode;
import ua.bookloom.ui.state.RunState;
import ua.bookloom.ui.state.Throughput;
import ua.bookloom.ui.state.TranslatingViewModel;

/**
 * The run in the title bar (app-shell "Show the run in the title bar while one exists"): the project's name, the state
 * text of its run-state row, the mode, the times, the AI-model chip and the one run control, which acts as the
 * toolbar's control of the same name.
 */
class RunStatusBarTest extends TranslatingScreenTestBase {

    private static final String BAR = "shell-run-status";
    private static final String CONTROL = "shell-run-control";
    private static final String CHIP = "shell-run-connection";

    // Wide enough for every part: a window below the wide class hides the mode, the time and the speed.
    private void runAt78(final RunState state) {
        resizeScene(1440, 700);
        mirror().publishRunStarted("Frankenstein.epub", null);
        mirror().publishProgress(ProgressFixtures.progress(7, 11, 78, 0, 22));
        mirror().live().publishThroughput(new Throughput(null, false, Duration.ofMinutes(80), Duration.ofMinutes(62)));
        mirror().publishRunState(state);
        WaitForAsyncUtils.waitForFxEvents();
    }

    private String barText() {
        return textOf(BAR);
    }

    // IF the bar were drawn with no project, THEN a first launch would show a dead control.
    @Test
    void bar_noProject_isUnmanagedAndTheTitleBarHoldsOnlyTheNameThemeAndAbout() {
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
    void bar_running_showsNameStateElapsedChipAndPauseAheadOfTheThemeToggle() {
        runAt78(RunState.RUNNING);

        assertThat(textsUnder(required(BAR)))
                .containsSubsequence(
                        "Frankenstein.epub",
                        "78 % done · about 1 h 20 min left",
                        "1h 02m elapsed",
                        "Model answering",
                        "Pause");
        assertThat(barText()).doesNotContain("Resume");
        assertThat(((javafx.scene.layout.HBox) required("shell-title-bar")).getChildren())
                .containsSubsequence(required(BAR), required("shell-theme-toggle"));
    }

    // IF the chip's colour were not the row's meaning, THEN a failure would look like a model at work.
    @ParameterizedTest
    @CsvSource({"RUNNING, state-working", "PAUSED, state-waiting", "FAILED, state-failed"})
    void chip_eachState_carriesTheMeaningOfItsRow(final RunState state, final String meaning) {
        runAt78(state);

        assertThat(required(CHIP).getStyleClass()).contains("state-marker", meaning);
    }

    // IF the chip led nowhere, THEN a person who sees "Cannot reach Ollama" would have to find the settings alone.
    @Test
    void chip_pressed_opensTheSettings() {
        runAt78(RunState.RUNNING);

        onFx(() -> button(CHIP).fire());

        assertThat(navigator.currentView().get()).isEqualTo(ViewNames.SETTINGS);
    }

    // IF the title bar and the strip each worked out the share, THEN at 81 of 163 they could read 50% and 49%.
    @Test
    void bar_andTheStrip_atOneShare_showTheSamePercent() {
        showTranslating();
        mirror().publishRunStarted("Frankenstein.epub", null);
        mirror().publishProgress(ProgressFixtures.progress(8, 12, 81, 0, 82));
        mirror().publishRunState(RunState.RUNNING);
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(labelText("shell-run-state")).startsWith("50 % done");
        assertThat(progressBar().getProgress()).isCloseTo(0.4969, org.assertj.core.api.Assertions.within(0.0001));
        assertThat(labelText("translating-strip-line")).startsWith("81 of 163 segments done");
    }

    // IF the time left were shown when the run reports none, THEN the bar would claim an estimate it does not have.
    @Test
    void bar_noTimeLeft_showsTheShareAlone() {
        mirror().publishRunStarted("Frankenstein.epub", null);
        mirror().publishRunState(RunState.RUNNING);
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(labelText("shell-run-state")).isEqualTo("0 % done");
    }

    // IF the dial and the review mode were not beside the state, THEN a person on another screen could not tell how
    // hard the run works or when it will stop to ask for them.
    @Test
    void bar_runStartedWithADialAndAMode_showsBalancedUnattendedNextToTheState() {
        mirror().publishRunStarted("Frankenstein.epub", new RunMode(QualityDial.BALANCED, ReviewMode.UNATTENDED));
        mirror().publishRunState(RunState.RUNNING);
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(labelText("shell-run-mode")).isEqualTo("Balanced · Unattended");
        assertThat(textsUnder(required(BAR))).containsSubsequence("0 % done", "Balanced · Unattended");
    }

    // IF a start that names no mode left the label showing, THEN the bar would show a mode no run has.
    @Test
    void bar_runStartedWithoutAMode_hidesTheModeLabel() {
        mirror().publishRunStarted("Frankenstein.epub", null);
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(required("shell-run-mode").isManaged()).isFalse();
    }

    // IF a state read as another, THEN the person would trust the wrong thing about their run.
    @ParameterizedTest
    @CsvSource({
        "PAUSED,Paused at 78 %,Resume",
        "STOPPED,Stopped at 78 %,Continue translation",
        "COMPLETED,Finished,-",
        "FAILED,Did not finish,-"
    })
    void bar_eachState_showsItsTextAndTheRightControl(final RunState state, final String text, final String control) {
        runAt78(state);

        assertThat(labelText("shell-run-state")).isEqualTo(text);
        assertThat(button(CONTROL).isManaged()).isEqualTo(!"-".equals(control));
        assertThat("-".equals(control) ? "-" : button(CONTROL).getText()).isEqualTo(control);
    }

    // IF a pause on a refusal read as one on an outage, THEN the person would look for a program that answers.
    @ParameterizedTest
    @CsvSource({"unreachable, Cannot reach Ollama", "validation, Error"})
    void bar_pausedOnAProviderError_namesItInTheChipWithResume(final ErrorCode code, final String chip) {
        runAt78(RunState.PAUSED);
        mirror().review().publishProviderError(AppError.of(code, "Provider", "It failed."));
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(labelText("shell-run-state")).isEqualTo("Paused at 78 %");
        assertThat(button(CHIP).getText()).isEqualTo(chip);
        assertThat(button(CONTROL).getText()).isEqualTo("Resume");
        assertThat(button(CONTROL).isDisabled()).isFalse();
    }

    // IF the control could be pressed while its request is pending, THEN a second request would pile onto the first.
    @Test
    void bar_pausing_showsPauseUnavailable() {
        runAt78(RunState.PAUSING);

        assertThat(labelText("shell-run-state")).isEqualTo("Pausing…");
        assertThat(button(CONTROL).getText()).isEqualTo("Pause");
        assertThat(button(CONTROL).isDisabled()).isTrue();
    }

    // IF a stop in flight offered a live control, THEN the person could continue a run that is ending.
    @Test
    void bar_stopping_showsContinueUnavailable() {
        runAt78(RunState.STOPPING);

        assertThat(button(CONTROL).getText()).isEqualTo("Continue translation");
        assertThat(button(CONTROL).isDisabled()).isTrue();
    }

    @Test
    void control_running_explainsItselfAsTheToolbarsPause() {
        runAt78(RunState.RUNNING);

        assertThat(Tips.explanationOf(button(CONTROL)))
                .contains("Pauses the translation. The request in progress is cancelled and repeated on resume;"
                        + " nothing decided so far is lost.");
    }

    // IF Ukrainian text were not translated, THEN the bar would speak English inside a Ukrainian window.
    @Test
    void bar_underUkrainian_readsInUkrainian() {
        useLocale(java.util.Locale.forLanguageTag("uk"));
        runAt78(RunState.RUNNING);
        assertThat(labelText("shell-run-elapsed")).isEqualTo("минуло 1 год 02 хв");

        publish(RunState.PAUSED);

        assertThat(labelText("shell-run-state")).isEqualTo("Пауза на 78 %");
        assertThat(button(CONTROL).getText()).isEqualTo("Відновити");
    }

    private void startRealRun() throws Exception {
        readyToStart();
        onFx(() -> injector.getInstance(TranslatingViewModel.class).start());
        job.awaitRunStarted();
        awaitFx(() -> mirror().runState().get() == RunState.RUNNING);
    }

    // IF the bar's control did not act like the toolbar's, THEN the two could disagree about the run.
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

    // IF Continue translation on a stopped run only flipped a label, THEN no work would continue.
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

    // IF the title bar's Pause and the toolbar's disagreed afterwards, THEN the person could not tell which to trust.
    @Test
    void control_pausedFromSettings_showsResumeHereAndInTheToolbar() throws Exception {
        startRealRun();
        onFx(() -> shell.activate(ViewNames.SETTINGS));

        onFx(() -> button(CONTROL).fire());
        publish(RunState.PAUSED);
        showTranslating();

        assertThat(button(CONTROL).getText()).isEqualTo("Resume");
        assertThat(isShown("translating-resume")).isTrue();
        assertThat(isShown("translating-pause")).isFalse();
    }

    // IF the average generation speed were left out of the title bar, THEN a person could not see how fast the run is.
    @Test
    void bar_runAverageKnown_showsTokensPerSecondAndHidesItBefore() {
        runAt78(RunState.RUNNING);
        assertThat(barText()).doesNotContain("tok/s");

        mirror().live()
                .publishThroughput(new Throughput(21.4, false, Duration.ofMinutes(80), Duration.ofMinutes(62), 21.5));
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(barText()).contains("22 tok/s");
    }
}
