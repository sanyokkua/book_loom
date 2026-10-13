package ua.bookloom.ui.screen;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.concurrent.TimeoutException;
import javafx.geometry.Bounds;
import javafx.scene.control.ScrollPane;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ua.bookloom.ui.ThemeTestSupport;
import ua.bookloom.ui.control.TwoColumns;

/**
 * How the import screen of an open book arranges its cards: the details beside the facts, and the structure beside the
 * segments, each pair side by side on a wide window and stacked, in the same order, on a narrow one.
 */
class ImportLayoutTest extends ImportBookTestBase {

    private static final String DETAILS_AND_FACTS = "import-columns";
    private static final String STRUCTURE_AND_SEGMENTS = "import-structure-columns";
    private static final double SAME_ROW = 1.0;

    // IF the pairs were plain boxes, THEN the screen could not give way to a narrow window or use a wide one.
    @Test
    void columns_bookOpened_areTwoColumnsControlsWithTheSurroundingCardsInOrder() throws TimeoutException {
        openFactsBook();

        assertThat(required(DETAILS_AND_FACTS)).isInstanceOf(TwoColumns.class);
        assertThat(required(STRUCTURE_AND_SEGMENTS)).isInstanceOf(TwoColumns.class);
        assertThat(isInside("import-card", DETAILS_AND_FACTS)).isTrue();
        assertThat(isInside("import-facts-card", DETAILS_AND_FACTS)).isTrue();
        assertThat(isInside("structure-card", STRUCTURE_AND_SEGMENTS)).isTrue();
        assertThat(isInside("structure-segments-card", STRUCTURE_AND_SEGMENTS)).isTrue();
    }

    // IF the details and the facts sat side by side on a window too narrow for both, THEN each would be squeezed
    // until its rows were cut.
    @ParameterizedTest(name = "{0} x {1}")
    @CsvSource({"944, 600", "960, 640"})
    void detailsAndFacts_narrowWindow_stackWithTheDetailsAbove(final double width, final double height)
            throws TimeoutException {
        openFactsBook();

        showAtSceneSize(width, height);

        final Bounds details = boundsOf("import-card");
        final Bounds facts = boundsOf("import-facts-card");
        assertThat(isStacked(DETAILS_AND_FACTS)).isTrue();
        assertThat(facts.getMinY()).isGreaterThanOrEqualTo(details.getMaxY());
        assertThat(facts.getMinX()).isCloseTo(details.getMinX(), within(SAME_ROW));
    }

    // IF a wide window kept the cards stacked, THEN the screen would waste its width and push the structure down.
    @Test
    void detailsAndFacts_wideWindow_sitSideBySideOnOneRow() throws TimeoutException {
        openFactsBook();

        showAtSceneSize(1440, 900);

        final Bounds details = boundsOf("import-card");
        final Bounds facts = boundsOf("import-facts-card");
        assertThat(isStacked(DETAILS_AND_FACTS)).isFalse();
        assertThat(facts.getMinX()).isGreaterThanOrEqualTo(details.getMaxX());
        assertThat(facts.getMinY()).isCloseTo(details.getMinY(), within(SAME_ROW));
    }

    // IF the structure and the segments stayed side by side on a narrow window, THEN the segment rows would be cut.
    @Test
    void structureAndSegments_narrowWindow_stackWithTheTreeAbove() throws TimeoutException {
        openFactsBook();

        showAtSceneSize(960, 640);

        assertThat(isStacked(STRUCTURE_AND_SEGMENTS)).isTrue();
        assertThat(boundsOf("structure-segments-card").getMinY())
                .isGreaterThanOrEqualTo(boundsOf("structure-card").getMaxY());
    }

    // IF the tree and the segments did not share a row on a wide window, THEN reading a chapter's segments would mean
    // scrolling away from the tree that picks them.
    @Test
    void structureAndSegments_wideWindow_sitSideBySideTreeFirst() throws TimeoutException {
        openFactsBook();

        showAtSceneSize(1440, 900);

        final Bounds tree = boundsOf("structure-card");
        final Bounds segments = boundsOf("structure-segments-card");
        assertThat(isStacked(STRUCTURE_AND_SEGMENTS)).isFalse();
        assertThat(segments.getMinX()).isGreaterThanOrEqualTo(tree.getMaxX());
        assertThat(segments.getMinY()).isCloseTo(tree.getMinY(), within(SAME_ROW));
    }

    // IF the screen's rows came in another order, THEN the dropzone, the book's identity and the structure would not
    // read
    // top to bottom; the way on is in the window's toolbar, which sits outside the scrolling content.
    @Test
    void screen_bookOpened_readsDropzoneThenDetailsRowThenStructureRowAndFootersActionsAreInTheToolbar()
            throws TimeoutException {
        openFactsBook();

        assertThat(boundsOf("import-dropzone").getMaxY())
                .isLessThanOrEqualTo(boundsOf(DETAILS_AND_FACTS).getMinY());
        assertThat(boundsOf(DETAILS_AND_FACTS).getMaxY())
                .isLessThanOrEqualTo(boundsOf(STRUCTURE_AND_SEGMENTS).getMinY());
        assertThat(isInside("import-continue", "shell-actions")).isTrue();
        assertThat(isInside("import-cancel", "shell-actions")).isTrue();
    }

    // IF a wide card pushed the content past the viewport, THEN a horizontal scroll bar would hide the right column.
    @ParameterizedTest(name = "{0} x {1}")
    @CsvSource({"944, 600", "1440, 900"})
    void screen_bookOpened_neverOverflowsTheViewportWidth(final double width, final double height)
            throws TimeoutException {
        openFactsBook();

        showAtSceneSize(width, height);

        final ScrollPane scroll = (ScrollPane) required("import-screen");
        final double content = ThemeTestSupport.onFx(
                () -> scroll.getContent().getLayoutBounds().getWidth());
        final double viewport =
                ThemeTestSupport.onFx(() -> scroll.getViewportBounds().getWidth());
        assertThat(content).isLessThanOrEqualTo(viewport);
    }
}
