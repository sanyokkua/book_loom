package ua.bookloom.ui.control;

import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.jspecify.annotations.Nullable;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.state.StepEntry;

/** One step: its marker, its name and its one result line. Logs nothing: rows change up to ten times a second. */
final class StepCell extends ListCell<StepEntry> {

    private final Messages messages;
    private final @Nullable String idPrefix;
    private final Label marker = new Label();
    private final Label name = new Label();
    private final Label result = new Label();
    private final HBox row;

    /**
     * Builds a cell.
     *
     * @param messages the catalogue the step's words come from
     * @param idPrefix the start of the cell's id, followed by its index, or {@code null} for a cell with none
     */
    StepCell(final Messages messages, final @Nullable String idPrefix) {
        this.messages = messages;
        this.idPrefix = idPrefix;
        name.getStyleClass().add("progress-step-name");
        result.getStyleClass().add("progress-step-result");
        marker.getStyleClass().add("progress-marker");
        row = new HBox(marker, new VBox(name, result));
        row.getStyleClass().add("progress-step");
        row.setAlignment(Pos.CENTER_LEFT);
    }

    @Override
    protected void updateItem(final @Nullable StepEntry item, final boolean empty) {
        super.updateItem(item, empty);
        if (empty || item == null) {
            setId(null);
            setGraphic(null);
            return;
        }
        setId(idPrefix == null ? null : idPrefix + getIndex());
        final StepWords.Look look = StepWords.Look.of(item);
        marker.setText(look.glyph());
        marker.getStyleClass().removeIf(style -> style.startsWith("progress-marker-"));
        marker.getStyleClass().add("progress-marker-" + look.token());
        name.setText(StepWords.name(messages, item));
        result.setText(StepWords.result(messages, item));
        setGraphic(row);
    }
}
