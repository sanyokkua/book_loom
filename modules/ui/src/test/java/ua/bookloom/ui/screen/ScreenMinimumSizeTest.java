package ua.bookloom.ui.screen;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.concurrent.TimeoutException;
import javafx.scene.control.ScrollPane;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import ua.bookloom.api.Result;
import ua.bookloom.ui.BookFixtures;
import ua.bookloom.ui.ViewNames;

/**
 * Every screen with a working view, shown in the content area the 960 by 640 window minimum leaves with no book and with one open: none needs
 * sideways scrolling, and the content area is a scroll pane, so anything taller than it can still be reached.
 */
class ScreenMinimumSizeTest extends ImportScreenTestBase {

    private static final Path BOOK = Path.of("Frankenstein.epub");

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
            names = {"IMPORT", "BOOK_BRIEF", "STRUCTURE", "TRANSLATING", "EXPORT", "SETTINGS"})
    void screen_noBookOpen_atMinimumWidth_needsNoSidewaysScrolling(final ViewNames view) {
        assertFitsTheWidth(view);
    }

    // The same with the fullest cards a book gives the screens: the import card, the brief and the structure tree.
    @ParameterizedTest
    @EnumSource(
            value = ViewNames.class,
            names = {"IMPORT", "BOOK_BRIEF", "STRUCTURE", "TRANSLATING", "EXPORT"})
    void screen_bookOpen_atMinimumWidth_needsNoSidewaysScrolling(final ViewNames view) throws TimeoutException {
        port.on(BOOK, Result.ok(BookFixtures.frankenstein()));
        openImport();
        openBook(BOOK);

        assertFitsTheWidth(view);
    }
}
