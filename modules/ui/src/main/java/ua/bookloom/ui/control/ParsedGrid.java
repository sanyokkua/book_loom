package ua.bookloom.ui.control;

import java.util.List;
import java.util.Objects;
import javafx.geometry.VPos;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Region;
import org.jspecify.annotations.Nullable;

/**
 * A small read-only table of wrapped labels with a header row, for a prompt part or a reply that has columns. A grid
 * rather than a table control so every row is as tall as its wrapped text and the body around it scrolls as one.
 *
 * <p>Cells are named {@code <id>-r<row>c<column>}, the header being row 0, so a test or a screen reader can address one.
 */
final class ParsedGrid extends GridPane {

    private static final double GAP = 8;

    private final int columns;
    private int rows;

    ParsedGrid(final String id, final List<String> heads, final double[] widths) {
        Objects.requireNonNull(heads, "heads");
        Objects.requireNonNull(widths, "widths");
        setId(Objects.requireNonNull(id, "id"));
        getStyleClass().add("parsed-grid");
        setHgap(GAP);
        setVgap(GAP / 2);
        columns = heads.size();
        ReadOnlyGrid.style(this, widths);
        for (int index = 0; index < columns; index++) {
            put(heads.get(index), "parsed-head", index);
        }
    }

    /**
     * Adds one row.
     *
     * @param cells one text per column
     * @param lastClass an extra style class for the last cell, or null for none
     */
    void addRow(final List<String> cells, final @Nullable String lastClass) {
        Objects.requireNonNull(cells, "cells");
        rows++;
        for (int index = 0; index < columns; index++) {
            final Label cell = put(cells.get(index), "parsed-cell", index);
            if (lastClass != null && index == columns - 1) {
                cell.getStyleClass().add(lastClass);
            }
        }
    }

    private Label put(final String text, final String role, final int column) {
        final Label cell = new Label(text);
        cell.getStyleClass().add(role);
        cell.setWrapText(true);
        cell.setMinHeight(Region.USE_PREF_SIZE);
        cell.setMaxWidth(Double.MAX_VALUE);
        cell.setId(getId() + "-r" + rows + "c" + column);
        GridPane.setValignment(cell, VPos.TOP);
        add(cell, column, rows);
        return cell;
    }
}
