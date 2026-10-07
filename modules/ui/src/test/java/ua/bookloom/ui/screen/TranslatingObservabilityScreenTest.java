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
    private static final String AGAIN = "translating-send-again";
    private static final String PAUSE_STUCK = "translating-pause-stuck";
    private static final String RETRY = "translating-retry-now";
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
        return List.of(RETRY, SKIP, AGAIN, PAUSE_STUCK).stream()
                .filter(this::isShown)
                .toList();
    }

    // IF the banner said only "waiting", THEN a person could not tell a slow draft from a judge on its last attempt.
    @Test
    void waiting_namesTheCallTheSegmentTheAttemptAndBothClocks() {
        showTranslating();
        publish(RunState.RUNNING);

        waitOn(reviewCall(2, 12, 102, false));

        assertThat(labelText("translating-banner-text"))
                .isEqualTo("Review · ch9 · p02 · attempt 2 of 2 · 0:12 of 1:30 · 1:42 in all");
        assertThat(shownActions()).isEmpty();
    }

    // IF a stuck call offered nothing, THEN the person's only way out would be to stop the whole run.
    @Test
    void stuck_turnsIntoAWarningWithSkipRetryAndPause() {
        showTranslating();
        publish(RunState.RUNNING);

        waitOn(reviewCall(1, 61, 61, true));

        assertThat(labelText("translating-banner-title")).isEqualTo("The model is slow to answer");
        assertThat(labelText("translating-banner-text"))
                .startsWith("Review · ch9 · p02 · attempt 1 of 2 · 1:01 of 1:30\n")
                .contains("Skip the segment");
        assertThat(required("translating-banner").getStyleClass()).contains("banner-warn");
        assertThat(shownActions()).containsExactly(SKIP, AGAIN, PAUSE_STUCK);
    }

    // IF Skip segment on a stuck call did not reach the job, THEN the button would promise what it cannot do.
    @Test
    void skip_pressedOnAStuckCall_pausesTheJobFirst() throws Exception {
        readyToStart();
        showTranslating();
        onFx(() -> button("translating-start").fire());
        job.awaitRunStarted();
        awaitFx(() -> isShown("translating-pause"));
        waitOn(reviewCall(1, 61, 61, true));

        onFx(() -> button(SKIP).fire());

        assertThat(job.calls()).contains("pause");
    }

    // IF the pause did not name the failed call and what Retry now does, THEN the person would resume blind.
    @Test
    void providerError_namesTheFailedCallThePausesSpentAndWhatRetryDoes() throws Exception {
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

        assertThat(labelText("translating-banner-text"))
                .contains("Review · ch9 · p02 failed: timeout — pause 2 of 2.")
                .contains("if it fails again, the segment is flagged")
                .contains("kept in memory until the application closes")
                .doesNotContain("no work was lost");
        assertThat(shownActions()).containsExactly(RETRY, SKIP);

        onFx(() -> button(SKIP).fire());

        assertThat(job.calls()).last().isEqualTo("skipSegment");
    }

    // IF the controls sat under the log, THEN during a stall they would be out of view below the fold.
    @Test
    void runControls_sitBesideTheBannerAboveTheLivePanel() {
        showTranslating();
        publish(RunState.RUNNING);

        final Node head = required("translating-head");
        assertThat(head.lookup("#translating-pause")).isNotNull();
        assertThat(head.lookup("#translating-review-flagged")).isNotNull();
        assertThat(head.lookup("#translating-live-card")).isNull();
    }

    // IF the log could not hide the routine lines, THEN one failure would be lost among hundreds of answered calls.
    @Test
    void errorsOnly_switchedOn_keepsOnlyTheTroubleLines() {
        showTranslating();
        publish(RunState.RUNNING);
        publishLog(
                new LogEntry(LogKind.ACCEPTED, List.of("ch9 · p01")),
                new LogEntry(LogKind.CALL_FAILED, List.of("review", " · ch9 · p02", "0", "1", "1:30", "timeout")),
                new LogEntry(LogKind.MILESTONE, List.of("paused")));

        onFx(() -> ((ToggleButton) required("translating-log-errors-only")).fire());

        assertThat(logCells()).extracting(cell -> cell.getItem().kind()).containsExactly(LogKind.CALL_FAILED);
        assertThat(TooltipProbe.tipText(required("translating-log-errors-only")))
                .isNotBlank();
    }

    // IF a repeated line were not counted, or the time not shown, THEN a stall's length could not be read off the log.
    @Test
    void line_showsItsTimeAndHowOftenItRepeated() {
        showTranslating();
        publish(RunState.RUNNING);
        final LogEntry failed = new LogEntry(
                LogKind.CALL_FAILED,
                List.of("review", " · ch9 · p02", "0", "1", "1:30", "timeout"),
                LocalTime.of(8, 43, 0),
                1);

        publishLog(failed, failed, failed, failed);

        final Node cell = logCells().getFirst();
        assertThat(((Label) cell.lookup(".log-time")).getText()).isEqualTo("08:43:00");
        assertThat(((Label) cell.lookup(".log-repeats")).getText()).isEqualTo("×4");
    }

    // IF the log measured every line as it scrolled, THEN a long run's log would stutter; its lines share one height.
    @Test
    void log_shown_linesShareOneFixedHeight() {
        showTranslating();

        assertThat(logList().getFixedCellSize()).isEqualTo(34.0);
    }

    // IF the chip did not say how the server answers, THEN a dead server would look like a slow book.
    @Test
    void connectionChip_unsteady_countsTheFailuresAndOpensTheSettings() {
        showTranslating();
        mirror().publishRunStarted("Frankenstein.epub", null);
        mirror().live().publishConnection(new ConnectionStatus(Duration.ofSeconds(200), 2, 3, 31.0, 48.0));
        WaitForAsyncUtils.waitForFxEvents();

        final Button chip = (Button) required("shell-run-connection");
        assertThat(chip.getText()).isEqualTo("● 3 failed calls in 10 min");
        assertThat(chip.getStyleClass()).contains("conn-unsteady");
        assertThat(TooltipProbe.tipText(chip))
                .contains("Timeouts in the last 10 minutes: 2")
                .contains("Drafting: 31 tok/s · Judging: 48 tok/s");

        onFx(chip::fire);

        assertThat(ThemeTestSupport.onFx(() -> navigator.currentView().get())).isEqualTo(ViewNames.SETTINGS);
    }

    // IF the chip showed nothing while the server answered, THEN a person could not tell a quiet run from a dead one.
    @Test
    void connectionChip_steady_saysWhenTheModelLastAnswered() {
        showTranslating();
        mirror().publishRunStarted("Frankenstein.epub", null);
        mirror().live().publishConnection(new ConnectionStatus(Duration.ofSeconds(4), 0, 0, null, null));
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(((Button) required("shell-run-connection")).getText()).isEqualTo("● Model answered 0:04 ago");
    }
}
