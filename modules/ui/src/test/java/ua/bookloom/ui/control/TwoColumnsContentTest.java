package ua.bookloom.ui.control;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ua.bookloom.ui.FxTestBase;
import ua.bookloom.ui.Theme;
import ua.bookloom.ui.ThemeTestSupport;

/**
 * The two-column layout decided by its content as well as its breakpoint: two equal columns need twice the wider
 * column's minimum and the gap, a side column keeps its preferred width, and going back side by side waits 16 px.
 */
@SuppressWarnings("NullAway.Init")
class TwoColumnsContentTest extends FxTestBase {

    private static final double EDGE = 0.51;
    private static final double BREAKPOINT = 700;
    private static final double UNSHRINKABLE = 400;
    private static final double SIDE = 320;

    private Stage stage;
    private TwoColumns columns;
    private Node right;

    @Override
    public void start(final Stage primary) {
        this.stage = primary;
    }

    private static VBox column(final String id, final double minWidth) {
        final Button button = new Button("Caption " + id);
        button.setMinWidth(minWidth);
        button.setPrefWidth(Math.max(minWidth, 1));
        final VBox box = new VBox(button);
        box.setId(id);
        return box;
    }

    private void show(final boolean sideColumn) {
        ThemeTestSupport.onFx(() -> {
            if (sideColumn) {
                final VBox side = column("side", 0);
                side.setPrefWidth(SIDE);
                right = side;
                columns = TwoColumns.withSideColumn(column("main", UNSHRINKABLE), side);
            } else {
                right = column("right", UNSHRINKABLE);
                columns = new TwoColumns(column("left", 0), right, BREAKPOINT);
            }
            final ScrollPane host = new ScrollPane(new VBox(columns));
            host.setFitToWidth(true);
            final Scene scene = new Scene(host, 1440, 900);
            scene.getStylesheets().add(Theme.stylesheet());
            stage.setScene(scene);
            stage.sizeToScene();
            stage.show();
            return null;
        });
        interact(() -> {});
    }

    private void limitWidth(final double width) {
        interact(() -> columns.setMaxWidth(width));
        interact(() -> {});
    }

    private double rightEdge(final Node node) {
        return ThemeTestSupport.onFx(
                () -> node.getLayoutX() + node.getLayoutBounds().getMaxX());
    }

    private double width(final Node node) {
        return ThemeTestSupport.onFx(() -> node.getLayoutBounds().getWidth());
    }

    // IF the columns stayed side by side above the breakpoint while a control could not fit its half, THEN the control
    // would be cut at the right edge (the owner's narrow Book Brief).
    @ParameterizedTest(name = "{0} px")
    @CsvSource({"815", "760", "701"})
    void layout_aboveTheBreakpointButNarrowerThanTwoMinimumColumns_stacks(final double contentWidth) {
        show(false);
        limitWidth(contentWidth);

        assertThat(columns.isStacked()).isTrue();
        assertThat(rightEdge(right)).isLessThanOrEqualTo(contentWidth + EDGE);
    }

    // IF two minimum columns and the gap fit, THEN the columns sit side by side.
    @Test
    void layout_wideEnoughForTwoMinimumColumns_staysSideBySide() {
        show(false);
        limitWidth(2 * UNSHRINKABLE + 16);

        assertThat(columns.isStacked()).isFalse();
        assertThat(width(right)).isCloseTo(UNSHRINKABLE, within(EDGE));
    }

    // IF the columns went back side by side the moment they fit, THEN a minimum that changes with the column or a
    // scrollbar coming and going would flip the layout to and fro; they wait 16 px past the boundary.
    @ParameterizedTest(name = "back to {0} px -> stacked {1}")
    @CsvSource({"820, true", "831, true", "832, false"})
    void layout_widenedAfterStacking_goesBackSideBySideOnly16PxPastTheBoundary(
            final double contentWidth, final boolean stacked) {
        show(false);
        limitWidth(780);
        limitWidth(contentWidth);

        assertThat(columns.isStacked()).isEqualTo(stacked);
    }

    // IF the side column were squeezed to half the width, THEN the export's side files would crush the main cards.
    @ParameterizedTest(name = "{0} px -> stacked {1}, side {2} px")
    @CsvSource({"900, false, 320", "736, false, 320", "735, true, 735"})
    void sideColumn_keepsItsPreferredWidthUntilTheMainMinimumNoLongerFitsBesideIt(
            final double contentWidth, final boolean stacked, final double sideWidth) {
        show(true);
        limitWidth(contentWidth);

        assertThat(columns.isStacked()).isEqualTo(stacked);
        assertThat(width(right)).isCloseTo(sideWidth, within(EDGE));
    }
}
