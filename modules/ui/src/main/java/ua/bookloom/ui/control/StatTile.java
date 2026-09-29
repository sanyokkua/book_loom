package ua.bookloom.ui.control;

import java.util.Objects;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/** A count tile: the number above its caption, on the stat surface, sharing its row equally with its siblings. */
public final class StatTile extends VBox {

    /**
     * Builds the tile around a number label the caller owns, so the caller can bind or set its text.
     *
     * @param id the tile's node id
     * @param number the label holding the figure; it gets the {@code stat-number} class
     * @param caption the visible, already translated caption
     */
    public StatTile(final String id, final Label number, final String caption) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(number, "number");
        Objects.requireNonNull(caption, "caption");
        final Label label = new Label(caption);
        label.getStyleClass().add("stat-caption");
        if (!number.getStyleClass().contains("stat-number")) {
            number.getStyleClass().add("stat-number");
        }
        getChildren().addAll(number, label);
        setId(id);
        getStyleClass().add("stat");
        HBox.setHgrow(this, Priority.ALWAYS);
        setMaxWidth(Double.MAX_VALUE);
    }
}
