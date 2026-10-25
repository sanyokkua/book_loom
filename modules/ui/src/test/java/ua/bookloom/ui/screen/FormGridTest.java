package ua.bookloom.ui.screen;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ua.bookloom.ui.FxTestBase;
import ua.bookloom.ui.Theme;
import ua.bookloom.ui.ThemeTestSupport;

/** The provider form's labelled rows: labels beside the controls while they fit, above them in a narrow form. */
@SuppressWarnings("NullAway.Init")
class FormGridTest extends FxTestBase {

    private static final double EDGE = 0.51;

    private Stage stage;
    private FormGrid grid;
    private Button wide;
    private TextField field;

    @Override
    public void start(final Stage primary) {
        this.stage = primary;
    }

    // A form whose widest control cannot shrink below 300 px, so two columns need 190 + 20 + 300 = 510 px.
    private void show(final double width) {
        ThemeTestSupport.onFx(() -> {
            grid = new FormGrid();
            wide = new Button("Sign-in");
            wide.setMinWidth(300);
            wide.setPrefWidth(300);
            field = new TextField();
            grid.row("Name", field);
            grid.row("Sign-in", wide);
            grid.setMaxWidth(width);
            final Scene scene = new Scene(new StackPane(grid), 900, 600);
            scene.getStylesheets().add(Theme.stylesheet());
            stage.setScene(scene);
            stage.sizeToScene();
            stage.show();
            return null;
        });
        interact(() -> {});
    }

    private double x(final javafx.scene.Node node) {
        return ThemeTestSupport.onFx(() -> node.getLayoutX());
    }

    private double right(final javafx.scene.Node node) {
        return ThemeTestSupport.onFx(
                () -> node.getLayoutX() + node.getLayoutBounds().getWidth());
    }

    // IF the label column stayed beside a control that no longer fits, THEN the control would be cut at the edge.
    @ParameterizedTest(name = "{0} px -> labels above {1}, control at x {2}")
    @CsvSource({"510, false, 210", "509, true, 0", "400, true, 0"})
    void layout_atEachWidth_putsTheLabelsBesideOrAboveAndNeverPastTheEdge(
            final double width, final boolean stacked, final double controlX) {
        show(width);

        assertThat(ThemeTestSupport.onFx(() -> grid.isStacked())).isEqualTo(stacked);
        assertThat(x(wide)).isCloseTo(controlX, within(EDGE));
        assertThat(right(wide)).isLessThanOrEqualTo(width + EDGE);
    }
}
