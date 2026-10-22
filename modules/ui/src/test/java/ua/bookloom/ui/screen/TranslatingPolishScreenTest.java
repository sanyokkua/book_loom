package ua.bookloom.ui.screen;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeoutException;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TitledPane;
import javafx.scene.control.ToggleButton;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.testfx.util.WaitForAsyncUtils;
import ua.bookloom.api.pipeline.ReviewCounts;
import ua.bookloom.ui.LiveCallFixtures;
import ua.bookloom.ui.LiveRowFixtures;
import ua.bookloom.ui.ThemeTestSupport;
import ua.bookloom.ui.state.ConnectionStatus;
import ua.bookloom.ui.state.LiveRow;
import ua.bookloom.ui.state.LiveRow.Stage;
import ua.bookloom.ui.state.RunState;
import ua.bookloom.ui.state.Throughput;

/**
 * What the dashboard tells a person who presses or hovers its figures: Review flagged always shows something where it
 * was pressed, and the remaining tile says what it counts besides the book text.
 */
class TranslatingPolishScreenTest extends TranslatingScreenTestBase {

    private static final String PROMPT = "translating-live-call-prompt";

    private Bounds sceneBounds(final String id) {
        return ThemeTestSupport.onFx(() -> {
            final Node node = required(id);
            return node.localToScene(node.getLayoutBounds());
        });
    }

    // IF Review flagged (0) opened its panel below the live card, THEN pressing it would seem to do
    // nothing; the panel opens under the run controls, inside the viewport, and says nothing is flagged.
    @ParameterizedTest
    @EnumSource(
            value = RunState.class,
            names = {"RUNNING", "PAUSED"})
    void reviewFlagged_nothingFlagged_opensTheEmptyPanelInViewUnderTheControls(final RunState state) {
        showTranslating();
        publish(state);
        publishProgress(10, 0, 20);
        final ScrollPane pane = (ScrollPane) required("shell-content-scroll");
        onFx(() -> pane.setVvalue(0));

        onFx(() -> button("translating-review-flagged").fire());

        assertThat(isShown("review-panel")).isTrue();
        assertThat(labelText("review-empty-text")).isEqualTo("Nothing flagged — every segment passed the checks");
        final Bounds viewport = ThemeTestSupport.onFx(() -> pane.localToScene(pane.getLayoutBounds()));
        assertThat(sceneBounds("review-panel").getMinY())
                .isGreaterThanOrEqualTo(sceneBounds("translating-head").getMaxY())
                .isLessThan(viewport.getMaxY());
        assertThat(sceneBounds("review-panel").getMaxY())
                .isLessThanOrEqualTo(sceneBounds("translating-strip").getMinY());
    }

    // The row of segment 1 records call 1, so the Detailed detail shows that call's prompt section.
    private void runningWithPrompt(final long callId) {
        final LiveRow row = new LiveRow("s-1-1", "ch7 · p101", "Text.", null, Stage.IN_CALL, null, List.of(), callId);
        showLive(LiveRowFixtures.calls(
                LiveCallFixtures.waiting(callId, 2, "Text.", LiveCallFixtures.smallPrompt()), List.of(row)));
        onFx(() -> ((ToggleButton) required("translating-live-mode-detailed")).fire());
        WaitForAsyncUtils.waitForFxEvents();
    }

    private boolean isExpanded(final String id) {
        return ThemeTestSupport.onFx(() -> ((TitledPane) required(id)).isExpanded());
    }

    // IF the prompt the person opened closed itself whenever they looked at another step, THEN they would have to
    // reopen it on every return; the section stays as they left it, open or closed again.
    @Test
    void promptSection_openedThenAnotherStepVisited_isStillOpenOnReturn() {
        runningWithPrompt(1);
        onFx(() -> ((TitledPane) required(PROMPT)).setExpanded(true));

        showTranslating();

        assertThat(isExpanded(PROMPT)).isTrue();
        onFx(() -> ((TitledPane) required(PROMPT)).setExpanded(false));
        showTranslating();
        assertThat(isExpanded(PROMPT)).isFalse();
    }

    // IF the open section closed when the next call took its place, THEN it could not be read during a run.
    @Test
    void promptSection_opened_staysOpenWhenTheCallChanges() {
        runningWithPrompt(1);
        onFx(() -> ((TitledPane) required(PROMPT)).setExpanded(true));

        mirror().live()
                .publishCalls(LiveRowFixtures.calls(
                        LiveCallFixtures.waiting(2, 2, "Text.", LiveCallFixtures.smallPrompt()),
                        List.of(new LiveRow(
                                "s-1-1", "ch7 · p101", "Text.", null, Stage.IN_CALL, null, List.of(), 2L))));
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(isExpanded(PROMPT)).isTrue();
    }

    // IF the Flagged tile kept the run's last figure while Review flagged counts what is still flagged, THEN a person
    // who had cleared two segments would be given two numbers for one thing.
    @Test
    void flaggedFigures_deskCountsFewerThanTheRunEndedWith_allShowTheDesksCount() throws TimeoutException {
        readyToStart();
        desk.willAnswerCounts(new ReviewCounts(100, 90, 0, 3, 0, 0, 0, 1, 0));
        showTranslating();
        publishProgress(90, 5, 0);

        publish(RunState.COMPLETED);

        awaitFx(() -> "3 to review".equals(labelText("translating-count-to-review")));
        assertThat(button("translating-review-flagged").getText()).isEqualTo("Review (3)");
    }

    // IF the run's own figure were replaced while it is still deciding, THEN the desk's read, which lags the run's last
    // flush, would make the tile fall back.
    @Test
    void flaggedTile_runStillGoing_keepsTheRunsLiveFigure() throws TimeoutException {
        readyToStart();
        desk.willAnswerCounts(new ReviewCounts(100, 90, 0, 3, 0, 0, 0, 1, 0));
        showTranslating();

        publish(RunState.RUNNING);
        publishProgress(80, 5, 10);

        assertThat(labelText("translating-count-to-review")).isEqualTo("5 to review");
    }

    private void endedRunWithZeroLeft(final RunState state) {
        mirror().publishRunStarted("Frankenstein.epub", null);
        publishProgress(100, 0, 0);
        mirror().live().publishThroughput(new Throughput(null, false, Duration.ZERO, Duration.ofMinutes(62)));
        mirror().live().publishConnection(new ConnectionStatus(Duration.ZERO, 0, 0, null, null));
        publish(state);
    }

    // IF "~0s left" stayed after the run ended, THEN the title bar and the card would promise work that is over.
    @ParameterizedTest
    @EnumSource(
            value = RunState.class,
            names = {"COMPLETED", "STOPPED", "FAILED"})
    void timeLeft_runEnded_isGoneFromTheTitleBarAndTheProgressCard(final RunState state) {
        showTranslating();

        endedRunWithZeroLeft(state);

        assertThat(labelText("shell-run-state")).doesNotContain("left");
        assertThat(textOf("translating-strip")).doesNotContain("left");
    }

    @Test
    void timeLeft_runGoing_isShownInTheTitleBar() {
        showTranslating();
        mirror().publishRunStarted("Frankenstein.epub", null);
        mirror().live().publishThroughput(new Throughput(null, false, Duration.ofMinutes(5), Duration.ofMinutes(62)));

        publish(RunState.RUNNING);

        assertThat(labelText("shell-run-state")).isEqualTo("0 % done · about 5 min left");
        assertThat(labelText("translating-strip-big")).isEqualTo("about 5 min left");
    }

    // IF the chip still said the model answers after the run ended, THEN it would claim a live conversation.
    @ParameterizedTest
    @CsvSource({"COMPLETED, Model idle", "STOPPED, Model idle", "FAILED, Error"})
    void connectionChip_runEnded_saysTheModelIsIdleOrTheRunFailed(final RunState state, final String chip) {
        showTranslating();

        endedRunWithZeroLeft(state);

        assertThat(button("shell-run-connection").getText()).isEqualTo(chip);
    }

    // IF the chip's hover lost when the model last answered, THEN a quiet run could not be told from a dead one.
    @Test
    void connectionChip_runGoing_saysTheModelAnswersAndItsHoverWhenItLastDid() {
        showTranslating();
        mirror().publishRunStarted("Frankenstein.epub", null);
        mirror().live().publishConnection(new ConnectionStatus(Duration.ofSeconds(4), 0, 0, null, null));

        publish(RunState.RUNNING);

        assertThat(button("shell-run-connection").getText()).isEqualTo("Model answering");
        assertThat(ua.bookloom.ui.TooltipProbe.tipText(required("shell-run-connection")))
                .contains("Last answer: 0:04 ago");
    }
}
