package ua.bookloom.ui.screen;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Locale;
import javafx.scene.control.ToggleButton;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.testfx.util.WaitForAsyncUtils;
import ua.bookloom.api.pipeline.CallKind;
import ua.bookloom.ui.state.RunState;
import ua.bookloom.ui.state.WaitingCall;

/**
 * A call the model is slow to answer (app-shell "Call a model call stuck after a minute and show one clock"): under a
 * minute the run reads as translating, from a minute it is the stuck-call row with its amber chip and its banner, and a
 * new attempt, or the run leaving RUNNING, ends it.
 */
class TranslatingScreenWaitingTest extends TranslatingScreenTestBase {

    private void runningOnTheShell() {
        chooseModel();
        showTranslating();
        mirror().publishRunStarted("Frankenstein.epub", null);
        publish(RunState.RUNNING);
    }

    /** A draft attempt that has waited {@code seconds} against a limit of six minutes, or no wait at all. */
    private void waitOn(final @Nullable Long seconds) {
        mirror().live()
                .publishWaitingCall(
                        seconds == null
                                ? null
                                : new WaitingCall(
                                        CallKind.DRAFT,
                                        "ch2 · p05",
                                        3,
                                        1,
                                        2,
                                        Duration.ofSeconds(seconds),
                                        Duration.ofMinutes(6),
                                        Duration.ofSeconds(seconds),
                                        seconds >= 60));
        WaitForAsyncUtils.waitForFxEvents();
    }

    // IF a slow model were called stuck a second early, THEN it would look broken; at a minute it is stuck.
    @ParameterizedTest(name = "{0} s")
    @CsvSource({"10, Model answering, false", "59, Model answering, false", "60, No answer for 1:00, true"})
    void chip_attemptWaited_isStuckFromAMinute(final long seconds, final String chip, final boolean banner) {
        runningOnTheShell();

        waitOn(seconds);

        assertThat(button("shell-run-connection").getText()).isEqualTo(chip);
        assertThat(optional("translating-banner") != null).isEqualTo(banner);
    }

    @Test
    void banner_stuckDraftAtThreeFortyOfSix_showsTheOneClockAndTheRetryTime() {
        runningOnTheShell();

        waitOn(220L);

        assertThat(labelText("translating-banner-title"))
                .isEqualTo("The model is taking longer than usual (3:40). BookLoom tries again by itself at 6:00."
                        + " You do not need to do anything.");
        assertThat(labelText("translating-live-line"))
                .isEqualTo("The model is taking longer than usual with segments ch2 · p05.");
    }

    // IF the pinned banner were squeezed to one row when the content below is taller than the window, THEN the long
    // Ukrainian sentence would be cut at the right edge and "You do not need to do anything" would be lost.
    @Test
    void banner_stuckInUkrainianAtTheMinimumSize_showsEveryRowOfTheSentence() {
        useLocale(Locale.forLanguageTag("uk"));
        resizeScene(960, 640);
        runningOnTheShell();

        waitOn(220L);
        onFx(() -> scene.getRoot().requestLayout());
        WaitForAsyncUtils.waitForFxEvents();

        final javafx.scene.control.Label title = (javafx.scene.control.Label) required("translating-banner-title");
        assertThat(title.getLayoutBounds().getHeight())
                .isGreaterThanOrEqualTo(title.prefHeight(title.getLayoutBounds().getWidth()));
    }

    // IF a new attempt kept the stuck state, THEN the clock would count past the try that restarted it.
    @Test
    void banner_newAttemptStarts_returnsToTranslating() {
        runningOnTheShell();
        waitOn(220L);

        waitOn(null);

        assertThat(optional("translating-banner")).isNull();
        assertThat(button("shell-run-connection").getText()).isEqualTo("Model answering");
    }

    // IF a wait still published when the run left RUNNING were shown, THEN a pending pause would read as stuck.
    @ParameterizedTest(name = "{0}")
    @CsvSource({"PAUSING, Pausing...", "STOPPING, Stopping..."})
    void banner_stuckThenStateLeavesRunning_showsTheStateAndNotTheStuckBanner(
            final RunState state, final String title) {
        runningOnTheShell();
        waitOn(220L);

        publish(state);

        assertThat(labelText("shell-run-state")).isEqualTo(title);
        assertThat(optional("translating-banner")).isNull();
    }

    private void waitOnAttempt(final long attemptSeconds, final long totalSeconds) {
        mirror().live()
                .publishWaitingCall(new WaitingCall(
                        CallKind.DRAFT,
                        "ch2 · p05",
                        3,
                        2,
                        2,
                        Duration.ofSeconds(attemptSeconds),
                        Duration.ofMinutes(6),
                        Duration.ofSeconds(totalSeconds),
                        attemptSeconds >= 60));
        WaitForAsyncUtils.waitForFxEvents();
    }

    // IF the 10 s wait showed no words, THEN a person looking at a still screen would not know the model is working.
    @Test
    void liveLine_twelveSecondsWaited_saysWaitingForTheModelWithTheClock() {
        runningOnTheShell();

        waitOn(12L);

        assertThat(labelText("translating-live-line")).isEqualTo("Waiting for the model... 0:12");
    }

    // IF a retry restarted the clock, THEN a call that has been going for three minutes would read as five seconds.
    @Test
    void liveLine_retryAfterThreeMinutes_countsTheWholeWaitNotTheAttempt() {
        runningOnTheShell();

        waitOnAttempt(5, 185);

        assertThat(labelText("translating-live-line")).isEqualTo("Waiting for the model... 3:05");
    }

    // IF a wait of nine seconds already showed the words, THEN every ordinary call would flash them.
    @Test
    void liveLine_nineSecondsWaited_isTheOrdinaryLine() {
        runningOnTheShell();

        waitOn(9L);

        assertThat(labelText("translating-live-line")).doesNotContain("Waiting for the model");
    }

    // IF the words stayed English in a Ukrainian window, THEN half of the live line would be unreadable.
    @Test
    void liveLine_ukrainianWindow_saysWaitingInUkrainian() {
        useLocale(Locale.forLanguageTag("uk"));
        runningOnTheShell();

        waitOn(12L);

        assertThat(labelText("translating-live-line")).isEqualTo("Очікування відповіді моделі... 0:12");
    }

    // IF a stuck call showed its wait twice, THEN a slow model would read as a model with two clocks.
    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"simple", "detailed"})
    void stuckCall_atThreeFortyOfSix_showsOneClockInEitherView(final String view) {
        runningOnTheShell();
        onFx(() -> ((ToggleButton) required("translating-live-mode-" + view)).fire());

        waitOn(220L);

        assertThat(labelText("translating-live-clock")).isEqualTo("3:40 of 6:00");
        assertThat(textOf("translating-live-card").split("3:40 of 6:00", -1)).hasSize(2);
    }
}
