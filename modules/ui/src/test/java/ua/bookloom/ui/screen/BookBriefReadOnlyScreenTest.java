package ua.bookloom.ui.screen;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.testfx.util.WaitForAsyncUtils;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.pipeline.CallKind;
import ua.bookloom.api.pipeline.RecoveryWaiting;
import ua.bookloom.ui.state.RecoveryState;
import ua.bookloom.ui.state.RunState;
import ua.bookloom.ui.state.WaitingCall;

/**
 * While a translation is at work the brief is read-only (book-brief "Make the brief read-only while a translation is
 * going on"): a banner says why and offers Pause, every control of the two columns is disabled, and the person gets the
 * brief back once the run is paused. The footer's Back and Continue stay usable.
 */
class BookBriefReadOnlyScreenTest extends BriefRunScreenTestBase {

    private static final String BANNER =
            "Pause the translation to change the brief. Changes apply to segments not translated yet.";
    private static final List<String> CONTROLS = List.of(
            "brief-source",
            "brief-target",
            "brief-tone-genre",
            "brief-tone-register",
            "brief-tone-voice",
            "brief-tone-audience",
            "brief-policy-names",
            "brief-policy-balance",
            "brief-aux-nav",
            "brief-quality",
            "brief-suggest-style");

    private void waitingForTheProgram() {
        final AppError error = AppError.of(ErrorCode.unreachable, "Unreachable", "No answer.");
        mirror().review().publishProviderError(error);
        mirror().review()
                .publishRecovery(new RecoveryState(
                        RecoveryWaiting.Status.WAITING,
                        1,
                        LocalTime.of(2, 14),
                        Instant.parse("2026-10-02T03:00:00Z"),
                        error.code(),
                        30));
        WaitForAsyncUtils.waitForFxEvents();
    }

    // IF the brief stayed editable under a running translation, THEN a change would apply to some segments and not
    // others without the person seeing which.
    @ParameterizedTest
    @ValueSource(strings = {"RUNNING", "PAUSING", "STOPPING"})
    void banner_runAtWork_isShownWithItsExactText(final String state) throws Exception {
        openAndShowBrief();

        publishRun(RunState.valueOf(state), 10);

        assertThat(isShown("brief-readonly-banner")).isTrue();
        assertThat(textOf("brief-readonly-banner")).contains(BANNER);
    }

    @Test
    void controls_runTranslating_areAllDisabled() throws Exception {
        openAndShowBrief();

        publishRun(RunState.RUNNING, 10);

        assertThat(required("brief-columns").isDisabled()).isTrue();
        assertThat(CONTROLS)
                .allSatisfy(id -> assertThat(required(id).isDisabled()).as(id).isTrue());
    }

    // IF the stuck call looked editable, THEN the brief would change under a run that is still going.
    @Test
    void controls_callStuckForAMinute_stayDisabled() throws Exception {
        openAndShowBrief();
        publishRun(RunState.RUNNING, 10);

        mirror().live()
                .publishWaitingCall(new WaitingCall(
                        CallKind.DRAFT,
                        "ch2 · p05",
                        3,
                        1,
                        2,
                        Duration.ofSeconds(90),
                        Duration.ofMinutes(6),
                        Duration.ofSeconds(90),
                        true));
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(isShown("brief-readonly-banner")).isTrue();
        assertThat(required("brief-columns").isDisabled()).isTrue();
    }

    // The run retries by itself while it waits for the AI program, so an edit then is as lost as while translating.
    @Test
    void controls_waitingForTheAiProgram_areReadOnlyToo() throws Exception {
        openAndShowBrief();
        publishRun(RunState.PAUSED, 10);

        waitingForTheProgram();

        assertThat(isShown("brief-readonly-banner")).isTrue();
        assertThat(required("brief-columns").isDisabled()).isTrue();
    }

    // IF the lock outlived the pause, THEN a person who paused to change the brief could not.
    @Test
    void controls_runPaused_areEditableAndTheBannerIsGone() throws Exception {
        openAndShowBrief();
        publishRun(RunState.RUNNING, 10);

        publishRun(RunState.PAUSED, 10);

        assertThat(isShown("brief-readonly-banner")).isFalse();
        assertThat(required("brief-columns").isDisabled()).isFalse();
        assertThat(required("brief-tone-register").isDisabled()).isFalse();
    }

    @Test
    void banner_noRun_isNotShown() throws Exception {
        openAndShowBrief();

        assertThat(isShown("brief-readonly-banner")).isFalse();
        assertThat(required("brief-columns").isDisabled()).isFalse();
    }

    // IF the footer were locked with the columns, THEN a person could not even leave the brief during a run.
    @Test
    void footer_runTranslating_backStaysUsable() throws Exception {
        openAndShowBrief();

        publishRun(RunState.RUNNING, 10);

        assertThat(button("brief-back").isDisabled()).isFalse();
    }

    // IF Pause were not wired, THEN the banner would tell the person to pause and give no way to.
    @Test
    void pause_pressedOnTheBanner_asksTheRunningJobToPause() throws Exception {
        startRunThenShowBrief();

        assertThat(button("brief-readonly-pause").isDisabled()).isFalse();
        onFx(() -> button("brief-readonly-pause").fire());

        assertThat(job.calls().stream().filter("pause"::equals)).hasSize(1);
    }

    // Pause is not on offer once the run is already pausing or stopping.
    @ParameterizedTest
    @ValueSource(strings = {"PAUSING", "STOPPING"})
    void pause_runAlreadyPausingOrStopping_isDisabled(final String state) throws Exception {
        openAndShowBrief();

        publishRun(RunState.valueOf(state), 10);

        assertThat(button("brief-readonly-pause").isDisabled()).isTrue();
    }

    @Test
    void banner_runAtWork_explainsPauseOnHover() throws Exception {
        openAndShowBrief();
        publishRun(RunState.RUNNING, 10);

        assertThat(ua.bookloom.ui.TooltipProbe.tipText(required("brief-readonly-pause")))
                .isEqualTo("Pauses after the request now in flight. The brief can be changed once the translation is"
                        + " paused.");
    }
}
