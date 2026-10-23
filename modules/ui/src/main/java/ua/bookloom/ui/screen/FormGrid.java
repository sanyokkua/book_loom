package ua.bookloom.ui.screen;

import java.util.Objects;
import javafx.geometry.Pos;
import javafx.geometry.VPos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;

/** Labelled rows in two aligned columns, where a row can be hidden as a whole without leaving a gap. */
final class FormGrid extends GridPane {

    private static final double LABEL_WIDTH = 190;
    private static final double GAP = 10;

    /** A row: its label and its control, shown or hidden together. */
    record Row(Label label, Node control) {

        void show(final boolean shown) {
            for (final Node node : new Node[] {label, control}) {
                node.setVisible(shown);
                node.setManaged(shown);
            }
        }
    }

    private int next;

    FormGrid() {
        setHgap(GAP + GAP);
        setVgap(GAP);
        final ColumnConstraints labels = new ColumnConstraints(LABEL_WIDTH);
        labels.setMinWidth(LABEL_WIDTH);
        final ColumnConstraints controls = new ColumnConstraints();
        controls.setHgrow(Priority.ALWAYS);
        getColumnConstraints().setAll(labels, controls);
    }

    /** Adds a row under the last one. */
    Row row(final String label, final Node control) {
        Objects.requireNonNull(label, "label");
        Objects.requireNonNull(control, "control");
        final Label text = new Label(label);
        text.getStyleClass().add("muted");
        text.setWrapText(true);
        GridPane.setValignment(text, VPos.TOP);
        setAlignment(Pos.TOP_LEFT);
        add(text, 0, next);
        add(control, 1, next);
        next++;
        return new Row(text, control);
    }
}
