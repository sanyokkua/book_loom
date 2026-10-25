package ua.bookloom.ui.screen;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.Region;

/**
 * Labelled rows in two aligned columns, where a row can be hidden as a whole without leaving a gap.
 *
 * <p>While the form is too narrow for the label column beside its widest control's minimum, every label sits above
 * its control instead, so no control is cut at the right edge of a narrow window, whatever its words in the display
 * language. The form goes back to two columns only 16 pixels past that width, as
 * {@link ua.bookloom.ui.control.TwoColumns} does. Nothing is logged in layout: it runs on every pass.
 */
final class FormGrid extends Region {

    private static final double LABEL_WIDTH = 190;
    private static final double GAP = 10;
    private static final double COLUMN_GAP = GAP + GAP;
    private static final double LABEL_ABOVE_GAP = 4;
    private static final double HYSTERESIS = 16;

    /** A row: its label and its control, shown or hidden together. */
    record Row(Label label, Node control) {

        void show(final boolean shown) {
            for (final Node node : new Node[] {label, control}) {
                node.setVisible(shown);
                node.setManaged(shown);
            }
        }
    }

    private final List<Row> rows = new ArrayList<>();
    private boolean stacked;

    /** Adds a row under the last one. */
    Row row(final String label, final Node control) {
        Objects.requireNonNull(label, "label");
        Objects.requireNonNull(control, "control");
        final Label text = new Label(label);
        text.getStyleClass().add("muted");
        text.setWrapText(true);
        getChildren().addAll(text, control);
        final Row row = new Row(text, control);
        rows.add(row);
        return row;
    }

    /**
     * Whether the last layout put each label above its control.
     *
     * @return {@code true} if the labels sat above the controls, {@code false} if they sat beside them
     */
    boolean isStacked() {
        return stacked;
    }

    @Override
    public Orientation getContentBias() {
        return Orientation.HORIZONTAL;
    }

    @Override
    protected double computeMinWidth(final double height) {
        return horizontalInsets() + widestControlMinimum();
    }

    @Override
    protected double computePrefWidth(final double height) {
        final double widest = shown().stream()
                .mapToDouble(row -> row.control().prefWidth(-1))
                .max()
                .orElse(0);
        return horizontalInsets() + LABEL_WIDTH + COLUMN_GAP + widest;
    }

    @Override
    protected double computePrefHeight(final double width) {
        final double inner = (width < 0 ? computePrefWidth(-1) : width) - horizontalInsets();
        final boolean stack = isStackedAt(inner);
        double height = 0;
        for (final Row row : shown()) {
            height += rowHeight(row, inner, stack) + GAP;
        }
        final Insets insets = getInsets();
        return insets.getTop() + Math.max(0, height - GAP) + insets.getBottom();
    }

    @Override
    protected void layoutChildren() {
        final Insets insets = getInsets();
        final double inner = getWidth() - horizontalInsets();
        stacked = isStackedAt(inner);
        double y = insets.getTop();
        for (final Row row : shown()) {
            y += placeRow(row, insets.getLeft(), y, inner) + GAP;
        }
    }

    // Lays one row out at the top edge y and answers its height.
    private double placeRow(final Row row, final double x, final double y, final double inner) {
        final Label label = row.label();
        final Node control = row.control();
        if (stacked) {
            final double labelHeight = label.prefHeight(inner);
            label.resizeRelocate(x, y, inner, labelHeight);
            final double controlWidth = controlWidth(control, inner);
            final double controlY = y + labelHeight + LABEL_ABOVE_GAP;
            control.resizeRelocate(x, controlY, controlWidth, control.prefHeight(controlWidth));
            return labelHeight + LABEL_ABOVE_GAP + control.prefHeight(controlWidth);
        }
        final double column = inner - LABEL_WIDTH - COLUMN_GAP;
        final double controlWidth = controlWidth(control, column);
        label.resizeRelocate(x, y, LABEL_WIDTH, label.prefHeight(LABEL_WIDTH));
        control.resizeRelocate(x + LABEL_WIDTH + COLUMN_GAP, y, controlWidth, control.prefHeight(controlWidth));
        return rowHeight(row, inner, false);
    }

    private double rowHeight(final Row row, final double inner, final boolean stack) {
        final Label label = row.label();
        final Node control = row.control();
        if (stack) {
            return label.prefHeight(inner) + LABEL_ABOVE_GAP + control.prefHeight(controlWidth(control, inner));
        }
        final double controlWidth = controlWidth(control, inner - LABEL_WIDTH - COLUMN_GAP);
        return Math.max(label.prefHeight(LABEL_WIDTH), control.prefHeight(controlWidth));
    }

    // A control fills its column up to its own maximum, and never goes below its minimum.
    private static double controlWidth(final Node control, final double column) {
        return Math.max(control.minWidth(-1), Math.min(column, control.maxWidth(-1)));
    }

    // Read by computePrefHeight and layoutChildren alike, without changing the held state.
    private boolean isStackedAt(final double inner) {
        final double sideBySide = LABEL_WIDTH + COLUMN_GAP + widestControlMinimum();
        return inner < (stacked ? sideBySide + HYSTERESIS : sideBySide);
    }

    private double widestControlMinimum() {
        return shown().stream()
                .mapToDouble(row -> row.control().minWidth(-1))
                .max()
                .orElse(0);
    }

    private List<Row> shown() {
        return rows.stream().filter(row -> row.control().isManaged()).toList();
    }

    private double horizontalInsets() {
        final Insets insets = getInsets();
        return insets.getLeft() + insets.getRight();
    }
}
