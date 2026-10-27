package ua.bookloom.ui.control;

import java.util.Objects;
import javafx.geometry.HPos;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * The shared shape of a read-only grid: a {@link GridPane} with the {@code read-only-grid} style class, which the theme
 * borders, and columns sized in proportion. It is a grid rather than a table because it is never resized or edited.
 */
// Checkstyle's HideUtilityClassConstructor parses source text before Lombok's annotation processor runs, so it
// cannot see the private constructor @NoArgsConstructor generates (ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ReadOnlyGrid {

    private static final double PERCENT = 100;

    /**
     * Builds an empty read-only grid.
     *
     * @param id the node id
     * @param widths the relative width of each column; each column gets its share of 100 percent
     * @return a new grid with one column constraint per width
     */
    public static GridPane of(final String id, final double[] widths) {
        Objects.requireNonNull(id, "id");
        final GridPane grid = new GridPane();
        grid.setId(id);
        style(grid, widths);
        return grid;
    }

    /**
     * Gives a grid the read-only look and its proportional columns; for a grid that is a subclass of its own.
     *
     * @param grid the grid to dress
     * @param widths the relative width of each column
     */
    public static void style(final GridPane grid, final double[] widths) {
        Objects.requireNonNull(grid, "grid");
        Objects.requireNonNull(widths, "widths");
        grid.getStyleClass().add("read-only-grid");
        double total = 0;
        for (final double width : widths) {
            total += width;
        }
        for (final double width : widths) {
            final ColumnConstraints column = new ColumnConstraints();
            column.setPercentWidth(width * PERCENT / total);
            column.setHalignment(HPos.LEFT);
            grid.getColumnConstraints().add(column);
        }
    }
}
