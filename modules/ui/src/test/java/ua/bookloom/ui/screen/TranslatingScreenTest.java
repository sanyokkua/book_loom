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
            STOPPING  | -                                    | translating-pause translating-stop
            STOPPED   | translating-resume                   | -
            COMPLETED | -                                    | -
            FAILED    | translating-start                    | -
            """)
    void controls_eachRunState_showTheStatedButtonsAvailableOrNot(
            final RunState state, final String enabled, final String disabled) {
        chooseModel();
        showTranslating();

        publish(state);

        assertThat(enabledControls()).containsExactlyInAnyOrderElementsOf(ids(enabled));
        assertThat(disabledControls()).containsExactlyInAnyOrderElementsOf(ids(disabled));
    }

    // IF the banner said the wrong thing for a state, THEN a pending request would read as one already honoured; the
    // README's run-state table gives a banner to an ended run only, among these.
    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', textBlock = """
            COMPLETED | Translation finished. Nothing needs your review.                           | banner-ok
            FAILED    | The translation stopped because of an error. Finished segments are saved.  | banner-err
            """)
    void banner_endedRun_carriesTheStatedTitleAndRole(
            final RunState state, final String title, final String roleClass) {
        chooseModel();
        showTranslating();
        mirror().publishRunStarted("Frankenstein.epub", null);

        publish(state);

        assertThat(labelText("translating-banner-title")).isEqualTo(title);
        assertThat(required("translating-banner").getStyleClass()).contains("banner", roleClass);
    }

    // IF a run at work or paused by the person carried a banner, THEN the slot would repeat what the title bar says.
    @ParameterizedTest
    @EnumSource(
            value = RunState.class,
            names = {"IDLE", "RUNNING", "PAUSING", "PAUSED", "STOPPING", "STOPPED"})
    void banner_runAtWorkOrPausedByThePerson_isNotShown(final RunState state) {
        chooseModel();
        showTranslating();
        mirror().publishRunStarted("Frankenstein.epub", null);

        publish(state);

        assertThat(optional("translating-banner")).isNull();
    }

    // IF the derived figures were shown wrongly, THEN the dashboard would contradict what the engine reports.
    @Test
    void figures_engineReports768Accepted3Flagged469Pending_showTheCountsAndTheProportion() {
        showTranslating();

        publishProgress(768, 3, 469);

        assertThat(labelText("translating-count-translated")).isEqualTo("768 translated");
        assertThat(labelText("translating-count-to-review")).isEqualTo("3 to review");
        assertThat(labelText("translating-strip-line")).isEqualTo("771 of 1,240 segments done · Chapter 7 of 11");
        assertThat(progressBar().getProgress()).isCloseTo(0.6218, within(0.0001));
    }

    // IF an empty book divided by its total, THEN the dashboard would show NaN instead of zero.
    @Test
    void figures_emptyBook_showZeroProgressWithoutFailing() {
        showTranslating();

        publishProgress(0, 0, 0);

        assertThat(labelText("translating-strip-line")).startsWith("0 of 0 segments done");
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

        assertThat(labelText("translating-count-translated")).isEqualTo("413 translated");
        assertThat(labelText("translating-live-line")).startsWith("Pausing…");
        assertThat(button("translating-pause").isDisabled()).isTrue();
    }

    // IF a pending stop read as stopped, THEN the person would think the model had stopped working when it had not.
    @Test
    void stopping_pendingStop_readsAsStoppingUntilTheRunReturns() {
        showTranslating();
        publish(RunState.RUNNING);

        publish(RunState.STOPPING);

        assertThat(labelText("translating-live-line")).startsWith("Stopping…");
        assertThat(button("translating-stop").isDisabled()).isTrue();
    }

    // IF a stop were dressed as a failure, THEN a choice the person made would be reported as if it had gone wrong.
    @Test
    void stopped_cancelledRunReturns_isANeutralOutcomeWithResumeAndNoStartOrErrorSurface() {
        showTranslating();
        publish(RunState.RUNNING);

        mirror().publishOutcome(RunState.STOPPED, cancelledReport(), null);
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(labelText("translating-live-line"))
                .isEqualTo("Stopped. Continue translation picks up at the first segment not done.");
        assertThat(optional("translating-banner")).isNull();
        assertThat(isShown("translating-resume")).isTrue();
        assertThat(button("translating-resume").getText()).isEqualTo("Continue translation");
        assertThat(isShown("translating-start")).isFalse();
        assertThat(optional("error-card")).isNull();
        assertThat(scene.getRoot().lookupAll(".toast-err")).isEmpty();
    }

    // IF the dashboard hid itself with no book open, THEN the message naming a missing book or model would have nowhere
    // to appear.
    @Test
    void screen_noBookOpen_showsTheDashboardWithItsStartControlAndNoNoBookState() {
        showTranslating();

        assertThat(isShown("translating-start")).isTrue();
        assertThat(optional("nobook-card")).isNull();
    }

    // IF a time left were shown before the engine measured one, THEN the strip would invent a finishing time.
    @Test
    void bigLine_runInProgressNoFiguresYet_saysNothingAboutTimeLeft() {
        showTranslating();
        publish(RunState.RUNNING);
        publishProgress(768, 3, 469);

        assertThat(optional("translating-strip-big") == null
                        || !labelText("translating-strip-big").contains("left"))
                .isTrue();
    }

    // IF a run could be started from an earlier step, THEN the specification's single start control would be two.
    @ParameterizedTest
    @EnumSource(
            value = ViewNames.class,
            names = {"IMPORT", "BOOK_BRIEF", "SETTINGS"})
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
        publish(RunState.RUNNING);

        publishLog(new LogEntry(LogKind.ACCEPTED, List.of("741")), new LogEntry(LogKind.SEGMENT_ERROR, List.of("742")));
        openActivityLog();

        assertThat(logCells().stream().map(cell -> cell.getAccessibleText()).toList())
                .containsExactly(
                        "ok    ✓ Segment 741 was accepted.", "err   ✕ Segment 742 failed with a recoverable error.");
    }

    // IF a role were drawn in the wrong token, THEN a flagged segment would look like an accepted one; IF the words
    // took the role's colour too, THEN a pale green line would be hard to read on the light surface.
    @Test
    void log_acceptedAndSegmentError_drawTheirTagInTheRoleAndTheirWordsInTheTextColour() {
        showTranslating();
        publish(RunState.RUNNING);

        publishLog(new LogEntry(LogKind.ACCEPTED, List.of("741")), new LogEntry(LogKind.SEGMENT_ERROR, List.of("742")));
        openActivityLog();

        ThemeTestSupport.assertSameColour(partOf(0, ".log-tag").getTextFill(), "#4a6c54", "accepted entry tag");
        ThemeTestSupport.assertSameColour(partOf(1, ".log-tag").getTextFill(), "#a04f45", "segment-error entry tag");
        ThemeTestSupport.assertSameColour(partOf(0, ".log-text").getTextFill(), "#2c3941", "accepted entry words");
    }

    private Label partOf(final int row, final String selector) {
        return (Label) logCells().get(row).lookup(selector);
    }

    // IF the newest entries were left below the fold, THEN a person watching a long run would see a stale log.
    @Test
    void log_manyEntries_keepsTheNewestInView() {
        showTranslating();
        publish(RunState.RUNNING);
        openActivityLog();
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
        publish(RunState.RUNNING);
        openActivityLog();

        assertThat(((Label) logList().getPlaceholder()).getText())
                .isEqualTo("Nothing has happened yet. Decisions appear here as the run makes them.");
    }

    // IF the bar were squeezed to nothing when the dashboard is taller than the window, THEN a run would show no
    // progress until a resize gave the bar room back (a progress bar's own minimum height is zero).
    @Test
    void progressBar_sceneShorterThanDashboard_keepsPositiveHeight() {
        showTranslating();
        resizeScene(960, 300);
        publish(RunState.RUNNING);

        publishProgress(42, 0, 58);

        final Node bar = progressBar().lookup(".bar");
        assertThat(progressBar().getProgress()).isCloseTo(0.42, within(0.0001));
        assertThat(progressBar().getHeight()).isGreaterThan(0);
        assertThat(bar.getLayoutBounds().getWidth()).isGreaterThan(0);
        assertThat(bar.getLayoutBounds().getHeight()).isGreaterThan(0);
    }

    // IF Pause and Stop scrolled with the screen, THEN during a long stall a person would have to scroll past the live
    // panel and the log to stop the run; they sit in the toolbar, above the scrolling content (README L2).
    @Test
    void controls_atMinimum_pauseAndStopAreInTheToolbar() {
        showTranslating();
        resizeScene(CONTENT_AT_MINIMUM_WIDTH, CONTENT_AT_MINIMUM_HEIGHT);
        publish(RunState.RUNNING);

        assertThat(required("translating-pause").getParent()).isSameAs(required("shell-actions"));
        assertThat(required("translating-stop").getParent()).isSameAs(required("shell-actions"));
    }

    // IF a screen taller than the window were clipped rather than scrolled, THEN the parts below the fold of a short
    // window could never be reached; the Stop button stays in view in the toolbar wherever the content is scrolled.
    @Test
    void screen_sceneShorterThanDashboard_scrollsAndKeepsStopInView() {
        showTranslating();
        resizeScene(960, 300);
        publish(RunState.RUNNING);
        final ScrollPane pane = (ScrollPane) required("shell-content-scroll");

        assertThat(pane.getContent().getLayoutBounds().getHeight())
                .isGreaterThan(pane.getViewportBounds().getHeight());
        onFx(() -> pane.setVvalue(1));

        final Bounds stop = required("translating-stop")
                .localToScene(required("translating-stop").getLayoutBounds());
        assertThat(stop.getMinY()).isGreaterThanOrEqualTo(0);
        assertThat(stop.getMaxY())
                .isLessThanOrEqualTo(pane.localToScene(pane.getLayoutBounds()).getMinY());
    }
}
