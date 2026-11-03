package ua.bookloom.ui.screen;

import java.util.Objects;
import java.util.function.Function;
import javafx.collections.MapChangeListener;
import javafx.collections.WeakMapChangeListener;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.state.ModelEntry;
import ua.bookloom.ui.state.ModelQuantChips;
import ua.bookloom.ui.state.QuantChipModel;

/**
 * One model in the Settings › Models chooser: its label, and beside it the quant chip once a quant is known; with none
 * the cell has no graphic at all. Nothing is logged here: a cell is filled again on every scroll.
 */
final class ModelEntryCell extends ListCell<ModelEntry> {

    private final ModelQuantChips quantChips;
    private final Function<ModelEntry, String> label;
    private final Messages messages;
    private final Label quant;
    private final MapChangeListener<ModelQuantChips.Key, QuantChipModel> onChips = change -> refreshGraphic();

    ModelEntryCell(
            final ModelQuantChips quantChips, final Function<ModelEntry, String> label, final Messages messages) {
        this.quantChips = Objects.requireNonNull(quantChips, "quantChips");
        this.label = Objects.requireNonNull(label, "label");
        this.messages = Objects.requireNonNull(messages, "messages");
        quant = QuantChip.blank(messages);
        setContentDisplay(ContentDisplay.RIGHT);
        quantChips.chips().addListener(new WeakMapChangeListener<>(onChips));
    }

    @Override
    protected void updateItem(final ModelEntry item, final boolean empty) {
        super.updateItem(item, empty);
        if (empty || item == null) {
            setText(null);
            setGraphic(null);
            return;
        }
        setText(label.apply(item));
        refreshGraphic();
    }

    private void refreshGraphic() {
        final ModelEntry entry = getItem();
        if (isEmpty() || entry == null) {
            return;
        }
        final QuantChipModel chip = quantChips.chipFor(entry.providerId(), entry.modelId());
        if (chip == null) {
            setGraphic(null);
            return;
        }
        QuantChip.show(quant, chip, messages);
        setGraphic(quant);
    }
}
