package ua.bookloom.ui.screen;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.testfx.util.WaitForAsyncUtils;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.pipeline.RecoveryWaiting;
import ua.bookloom.ui.ProgressFixtures;
import ua.bookloom.ui.state.RecoveryState;
import ua.bookloom.ui.state.RunState;

/**
 * A run left overnight that waits for its provider says so everywhere a person looks in the morning: the banner with
 * the countdown, the attempt and when the outage began, the title bar's state and its connection chip, and the controls
 * to try at once, skip the segment, hold the run or stop it.
 */
class TranslatingScreenRecoveryTest extends TranslatingScreenTestBase {

    private static final String RETRY = "translating-retry-now";
    private static final String SKIP = "translating-skip-segment";
    private static final String STOP_RUN = "translating-stop-run";
    private static final String SETTINGS = "translating-open-settings";
    private static final String STAY = "translating-stay-paused";
    private static final AppError UNREACHABLE = AppError.of(
            ErrorCode.unreachable, "Model server unreachable", "Nothing is listening.", "endpointHost=localhost", null);

    private static final AppError UNLOADED = AppError.of(
            ErrorCode.modelUnavailable,
            "Provider model is not loaded",
            "The provider has no model loaded to answer the request.",
            "httpStatus=400, endpointHost=localhost:1234",
            null);

    private void waitForProvider(final RecoveryWaiting.Status status, final int attempt, final long secondsLeft) {
        waitForProvider(UNREACHABLE, status, attempt, secondsLeft);
    }

    private void waitForProvider(
            final AppError error, final RecoveryWaiting.Status status, final int attempt, final long secondsLeft) {
        mirror().publishRunStarted("Frankenstein.epub", null);
        mirror().publishProgress(ProgressFixtures.progress(7, 11, 78, 0, 22));
        mirror().publishRunState(RunState.PAUSED);
        mirror().review().publishProviderError(error);
        mirror().review()
                .publishRecovery(new RecoveryState(
                        status,
                        attempt,
                        LocalTime.of(2, 14),
                        status == RecoveryWaiting.Status.WAITING ? Instant.parse("2026-10-02T03:00:00Z") : null,
                        error.code(),
                        secondsLeft));
        WaitForAsyncUtils.waitForFxEvents();
    }

    private List<String> shownActions() {
        return List.of(RETRY, SKIP, SETTINGS, STAY, STOP_RUN).stream()
                .filter(this::isShown)
                .toList();
    }

    // IF the waiting run looked like any other paused error, THEN in the morning it would read as stuck.
    @Test
    void banner_waitingForTheProvider_namesTheCountdownAttemptAndOutageStartWithItsActions() {
        showTranslating();

        waitForProvider(RecoveryWaiting.Status.WAITING, 6, 252);

        assertThat(labelText("translating-banner-title")).isEqualTo("Waiting for the provider");
        assertThat(labelText("translating-banner-text"))
                .contains("Next try in 4:12 · attempt 6 · down since 02:14 · last check: unreachable")
                .contains("resumes by itself");
        assertThat(required("translating-banner").getStyleClass()).contains("banner-warn");
        assertThat(shownActions()).containsExactly(RETRY, SKIP, STOP_RUN);
        assertThat(enabledControls()).containsExactly("translating-pause", "translating-resume", "translating-stop");
        assertThat(button("translating-resume").getText()).isEqualTo("Retry now");
    }

    @Test
    void titleBar_waitingForTheProvider_saysSoAndTheChipSaysRetrying() {
        waitForProvider(RecoveryWaiting.Status.WAITING, 6, 252);

        assertThat(labelText("shell-run-state")).isEqualTo("Waiting for the provider · next try in 4:12");
        assertThat(button("shell-run-connection").getText()).isEqualTo("● Provider unreachable · retrying");
        assertThat(required("shell-run-connection").getStyleClass()).contains("conn-unsteady");
    }

    // IF a run past the twelve-hour limit still read as retrying, THEN the person would wait for a retry that never
    // comes.
    @Test
    void banner_gaveUpAfterTwelveHours_saysTheRunWaitsForThePerson() {
        showTranslating();

        waitForProvider(RecoveryWaiting.Status.GAVE_UP, 77, 0);

        assertThat(labelText("translating-banner-title")).isEqualTo("The provider has been down for over 12 hours");
        assertThat(labelText("translating-banner-text")).contains("Down since 02:14 · 77 tries");
        assertThat(required("translating-banner").getStyleClass()).contains("banner-err");
        assertThat(shownActions()).containsExactly(RETRY, SKIP, SETTINGS, STAY);
        assertThat(button("translating-resume").getText()).isEqualTo("Resume");
        assertThat(labelText("shell-run-state")).isEqualTo("Provider error");
    }

    // IF a model nobody loads read like a provider outage, THEN the person would wait for a server that is already up.
    @Test
    void banner_unloadedModelGaveUp_asksThePersonToLoadItAndResume() {
        showTranslating();

        waitForProvider(UNLOADED, RecoveryWaiting.Status.GAVE_UP, 6, 0);

        assertThat(labelText("translating-banner-title")).isEqualTo("The model is not available");
        assertThat(labelText("translating-banner-text"))
                .contains("load it in the provider at localhost:1234 and press Resume")
                .contains("6 tries since 02:14");
        assertThat(required("translating-banner").getStyleClass()).contains("banner-err");
        assertThat(shownActions()).containsExactly(RETRY, SKIP, SETTINGS, STAY);
    }
}
