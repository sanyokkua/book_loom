package ua.bookloom.ui.screen;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.List;
import java.util.concurrent.TimeoutException;
import javafx.geometry.Bounds;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SplitPane;
import javafx.scene.layout.Region;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ua.bookloom.ui.ThemeTestSupport;

/**
 * How the import page of an open book is arranged: dropzone, verdict line, closed fold, then the one split of chapters,
 * segments and text, with the text under the segments in the compact layout and the page fitting a large window.
 */
class ImportBrowserLayoutTest extends ImportBookTestBase {

    private static final String BROWSER = "import-browser";
    private static final double SAME_ROW = 1.0;
    private static final double BROWSER_MIN_HEIGHT = 360;

    private SplitPane browser() {
        return (SplitPane) required(BROWSER);
    }

    // IF the three panes were separate boxes, THEN the person could not resize one against another.
    @Test
    void browser_regularWidth_isOneSplitOfChaptersSegmentsAndTextWithTwoDividers() throws TimeoutException {
        openFactsBook();

        showAtSceneSize(1440, 900);

        final List<String> ids = ThemeTestSupport.onFx(
                () -> browser().getItems().stream().map(item -> item.getId()).toList());
        assertThat(ids).containsExactly("structure-card", "structure-segments-card", "structure-text-card");
        assertThat(ThemeTestSupport.onFx(() -> browser().getDividers().size())).isEqualTo(2);
        assertThat(boundsOf("structure-segments-card").getMinX())
                .isGreaterThanOrEqualTo(boundsOf("structure-card").getMaxX());
        assertThat(boundsOf("structure-text-card").getMinX())
                .isGreaterThanOrEqualTo(boundsOf("structure-segments-card").getMaxX());
        assertThat(boundsOf("structure-text-card").getMinY())
                .isCloseTo(boundsOf("structure-card").getMinY(), within(SAME_ROW));
    }

    // IF a pane could shrink without limit, THEN dragging a divider would leave an unreadable sliver.
    @ParameterizedTest
    @CsvSource({"structure-card, 160", "structure-segments-card, 240", "structure-text-card, 220"})
    void panes_everyone_haveTheirMinimumWidth(final String id, final double minimum) throws TimeoutException {
        openFactsBook();

        assertThat(ThemeTestSupport.onFx(() -> ((Region) required(id)).getMinWidth()))
                .isEqualTo(minimum);
    }

    // IF the text stayed beside the segments on a narrow window, THEN both would be squeezed below their minimums.
    @Test
    void text_compactWidth_sitsBelowTheSegments() throws TimeoutException {
        openFactsBook();

        showAtSceneSize(960, 640);

        assertThat(boundsOf("structure-text-card").getMinY())
                .isGreaterThanOrEqualTo(boundsOf("structure-segments-card").getMaxY());
        assertThat(ThemeTestSupport.onFx(() -> required("structure-text-card").isManaged()))
                .isTrue();
    }

    // IF the browser were given what is left of the window, THEN a short window would leave it a few rows tall.
    @ParameterizedTest(name = "{0} x {1}")
    @CsvSource({"960, 640", "1440, 900"})
    void browser_anySize_isAtLeast360PixelsHigh(final double width, final double height) throws TimeoutException {
        openFactsBook();

        showAtSceneSize(width, height);

        assertThat(boundsOf(BROWSER).getHeight()).isGreaterThanOrEqualTo(BROWSER_MIN_HEIGHT);
    }

    // IF the page needed a scroll bar on a large window, THEN "one screen" would not hold. The full-HD size cannot run
    // here: JavaFX's headless screen is a 1000 by 1000 pixel buffer, and a window past it (1920 by 1080 or 1000) takes
    // the toolkit down, so the largest size that fits is used; the page only gains room above it.
    @Test
    void page_largeWindow_needsNoVerticalScroll() throws TimeoutException {
        openFactsBook();

        showAtSceneSize(1440, 900);

        final ScrollPane scroll = (ScrollPane) required("import-screen");
        assertThat(ThemeTestSupport.onFx(
                        () -> scroll.getContent().getLayoutBounds().getHeight()))
                .isLessThanOrEqualTo(
                        ThemeTestSupport.onFx(() -> scroll.getViewportBounds().getHeight()));
    }

    // IF the rows came in another order, THEN the verdict would not read as the answer to the dropped file.
    @Test
    void page_bookOpened_readsDropzoneVerdictFoldThenBrowser() throws TimeoutException {
        openFactsBook();

        showAtSceneSize(1440, 900);

        final Bounds dropzone = boundsOf("import-dropzone");
        final Bounds verdict = boundsOf("import-verdict");
        final Bounds fold = boundsOf("import-about");
        assertThat(dropzone.getMaxY()).isLessThanOrEqualTo(verdict.getMinY());
        assertThat(verdict.getMaxY()).isLessThanOrEqualTo(fold.getMinY());
        assertThat(fold.getMaxY()).isLessThanOrEqualTo(boundsOf(BROWSER).getMinY());
        assertThat(isInside("import-continue", "shell-actions")).isTrue();
    }

    // IF a wide pane pushed the content past the viewport, THEN a horizontal scroll bar would hide the right column.
    @ParameterizedTest(name = "{0} x {1}")
    @CsvSource({"944, 600", "1440, 900"})
    void page_bookOpened_neverOverflowsTheViewportWidth(final double width, final double height)
            throws TimeoutException {
        openFactsBook();

        showAtSceneSize(width, height);

        final ScrollPane scroll = (ScrollPane) required("import-screen");
        assertThat(ThemeTestSupport.onFx(
                        () -> scroll.getContent().getLayoutBounds().getWidth()))
                .isLessThanOrEqualTo(
                        ThemeTestSupport.onFx(() -> scroll.getViewportBounds().getWidth()));
    }
}
