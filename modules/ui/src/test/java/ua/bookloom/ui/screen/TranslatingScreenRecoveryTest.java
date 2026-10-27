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
 * A run left overnight that waits for its AI program says so everywhere a person looks in the morning (README "Run
 * states": Waiting for the AI model, Stopped trying, Model missing): the banner with the countdown and its actions, the
 * title bar's state and its red chip, and the toolbar's Pause that stops the trying.
 */
class TranslatingScreenRecoveryTest extends TranslatingScreenTestBase {

    private static final String RETRY = "translating-retry-now";
    private static final String SKIP = "translating-skip-segment";
    private static final String SETTINGS = "translating-open-settings";
    private static final String CHOOSE_MODEL = "translating-choose-model";
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
        chooseModel();
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
        return List.of(RETRY, SKIP, SETTINGS, CHOOSE_MODEL).stream()
                .filter(this::isShown)
                .toList();
    }

    // IF the waiting run looked like any other paused error, THEN in the morning it would read as stuck.
    @Test
    void banner_waitingForTheProgram_namesItAndTheNextTryWithItsThreeActions() {
        showTranslating();

        waitForProvider(RecoveryWaiting.Status.WAITING, 6, 252);

        assertThat(labelText("translating-banner-title")).isEqualTo("BookLoom cannot reach Ollama");
        assertThat(labelText("translating-banner-text"))
                .isEqualTo("Is the Ollama app open? BookLoom tries again by itself in 4:12.");
        assertThat(required("translating-banner").getStyleClass()).contains("banner-warn");
        assertThat(shownActions()).containsExactly(RETRY, SKIP, SETTINGS);
        assertThat(enabledControls()).containsExactly("translating-pause", "translating-stop");
        assertThat(button("translating-pause").getText()).isEqualTo("Pause");
    }

    @Test
    void titleBar_waitingForTheProgram_saysSoAndTheChipCannotReachIt() {
        waitForProvider(RecoveryWaiting.Status.WAITING, 6, 252);

        assertThat(labelText("shell-run-state")).isEqualTo("Waiting for Ollama");
        assertThat(button("shell-run-connection").getText()).isEqualTo("Cannot reach Ollama");
        assertThat(required("shell-run-connection").getStyleClass()).contains("state-failed");
        assertThat(button("shell-run-control").getText()).isEqualTo("Pause");
    }

    // IF Pause did not stop the trying, THEN a person who wants the program closed overnight could not hold the run.
    @Test
    void pause_pressedWhileWaiting_asksTheRunnerToHoldTheRun() throws Exception {
        readyToStart();
        showTranslating();
        onFx(() -> button("translating-start").fire());
        job.awaitRunStarted();
        waitForProvider(RecoveryWaiting.Status.WAITING, 1, 12);

        onFx(() -> button("translating-pause").fire());

        assertThat(job.calls()).contains("pause");
    }

    // IF a run past the twelve-hour limit still read as retrying, THEN the person would wait for a retry that never
    // comes.
    @Test
    void banner_gaveUpAfterTwelveHours_saysTheRunWaitsForThePerson() {
        showTranslating();

        waitForProvider(RecoveryWaiting.Status.GAVE_UP, 77, 0);

        assertThat(labelText("translating-banner-title"))
                .isEqualTo("Ollama has not answered for 12 hours — BookLoom stopped trying by itself.");
        assertThat(required("translating-banner").getStyleClass()).contains("banner-err");
        assertThat(shownActions()).containsExactly(RETRY, SKIP, SETTINGS);
        assertThat(button("translating-resume").getText()).isEqualTo("Try again now");
        assertThat(labelText("shell-run-state")).isEqualTo("Paused at 78 %");
        assertThat(button("shell-run-connection").getText()).isEqualTo("Ollama: no answer for 12 h");
    }

    // IF a model nobody loads read like a program outage, THEN the person would wait for a program that is already up.
    @Test
    void banner_unloadedModelGaveUp_namesTheModelAndOffersAnother() {
        showTranslating();

        waitForProvider(UNLOADED, RecoveryWaiting.Status.GAVE_UP, 6, 0);

        assertThat(labelText("translating-banner-title"))
                .isEqualTo("The AI model gemma3:12b is not available — check that it is installed.");
        assertThat(required("translating-banner").getStyleClass()).contains("banner-err");
        assertThat(shownActions()).containsExactly(CHOOSE_MODEL);
        assertThat(button("shell-run-connection").getText()).isEqualTo("Model missing");
    }
}
