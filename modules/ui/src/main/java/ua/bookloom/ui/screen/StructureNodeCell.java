package ua.bookloom.ui.screen;

import java.text.NumberFormat;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.TreeCell;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.document.StructureNode;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;

/**
 * Draws one node of the structure tree as its title and a count pill, and does nothing else.
 *
 * <p>The cell logs nothing, since it is refreshed on every scroll, and builds its nodes once, only changing their text
 * as it is reused. It is given no width of its own, because a cell never narrows below its preferred width: the title
 * label, the only one allowed to shrink, then gives way first and shows an ellipsis. A node without a count of its own,
 * one that only points into a unit another node counts, shows no pill rather than a misleading zero.
 */
final class StructureNodeCell extends TreeCell<StructureNode> {

    private static final double ROW_SPACING = 12;

    private final Messages messages;
    private final NumberFormat numbers;
    private final Label title = new Label();
    private final Label pill = new Label();
    private final HBox box = new HBox(ROW_SPACING, title, pill);

    StructureNodeCell(final Messages messages, final NumberFormat numbers) {
        this.messages = messages;
        this.numbers = numbers;
        setPrefWidth(0);
        title.getStyleClass().add("structure-row-title");
        title.setTextOverrun(OverrunStyle.CENTER_ELLIPSIS);
        title.setMinWidth(0);
        HBox.setHgrow(title, Priority.ALWAYS);
        title.setMaxWidth(Double.MAX_VALUE);
        pill.getStyleClass().add("count-pill");
        pill.setMinWidth(Region.USE_PREF_SIZE);
        box.setAlignment(Pos.CENTER_LEFT);
        setText(null);
    }

    @Override
    protected void updateItem(final @Nullable StructureNode node, final boolean empty) {
        super.updateItem(node, empty);
        if (empty || node == null) {
            setGraphic(null);
            return;
        }
        title.setText(node.title().isBlank() ? messages.get(MessageKey.STRUCTURE_UNTITLED) : node.title());
        final Integer count = node.segmentCount();
        pill.setText(count == null ? null : numbers.format(count));
        pill.setVisible(count != null);
        pill.setManaged(count != null);
        setGraphic(box);
    }
}
