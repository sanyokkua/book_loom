package ua.bookloom.ui.control;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import javafx.scene.layout.GridPane;
import org.junit.jupiter.api.Test;
import ua.bookloom.ui.FxTestBase;
import ua.bookloom.ui.ThemeTestSupport;

/** The read-only grid: a bordered {@link GridPane} told by its style class, not a new component. */
@SuppressWarnings("NullAway.Init")
class ReadOnlyGridTest extends FxTestBase {

    @Override
    public void start(final javafx.stage.Stage stage) {
        // Built off-screen.
    }

    // IF the factory returned a plain pane, THEN the theme could not give every read-only grid one look.
    @Test
    void of_percentWidths_isAGridPaneWithTheReadOnlyClassAndOneConstraintPerColumn() {
        final GridPane grid = ThemeTestSupport.onFx(() -> ReadOnlyGrid.of("findings", new double[] {1, 3}));

        assertThat(grid.getId()).isEqualTo("findings");
        assertThat(grid.getStyleClass()).contains("read-only-grid");
        assertThat(grid.getColumnConstraints()).hasSize(2);
        assertThat(grid.getColumnConstraints().stream()
                        .map(c -> c.getPercentWidth())
                        .toList())
                .isEqualTo(List.of(25.0, 75.0));
    }
}
