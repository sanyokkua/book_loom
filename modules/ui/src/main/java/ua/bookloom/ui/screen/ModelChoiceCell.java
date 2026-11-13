package ua.bookloom.ui.screen;

import java.util.Objects;
import java.util.function.Supplier;
import javafx.collections.MapChangeListener;
import javafx.collections.WeakMapChangeListener;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import ua.bookloom.ui.control.Tips;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.state.ModelListing;
import ua.bookloom.ui.state.ModelQuantChips;
import ua.bookloom.ui.state.QuantChipModel;

/**
 * One model in the Providers model list: its id, and beside it the quant chip once a quant is known and, for a model
 * that runs on the provider's own servers, the neutral label "cloud". Every model stays listed and choosing one asks
 * nothing. Nothing is logged here: a cell is filled again on every scroll. A model with neither has no graphic at
 * all, not an invisible one.
 */
final class ModelChoiceCell extends ListCell<String> {

    private static final double GAP = 6;

    private final ModelListing listing;
    private final ModelQuantChips quantChips;
    private final Supplier<String> providerId;
    private final Messages messages;
    private final Label cloud = new Label();
    private final Label quant;
    private final HBox both;
    private final MapChangeListener<ModelQuantChips.Key, QuantChipModel> onChips = change -> refreshGraphic();

    ModelChoiceCell(
            final ModelListing listing,
            final ModelQuantChips quantChips,
            final Supplier<String> providerId,
            final Messages messages) {
        this.listing = Objects.requireNonNull(listing, "listing");
        this.quantChips = Objects.requireNonNull(quantChips, "quantChips");
        this.providerId = Objects.requireNonNull(providerId, "providerId");
        this.messages = Objects.requireNonNull(messages, "messages");
        cloud.setText(messages.get(MessageKey.SETTINGS_MODEL_CLOUD));
        cloud.getStyleClass().addAll("chip", "chip-neutral");
        cloud.getStyleClass().add("model-cloud");
        cloud.setMinWidth(Region.USE_PREF_SIZE);
        Tips.install(messages, cloud, MessageKey.SETTINGS_MODEL_CLOUD_TIP);
        quant = QuantChip.blank(messages);
        both = new HBox(GAP);
        setContentDisplay(ContentDisplay.RIGHT);
        quantChips.chips().addListener(new WeakMapChangeListener<>(onChips));
    }

    @Override
    protected void updateItem(final String item, final boolean empty) {
        super.updateItem(item, empty);
        if (empty || item == null) {
            setText(null);
            setGraphic(null);
            return;
        }
        setText(item);
        refreshGraphic();
    }

    private void refreshGraphic() {
        final String item = getItem();
        if (isEmpty() || item == null) {
            return;
        }
        final QuantChipModel chip = quantChips.chipFor(providerId.get(), item);
        final boolean isCloud = listing.isCloud(item);
        if (chip == null && !isCloud) {
            setGraphic(null);
            return;
        }
        both.getChildren().clear();
        if (chip != null) {
            QuantChip.show(quant, chip, messages);
            both.getChildren().add(quant);
        }
        if (isCloud) {
            both.getChildren().add(cloud);
        }
        setGraphic(both);
    }
}
