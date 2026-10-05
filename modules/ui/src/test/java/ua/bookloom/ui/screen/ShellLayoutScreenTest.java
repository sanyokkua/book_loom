package ua.bookloom.ui.screen;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.List;
import java.util.concurrent.TimeoutException;
import javafx.scene.Node;
import javafx.scene.control.ScrollPane;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.testfx.util.WaitForAsyncUtils;
import ua.bookloom.api.Result;
import ua.bookloom.api.project.ContextSnapshot;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.SegmentPath;
import ua.bookloom.api.project.SnapshotTerm;
import ua.bookloom.api.project.TermType;
import ua.bookloom.ui.ThemeTestSupport;
import ua.bookloom.ui.ViewNames;
import ua.bookloom.ui.state.LiveRow;
import ua.bookloom.ui.state.LiveRows;
import ua.bookloom.ui.state.LogEntry;
import ua.bookloom.ui.state.LogKind;
import ua.bookloom.ui.state.RunState;

/**
 * The tall screens inside the shell at the smallest content area and at a roomy window: a screen is never squeezed
 * below its own height — the shell scrolls it instead — so the glossary table, the activity log and the review list
 * keep a usable height; and the shell keeps its scroll position while a run refreshes the screen. An opened context
 * section is measured in {@link ContextSectionLayoutTest}.
 * The roomy size is 1000 by 900 because JavaFX's headless screen is 1000 pixels square: a wider window cannot be drawn.
 */
class ShellLayoutScreenTest extends TranslatingScreenTestBase {

    private static final double TABLE_MIN = 400;
    private static final double LOG_MIN = 260;
    private static final double REVIEW_ROWS_MIN = 10 * 34;
    private static final String TEXT = "She had lost her mother, and the poor girl wept as she followed the coffin. ";
    private static final ContextSnapshot CONTEXT = new ContextSnapshot(
            List.of("Коли я приземлився на верхівку ліхтаря.", "Дощ лив стіною."),
            List.of(new SnapshotTerm("Lovelace", "Лавлейс", TermType.CHARACTER, Gender.MALE, true)),
            List.of(),
            "A djinni is summoned to steal an amulet.",
            "");

    // The headless screen is 1000 pixels square and draws nothing outside it, so the window starts at its corner.
    private void sizeTo(final double width, final double height) {
        interact(() -> {
            scene.getWindow().setX(0);
            scene.getWindow().setY(0);
        });
        resizeScene(width, height);
    }

    private ScrollPane shellScroll() {
        return (ScrollPane) required("shell-content-scroll");
    }

    private boolean shellScrolls() {
        final ScrollPane pane = shellScroll();
        return ThemeTestSupport.onFx(() -> pane.getContent().getLayoutBounds().getHeight()
                > pane.getViewportBounds().getHeight());
    }

    private double heightOf(final String id) {
        final Node node = required(id);
        return ThemeTestSupport.onFx(() -> node.getLayoutBounds().getHeight());
    }

    private double offset() {
        final ScrollPane pane = shellScroll();
        return ThemeTestSupport.onFx(() -> pane.getVvalue()
                * (pane.getContent().getLayoutBounds().getHeight()
                        - pane.getViewportBounds().getHeight()));
    }

    private void running() {
        mirror().publishRunStarted("Frankenstein.epub");
        publishLiveRows(true);
        mirror().publishLogEntries(List.of(new LogEntry(LogKind.RETRIED, List.of("format", "ch7 · p42"))));
        WaitForAsyncUtils.waitForFxEvents();
        showTranslating();
    }

    private void publishLiveRows(final boolean withCurrent) {
        mirror().live()
                .publishLiveRows(new LiveRows(
                        new LiveRow("s-1", "ch7 · p41", TEXT, TEXT, 0.93, SegmentPath.DRAFT, false, false),
                        withCurrent
                                ? new LiveRow("s-2", "ch7 · p42", TEXT, null, null, null, true, false, null, CONTEXT)
                                : null));
        WaitForAsyncUtils.waitForFxEvents();
    }

    // IF fitting the window's height squeezed the screen to its minimum, THEN the glossary table would shrink to a few
    // rows and the shell would never scroll; the table keeps its height and the shell scrolls, as the Recurring terms
    // card under the glossary does not fit beside a table of that height even in the tallest window the headless screen
    // has.
    @ParameterizedTest
    @CsvSource({CONTENT_AT_MINIMUM_WIDTH + ", " + CONTENT_AT_MINIMUM_HEIGHT + ", true", "1000, 900, true"})
    void namesStyle_withABook_keepsTheTableTallAndScrollsTheShell(
            final double width, final double height, final boolean scrolls) throws TimeoutException {
        readyToStart();
        glossary.willAnswer(Result.ok(List.of()));
        glossary.willAnswer(Result.ok(List.of()));
        onFx(() -> shell.activate(ViewNames.NAMES_STYLE));

        sizeTo(width, height);

        assertThat(heightOf("names-style-table")).isGreaterThanOrEqualTo(TABLE_MIN);
        assertThat(shellScrolls()).isEqualTo(scrolls);
    }

    // IF the dashboard were squeezed into the window, THEN the activity log would be a strip a few lines high; the
    // running dashboard scrolls the shell and the log keeps its height.
    @ParameterizedTest
    @CsvSource({CONTENT_AT_MINIMUM_WIDTH + ", " + CONTENT_AT_MINIMUM_HEIGHT, "1000, 900"})
    void translating_running_keepsTheLogTallAndScrollsTheShell(final double width, final double height) {
        running();

        sizeTo(width, height);

        assertThat(heightOf("translating-log")).isGreaterThanOrEqualTo(LOG_MIN);
        assertThat(shellScrolls()).isTrue();
    }

    // IF the review list took only what was left of the window, THEN it would show a handful of rows; it shows at
    // least ten at either size.
    @ParameterizedTest
    @CsvSource({CONTENT_AT_MINIMUM_WIDTH + ", " + CONTENT_AT_MINIMUM_HEIGHT, "1000, 900"})
    void review_panelOpen_showsAtLeastTenRows(final double width, final double height) {
        publish(RunState.PAUSED);
        showTranslating();
        onFx(() -> button("translating-review-flagged").fire());

        sizeTo(width, height);

        assertThat(heightOf("review-list")).isGreaterThanOrEqualTo(REVIEW_ROWS_MIN);
    }

    // IF the shell lost its place whenever the screen briefly grew shorter (a live row hidden between two segments),
    // THEN the page would jump while the person reads the review panel; the run's updates leave the place unchanged.
    @Test
    void translating_scrolledDown_runUpdatesLeaveThePlaceUnchanged() {
        running();
        sizeTo(CONTENT_AT_MINIMUM_WIDTH, CONTENT_AT_MINIMUM_HEIGHT);
        onFx(() -> shellScroll().setVvalue(shellScroll().getVmax()));
        final double before = offset();

        publishLiveRows(false);
        publishLiveRows(true);
        publishLog(new LogEntry(LogKind.ACCEPTED, List.of("ch7 · p43")));
        publishProgress(80, 0, 20);
        onFx(() -> {});

        assertThat(before).isPositive();
        assertThat(offset()).isCloseTo(before, within(1.0));
    }
}
