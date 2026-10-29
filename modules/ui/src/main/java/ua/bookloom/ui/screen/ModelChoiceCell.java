package ua.bookloom.ui.screen;

import java.util.Objects;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.layout.Region;
import ua.bookloom.ui.control.Tips;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.state.ModelListing;

/**
 * One model in the Providers model list: its id, and beside a model that runs on the provider's own servers the
 * neutral label "cloud". Every model stays listed and choosing one asks nothing. Nothing is logged here: a cell is
 * filled again on every scroll.
 */
final class ModelChoiceCell extends ListCell<String> {

    private final ModelListing listing;
    private final Label cloud = new Label();

    ModelChoiceCell(final ModelListing listing, final Messages messages) {
        this.listing = Objects.requireNonNull(listing, "listing");
        Objects.requireNonNull(messages, "messages");
        cloud.setText(messages.get(MessageKey.SETTINGS_MODEL_CLOUD));
        cloud.getStyleClass().addAll("chip", "chip-neutral");
        cloud.getStyleClass().add("model-cloud");
        cloud.setMinWidth(Region.USE_PREF_SIZE);
        Tips.install(messages, cloud, MessageKey.SETTINGS_MODEL_CLOUD_TIP);
        setContentDisplay(ContentDisplay.RIGHT);
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
        setGraphic(listing.isCloud(item) ? cloud : null);
    }
}
