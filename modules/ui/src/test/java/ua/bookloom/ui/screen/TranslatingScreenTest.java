package ua.bookloom.ui.screen;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.List;
import java.util.concurrent.TimeoutException;
import java.util.stream.IntStream;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.Region;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.testfx.util.WaitForAsyncUtils;
import ua.bookloom.ui.ThemeTestSupport;
import ua.bookloom.ui.ViewNames;
import ua.bookloom.ui.state.LogEntry;
import ua.bookloom.ui.state.LogKind;
import ua.bookloom.ui.state.RunState;

/**
 * The translating dashboard the application builds, read from the real scene: the controls each run state offers, the
 * figures and the log as the mirror publishes them, and the neutral reading of a stop. Every publication reaches the
 * screen one FX pulse late, so each driver waits for the queue before an assertion reads a node.
 */
class TranslatingScreenTest extends TranslatingScreenTestBase {

    // IF a state offered a control it cannot honour, or hid one it can, THEN the person would press the wrong thing.
    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', textBlock = """
            IDLE      | translating-start                    | -
            RUNNING   | translating-pause translating-stop   | -
            PAUSING   | translating-stop                     | translating-pause
            PAUSED    | translating-resume translating-stop  | -
            STOPPING  | -                                    | translating-stop
            STOPPED   | translating-new-run                  | -
            COMPLETED | translating-new-run                  | -
            FAILED    | translating-new-run                  | -
            """)
    void controls_eachRunState_showTheStatedButtonsAvailableOrNot(
            final RunState state, final String enabled, final String disabled) {
        showTranslating();

        publish(state);

        assertThat(enabledControls()).containsExactlyInAnyOrderElementsOf(ids(enabled));
        assertThat(disabledControls()).containsExactlyInAnyOrderElementsOf(ids(disabled));
    }

    // IF the banner said the wrong thing for a state, THEN a pending request would read as one already honoured.
    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', textBlock = """
            IDLE      | Ready to translate      | banner-info
            RUNNING   | Translating             | banner-info
            PAUSING   | Pausing                 | banner-info
            PAUSED    | Paused                  | banner-info
            STOPPING  | Stopping                | banner-info
            STOPPED   | Run stopped             | banner-info
            COMPLETED | Translation finished    | banner-ok
            FAILED    | The run did not finish  | banner-warn
            """)
    void banner_eachRunState_carriesTheStatedTitleAndRole(
            final RunState state, final String title, final String roleClass) {
        showTranslating();

        publish(state);

        assertThat(labelText("translating-banner-title")).isEqualTo(title);
        assertThat(required("translating-banner").getStyleClass()).contains("banner", roleClass);
    }

    // IF the derived figures were shown wrongly, THEN the dashboard would contradict what the engine reports.
    @Test
    void figures_engineReports768Accepted3Flagged469Pending_showTheFourCountsAndTheProportion() {
        showTranslating();

        publishProgress(768, 3, 469);

        assertThat(labelText("translating-count-accepted")).isEqualTo("768");
        assertThat(labelText("translating-count-flagged")).isEqualTo("3");
        assertThat(labelText("translating-count-remaining")).isEqualTo("469");
        assertThat(labelText("translating-count-total")).isEqualTo("1,240");
        assertThat(labelText("translating-progress-text")).isEqualTo("771 of 1,240 segments processed");
        assertThat(labelText("translating-remaining-text")).isEqualTo("469 segments remaining");
        assertThat(progressBar().getProgress()).isCloseTo(0.6218, within(0.0001));
    }

    // IF an empty book divided by its total, THEN the dashboard would show NaN instead of zero.
    @Test
    void figures_emptyBook_showZeroProgressWithoutFailing() {
        showTranslating();

        publishProgress(0, 0, 0);

        assertThat(labelText("translating-progress-text")).isEqualTo("0 of 0 segments processed");
        assertThat(progressBar().getProgress()).isZero();
    }

    // IF counts froze while a pause was pending, THEN the run would look dead when it is still translating.
    @Test
    void pausing_countsAdvanceWhilePending_keepCountingAndStillReadAsPausing() {
        showTranslating();
        publish(RunState.RUNNING);
        publishProgress(412, 3, 800);
        publish(RunState.PAUSING);

        publishProgress(413, 3, 799);

        assertThat(labelText("translating-count-accepted")).isEqualTo("413");
        assertThat(labelText("translating-banner-title")).isEqualTo("Pausing");
        assertThat(button("translating-pause").isDisabled()).isTrue();
    }

    // IF a pending stop read as stopped, THEN the person would think the model had stopped working when it had not.
    @Test
    void stopping_pendingStop_readsAsStoppingUntilTheRunReturns() {
        showTranslating();
        publish(RunState.RUNNING);

        publish(RunState.STOPPING);

        assertThat(labelText("translating-banner-title")).isEqualTo("Stopping");
        assertThat(button("translating-stop").isDisabled()).isTrue();
    }

    // IF a stop were dressed as a failure, THEN a choice the person made would be reported as if it had gone wrong.
    @Test
    void stopped_cancelledRunReturns_isANeutralOutcomeWithNewRunAndNoResumeOrErrorSurface() {
        showTranslating();
        publish(RunState.RUNNING);

        mirror().publishOutcome(RunState.STOPPED, cancelledReport(), null);
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(labelText("translating-banner-title")).isEqualTo("Run stopped");
        assertThat(required("translating-banner").getStyleClass()).doesNotContain("banner-err", "banner-warn");
        assertThat(textOf("translating-banner")).doesNotContainIgnoringCase("error", "fail");
        assertThat(isShown("translating-new-run")).isTrue();
        assertThat(isShown("translating-resume")).isFalse();
        assertThat(optional("error-card")).isNull();
        assertThat(scene.getRoot().lookupAll(".toast-err")).isEmpty();
    }

    // IF the dashboard hid itself with no book open, THEN task 9.2's message would have nowhere to appear.
    @Test
    void screen_noBookOpen_showsTheDashboardWithItsStartControlAndNoNoBookState() {
        showTranslating();

        assertThat(isShown("translating-start")).isTrue();
        assertThat(optional("nobook-card")).isNull();
    }

    // IF a rate or a finishing time were shown, THEN the dashboard would report something the engine never emits.
    @Test
    void screen_runInProgress_showsNoRateNoEstimateAndNoNodeForOne() {
        showTranslating();
        publish(RunState.RUNNING);
        publishProgress(768, 3, 469);

        assertThat(textsUnder(required("translating-screen")))
                .noneMatch(text -> text.matches("(?i).*(tok/s|\\bETA\\b|\\bleft\\b|per second|estimate|throughput).*"));
        assertThat(idsStartingWith("translating-"))
                .noneMatch(id -> id.matches(".*(rate|eta|speed|throughput|estimate).*"));
    }

    // IF a run could be started from an earlier step, THEN the specification's single start control would be two.
    @ParameterizedTest
    @EnumSource(
            value = ViewNames.class,
            names = {"IMPORT", "BOOK_BRIEF", "STRUCTURE", "SETTINGS"})
    void startControl_everyOtherWorkflowScreen_hasNone(final ViewNames view) throws TimeoutException {
        readyToStart();
        onFx(() -> shell.activate(ViewNames.EXPORT));

        onFx(() -> shell.activate(view));

        assertThat(textsUnder(scene.getRoot())).doesNotContain("Start translation", "New run");
        assertThat(idsStartingWith("translating-")).isEmpty();
    }

    // IF the log dropped its mark or its text, THEN a reader without colour would see identical lines.
    @Test
    void log_entriesPublished_renderEachWithItsMarkAndCatalogueText() {
        showTranslating();

        publishLog(new LogEntry(LogKind.ACCEPTED, List.of("741")), new LogEntry(LogKind.SEGMENT_ERROR, List.of("742")));

        assertThat(logCells().stream().map(cell -> cell.getText()).toList())
                .containsExactly("✓ Segment 741 was accepted.", "✕ Segment 742 failed with a recoverable error.");
    }

    // IF a role were drawn in the wrong token, THEN a flagged segment would look like an accepted one.
    @Test
    void log_acceptedAndSegmentError_areDrawnInTheSuccessAndDangerColours() {
        showTranslating();

        publishLog(new LogEntry(LogKind.ACCEPTED, List.of("741")), new LogEntry(LogKind.SEGMENT_ERROR, List.of("742")));

        ThemeTestSupport.assertSameColour(logCells().get(0).getTextFill(), "#5f8a6b", "accepted entry text");
        ThemeTestSupport.assertSameColour(logCells().get(1).getTextFill(), "#b0574c", "segment-error entry text");
    }

    // IF the newest entries were left below the fold, THEN a person watching a long run would see a stale log.
    @Test
    void log_manyEntries_keepsTheNewestInView() {
        showTranslating();
        final LogEntry[] entries = IntStream.range(0, 100)
                .mapToObj(i -> new LogEntry(LogKind.ACCEPTED, List.of("s-" + i)))
                .toArray(LogEntry[]::new);

        publishLog(entries);

        assertThat(logCells().stream().map(cell -> cell.getIndex()).toList()).contains(99);
    }

    // IF an empty log were a blank box, THEN a person could not tell it from a broken one.
    @Test
    void log_noEntries_showsTheEmptyText() {
        showTranslating();

        assertThat(((Label) logList().getPlaceholder()).getText())
                .isEqualTo("Nothing has happened yet. Decisions appear here as the run makes them.");
    }

    // IF the bar were squeezed to nothing when the dashboard is taller than the window, THEN a run would show no
    // progress until a resize gave the bar room back (a progress bar's own minimum height is zero).
    @Test
    void progressBar_sceneShorterThanDashboard_keepsPositiveHeight() {
        showTranslating();
        resizeScene(960, 300);

        publishProgress(42, 0, 58);

        final Node bar = progressBar().lookup(".bar");
        assertThat(progressBar().getProgress()).isCloseTo(0.42, within(0.0001));
        assertThat(progressBar().getHeight()).isCloseTo(9.0, within(0.5));
        assertThat(bar.getLayoutBounds().getWidth()).isGreaterThan(0);
        assertThat(bar.getLayoutBounds().getHeight()).isGreaterThan(0);
    }

    // IF a tile could shrink to its ellipsis, THEN the four counts would read as "…", "a…" and "fl…" in a narrow
    // window; at the window minimum each tile keeps the mockup's 120 pixels and every caption is shown whole.
    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
            accepted  | accepted   | 768
            flagged   | flagged    | 3
            remaining | remaining  | 469
            total     | in total   | 1,240
            """)
    void tiles_atMinimum_showNoTruncatedCaptions(final String name, final String caption, final String number) {
        showTranslating();
        resizeScene(CONTENT_AT_MINIMUM_WIDTH, CONTENT_AT_MINIMUM_HEIGHT);
        publishProgress(768, 3, 469);

        final Region tile = (Region) required("translating-tile-" + name);
        assertThat(tile.minWidth(-1)).isGreaterThanOrEqualTo(120);
        assertThat(tile.getWidth()).isGreaterThanOrEqualTo(120);
        assertThat(textsUnder(tile)).contains(caption, number);
        assertThat(textsUnder(tile)).noneMatch(text -> text.contains("…") || text.contains("..."));
        assertThat(tile.lookupAll(".label"))
                .allSatisfy(label -> assertThat(((Label) label).getWidth())
                        .as(
                                "the label %s of tile %s is wide enough for its whole text",
                                ((Label) label).getText(), name)
                        .isGreaterThanOrEqualTo(((Label) label).prefWidth(-1)));
    }

    // IF the four tiles did not fit the minimum width side by side, THEN the row would need wrapping the shell does
    // not do; 4 x 120 plus 3 x 12 gaps is 516 pixels, which the content area at the minimum must hold.
    @Test
    void tiles_atMinimum_fitTheContentAreaWithoutSidewaysScrolling() {
        showTranslating();
        resizeScene(CONTENT_AT_MINIMUM_WIDTH, CONTENT_AT_MINIMUM_HEIGHT);

        final ScrollPane pane = (ScrollPane) required("shell-content-scroll");
        final double contentWidth = pane.getContent().getLayoutBounds().getWidth();

        assertThat(required("translating-tiles").getLayoutBounds().getWidth()).isLessThanOrEqualTo(contentWidth);
        assertThat(contentWidth).isLessThanOrEqualTo(pane.getViewportBounds().getWidth());
    }

    // IF the Pause and Stop row sat below the fold of the minimum window with no way to reach it, THEN a person
    // could not stop a run; in the content area the minimum leaves it is inside the viewport.
    @Test
    void controls_atMinimum_pauseAndStopAreInsideTheViewport() {
        showTranslating();
        resizeScene(CONTENT_AT_MINIMUM_WIDTH, CONTENT_AT_MINIMUM_HEIGHT);

        publish(RunState.RUNNING);

        final ScrollPane pane = (ScrollPane) required("shell-content-scroll");
        final Bounds viewport = pane.localToScene(pane.getLayoutBounds());
        final Bounds stop = required("translating-stop")
                .localToScene(required("translating-stop").getLayoutBounds());
        assertThat(stop.getMinY()).isGreaterThanOrEqualTo(viewport.getMinY());
        assertThat(stop.getMaxY()).isLessThanOrEqualTo(viewport.getMaxY());
    }

    // IF a screen taller than the window were clipped rather than scrolled, THEN the run controls below the fold of a
    // short window could never be pressed; scrolling to the bottom brings the Stop button into the viewport.
    @Test
    void screen_sceneShorterThanDashboard_scrollsToTheRunControls() {
        showTranslating();
        resizeScene(960, 300);
        publish(RunState.RUNNING);
        final ScrollPane pane = (ScrollPane) required("shell-content-scroll");

        assertThat(pane.getContent().getLayoutBounds().getHeight())
                .isGreaterThan(pane.getViewportBounds().getHeight());
        onFx(() -> pane.setVvalue(pane.getVmax()));

        final Bounds viewport = pane.localToScene(pane.getLayoutBounds());
        final Bounds stop = required("translating-stop")
                .localToScene(required("translating-stop").getLayoutBounds());
        assertThat(stop.getMinY()).isGreaterThanOrEqualTo(viewport.getMinY());
        assertThat(stop.getMaxY()).isLessThanOrEqualTo(viewport.getMaxY());
    }
}
