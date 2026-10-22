package ua.bookloom.ui.screen;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.LocalTime;
import java.util.List;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import org.junit.jupiter.api.Test;
import org.testfx.util.WaitForAsyncUtils;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.pipeline.CallKind;
import ua.bookloom.api.pipeline.ChunkPosition;
import ua.bookloom.api.pipeline.ModelCallFinished;
import ua.bookloom.api.pipeline.PauseReason;
import ua.bookloom.api.pipeline.Paused;
import ua.bookloom.api.pipeline.SegmentStarted;
import ua.bookloom.ui.ProgressFixtures;
import ua.bookloom.ui.ThemeTestSupport;
import ua.bookloom.ui.TooltipProbe;
import ua.bookloom.ui.ViewNames;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.state.ConnectionStatus;
import ua.bookloom.ui.state.LogEntry;
import ua.bookloom.ui.state.LogKind;
import ua.bookloom.ui.state.RunState;
import ua.bookloom.ui.state.WaitingCall;

/**
 * What the dashboard shows about the model while a run waits on it or paused on it: the request it waits on, the way
 * out of a stuck one, the failed call a pause names, the log's errors-only view and the connection chip.
 */
class TranslatingObservabilityScreenTest extends TranslatingScreenTestBase {

    private static final String SKIP = "translating-skip-segment";
    private static final String RETRY = "translating-retry-now";
    private static final String SETTINGS = "translating-open-settings";
    private static final Duration TIMEOUT = Duration.ofSeconds(90);

    private void waitOn(final WaitingCall call) {
        mirror().live().publishWaitingCall(call);
        mirror().publishWaitingSeconds((int) call.attemptWaited().toSeconds());
        WaitForAsyncUtils.waitForFxEvents();
    }

    private static WaitingCall reviewCall(final int attempt, final long waited, final long total, final boolean stuck) {
        return new WaitingCall(
                CallKind.REVIEW,
                "ch9 · p02",
                0,
                attempt,
                2,
                Duration.ofSeconds(waited),
                TIMEOUT,
                Duration.ofSeconds(total),
                stuck);
    }

    private List<String> shownActions() {
        return List.of(RETRY, SKIP, SETTINGS).stream().filter(this::isShown).toList();
    }

    private void runningOnTheShell() {
        chooseModel();
        showTranslating();
        mirror().publishRunStarted("Frankenstein.epub", null);
        publish(RunState.RUNNING);
    }

    // IF a call under a minute read as stuck, THEN a slow model would look broken (translation-pipeline "Show a stuck
    // call with one clock": under a minute, no amber chip and no stuck banner).
    @Test
    void waiting_underAMinute_isTranslatingWithNoBannerAndNoAmberChip() {
        runningOnTheShell();

        waitOn(reviewCall(2, 59, 89, false));

        assertThat(optional("translating-banner")).isNull();
        assertThat(button("shell-run-connection").getText()).isEqualTo("Model answering");
        assertThat(required("shell-run-connection").getStyleClass()).doesNotContain("state-attention");
    }

    // IF a stuck call showed two clocks or asked the person to act, THEN a slow model would read as a broken one; it
    // shows one clock against the call's own limit and says nothing needs doing.
    @Test
    void stuck_atAMinute_showsTheAmberChipAndTheBannerWithOneClockAndNoAction() {
        runningOnTheShell();

        waitOn(reviewCall(1, 61, 61, true));

        assertThat(button("shell-run-connection").getText()).isEqualTo("No answer for 1:01");
        assertThat(required("shell-run-connection").getStyleClass()).contains("state-attention");
        assertThat(labelText("translating-banner-title"))
                .isEqualTo("The model is taking longer than usual (1:01). BookLoom tries again by itself at 1:30."
                        + " You do not need to do anything.");
        assertThat(required("translating-banner").getStyleClass()).contains("banner-warn");
        assertThat(shownActions()).isEmpty();
        assertThat(button("shell-run-control").getText()).isEqualTo("Pause");
    }

    // IF the pause did not offer Try again now, Skip and Open settings, THEN the person could only wait or stop; Skip
    // reaches the job.
    @Test
    void providerError_pausedOnATimeout_offersTryAgainSkipAndSettingsAndSkipReachesTheJob() throws Exception {
        readyToStart();
        showTranslating();
        onFx(() -> button("translating-start").fire());
        job.awaitRunStarted();
        job.emit(new SegmentStarted("s-2", "ch9 · p02", "Text.", new ChunkPosition(9, 81, 1, 7)));
        job.emit(new ModelCallFinished(
                "s-2", CallKind.REVIEW, TIMEOUT, null, 0, false, List.of("s-2"), 2, ErrorCode.timeout));
        job.emit(new Paused(
                PauseReason.ON_ERROR,
                AppError.of(ErrorCode.timeout, "Model server timed out", "The server did not answer."),
                ProgressFixtures.progress(1, 2, 412, 0, 5),
                "s-2",
                2,
                2));
        awaitFx(() -> isShown(RETRY));

        assertThat(labelText("translating-banner-title")).isEqualTo("Paused. Ollama could not be reached.");
        assertThat(labelText("translating-banner-text")).isEqualTo("Open Ollama, then press Resume.");
        assertThat(shownActions()).containsExactly(RETRY, SKIP, SETTINGS);
        assertThat(button(RETRY).getText()).isEqualTo("Try again now");

        onFx(() -> button(SKIP).fire());

        assertThat(job.calls()).last().isEqualTo("skipSegment");
    }

    // IF the run controls sat under the log, THEN during a stall they would be out of view below the fold; the live
    // line is in the live card, below the strip, and the controls are in the toolbar.
    @Test
    void liveLine_isInTheLiveCardAndTheControlsAreInTheToolbar() {
        showTranslating();
        publish(RunState.RUNNING);

        final Node head = required("translating-head");
        assertThat(head.lookup("#translating-live-line")).isNull();
        assertThat(required("translating-live-card").lookup("#translating-live-line"))
                .isNotNull();
        assertThat(head.lookup("#translating-pause")).isNull();
        assertThat(required("shell-actions").lookup("#translating-pause")).isNotNull();
        assertThat(head.lookup("#translating-live-card")).isNull();
    }

    // IF the log could not hide the routine lines, THEN one failure would be lost among hundreds of answered calls.
    @Test
    void problemsOnly_chosen_keepsOnlyTheTroubleLines() {
        showTranslating();
        publish(RunState.RUNNING);
        publishLog(
                new LogEntry(LogKind.ACCEPTED, List.of("ch9 · p01")),
                LogEntry.of(
                        LogKind.RETRIED,
                        MessageKey.LOG_CALL_FAILED,
                        List.of("review", " · ch9 · p02", "0", "1", "1:30", "timeout")),
                new LogEntry(LogKind.MILESTONE, List.of("paused")));
        openActivityLog();

        onFx(() -> ((ToggleButton) required("translating-log-problems")).fire());

        assertThat(logCells()).extracting(cell -> cell.getItem().kind()).containsExactly(LogKind.RETRIED);
        assertThat(TooltipProbe.tipText(required("translating-log-problems"))).isNotBlank();
    }

    // IF a repeated line were not counted, or the time not shown, THEN a stall's length could not be read off the log.
    @Test
    void line_showsItsTimeAndHowOftenItRepeated() {
        showTranslating();
        publish(RunState.RUNNING);
        final LogEntry failed = new LogEntry(LogKind.SEGMENT_ERROR, List.of("ch9 · p02"), LocalTime.of(8, 43, 0), 1);

        publishLog(failed, failed, failed, failed);
        openActivityLog();

        final Node cell = logCells().getFirst();
        assertThat(((Label) cell.lookup(".log-time")).getText()).isEqualTo("08:43:00");
        assertThat(((Label) cell.lookup(".log-repeats")).getText()).isEqualTo("×4");
    }

    // IF the log measured every line as it scrolled, THEN a long run's log would stutter; its lines share one height.
    @Test
    void log_shown_linesShareOneFixedHeight() {
        showTranslating();
        publish(RunState.RUNNING);
        openActivityLog();

        assertThat(logList().getFixedCellSize()).isEqualTo(34.0);
    }

    // IF the chip's hover lost how the server answers, THEN a dead server would look like a slow book; the chip leads
    // to
    // the settings.
    @Test
    void connectionChip_hovered_givesTheServerFiguresAndOpensTheSettings() {
        showTranslating();
        mirror().publishRunStarted("Frankenstein.epub", null);
        mirror().live().publishConnection(new ConnectionStatus(Duration.ofSeconds(200), 2, 3, 31.0, 48.0));
        WaitForAsyncUtils.waitForFxEvents();

        final Button chip = (Button) required("shell-run-connection");
        assertThat(TooltipProbe.tipText(chip))
                .contains("Last answer: 3:20 ago")
                .contains("Timeouts in the last 10 minutes: 2")
                .contains("Drafting: 31 tok/s · Judging: 48 tok/s");

        onFx(chip::fire);

        assertThat(ThemeTestSupport.onFx(() -> navigator.currentView().get())).isEqualTo(ViewNames.SETTINGS);
    }
}
