package ua.bookloom.ui.screen;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.testfx.util.WaitForAsyncUtils;
import ua.bookloom.ui.state.RunState;
import ua.bookloom.ui.state.StateMirror;

/** The dashboard banner says the model is slow when the mirror publishes a wait, and only while the run is running. */
class TranslatingScreenWaitingTest extends TranslatingScreenTestBase {

    private void publishWaiting(final int seconds) {
        mirror().publishWaitingSeconds(seconds);
        WaitForAsyncUtils.waitForFxEvents();
    }

    // IF the published seconds were not shown as minutes and seconds, THEN 72 would read as a bare number.
    @ParameterizedTest(name = "{0} s")
    @CsvSource({"10, 0:10", "12, 0:12", "72, 1:12", "600, 10:00"})
    void banner_waitingSecondsPublishedWhileRunning_replaceTheStateTextWithTheWaitingNotice(
            final int seconds, final String shown) {
        showTranslating();
        publish(RunState.RUNNING);

        publishWaiting(seconds);

        assertThat(labelText("translating-banner-text")).isEqualTo("Waiting for the model… " + shown);
        assertThat(labelText("translating-banner-title")).isEqualTo("Translating");
    }

    // IF a cleared wait left its text, THEN the banner would keep claiming the model is slow after it answered.
    @Test
    void banner_waitingCleared_returnsToTheRunningText() {
        showTranslating();
        publish(RunState.RUNNING);
        publishWaiting(12);

        publishWaiting(StateMirror.NOT_WAITING);

        assertThat(labelText("translating-banner-text"))
                .isEqualTo("The run is in progress. You can pause or stop it at any time.");
    }

    // IF a wait still published when the run left RUNNING were shown, THEN the pausing banner would read as waiting.
    @ParameterizedTest(name = "{0}")
    @CsvSource({"PAUSING, Pausing", "PAUSED, Paused", "STOPPING, Stopping", "COMPLETED, Translation finished"})
    void banner_waitingPublishedThenStateLeavesRunning_showsTheStateAndNotTheWait(
            final RunState state, final String title) {
        showTranslating();
        publish(RunState.RUNNING);
        publishWaiting(12);

        publish(state);

        assertThat(labelText("translating-banner-title")).isEqualTo(title);
        assertThat(labelText("translating-banner-text")).doesNotContain("Waiting for the model");
    }
}
