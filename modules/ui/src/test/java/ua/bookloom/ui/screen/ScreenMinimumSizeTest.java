package ua.bookloom.ui.screen;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeoutException;
import javafx.scene.control.ScrollPane;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.testfx.util.WaitForAsyncUtils;
import ua.bookloom.api.Result;
import ua.bookloom.api.project.SegmentPath;
import ua.bookloom.ui.BookFixtures;
import ua.bookloom.ui.ViewNames;
import ua.bookloom.ui.state.LiveRow;
import ua.bookloom.ui.state.LiveRows;
import ua.bookloom.ui.state.LogEntry;
import ua.bookloom.ui.state.LogKind;
import ua.bookloom.ui.state.StateMirror;

/**
 * Every screen with a working view, shown in the content area the 960 by 640 window minimum leaves with no book and with one open: none needs
 * sideways scrolling, and the content area is a scroll pane, so anything taller than it can still be reached.
 */
class ScreenMinimumSizeTest extends ImportScreenTestBase {

    private static final Path BOOK = Path.of("Frankenstein.epub");
    private static final String LONG_TEXT =
            "She had lost her mother, and the poor girl wept as she followed the coffin. ".repeat(6);

    private void assertFitsTheWidth(final ViewNames view) {
        onFx(() -> shell.activate(view));

        resizeScene(CONTENT_AT_MINIMUM_WIDTH, CONTENT_AT_MINIMUM_HEIGHT);

        final ScrollPane pane = (ScrollPane) required("shell-content-scroll");
        assertThat(pane.getContent().getLayoutBounds().getWidth())
                .as("%s at %s px wide", view, CONTENT_AT_MINIMUM_WIDTH)
                .isLessThanOrEqualTo(pane.getViewportBounds().getWidth());
    }

    // IF a screen's own minimum were wider than the content area at the window minimum, THEN the person would have to
    // scroll sideways to read it.
    @ParameterizedTest
    @EnumSource(
            value = ViewNames.class,
            names = {"IMPORT", "BOOK_BRIEF", "STRUCTURE", "NAMES_STYLE", "TRANSLATING", "EXPORT", "SETTINGS"})
    void screen_noBookOpen_atMinimumWidth_needsNoSidewaysScrolling(final ViewNames view) {
        assertFitsTheWidth(view);
    }

    // The same with the fullest cards a book gives the screens: the import card, the brief and the structure tree.
    @ParameterizedTest
    @EnumSource(
            value = ViewNames.class,
            names = {"IMPORT", "BOOK_BRIEF", "STRUCTURE", "NAMES_STYLE", "TRANSLATING", "EXPORT"})
    void screen_bookOpen_atMinimumWidth_needsNoSidewaysScrolling(final ViewNames view) throws TimeoutException {
        projects.on(BOOK, Result.ok(BookFixtures.frankensteinImport()));
        openImport();
        openBook(BOOK);

        assertFitsTheWidth(view);
    }

    // A run in progress is the fullest the dashboard gets: the live panel and the log side by side, with text in both.
    @Test
    void translating_runningWithLiveRowsAndLog_atMinimumWidth_needsNoSidewaysScrolling() {
        final StateMirror mirror = injector.getInstance(StateMirror.class);
        mirror.publishRunStarted("Frankenstein.epub");
        mirror.live()
                .publishLiveRows(new LiveRows(
                        new LiveRow("s-1", "ch7 · p41", LONG_TEXT, LONG_TEXT, 0.93, SegmentPath.DRAFT, false, false),
                        new LiveRow("s-2", "ch7 · p42", LONG_TEXT, null, null, null, true, false)));
        mirror.publishLogEntries(List.of(new LogEntry(LogKind.RETRIED, List.of("format", "ch7 · p42"))));
        WaitForAsyncUtils.waitForFxEvents();

        assertFitsTheWidth(ViewNames.TRANSLATING);
    }

    // The review panel open beside a paused run, with no segment listed, still fits the content area.
    @Test
    void translating_reviewPanelOpen_atMinimumWidth_needsNoSidewaysScrolling() {
        final StateMirror mirror = injector.getInstance(StateMirror.class);
        mirror.publishRunState(ua.bookloom.ui.state.RunState.PAUSED);
        WaitForAsyncUtils.waitForFxEvents();
        onFx(() -> shell.activate(ViewNames.TRANSLATING));
        onFx(() -> ((javafx.scene.control.Button) required("translating-review-flagged")).fire());
        WaitForAsyncUtils.waitForFxEvents();

        assertFitsTheWidth(ViewNames.TRANSLATING);
    }
}
