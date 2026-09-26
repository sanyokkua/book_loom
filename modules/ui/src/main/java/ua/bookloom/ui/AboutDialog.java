package ua.bookloom.ui;

import java.util.Objects;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;

/**
 * The About card: the product, its licence and the build version, in the shape the reference gives every dialog
 * (header, body, footer).
 *
 * <p>A {@link DialogPane} placed in the {@link ModalHost} rather than a {@code Dialog} in its own window: the pane is
 * the control the reference's dialog maps to, and staying inside the scene keeps the stylesheet and the active theme
 * block, which a second window would not inherit.
 */
final class AboutDialog {

    static final String CARD_ID = "about-card";
    static final String CLOSE_ID = "about-close";

    private final Messages messages;
    private final String version;
    private final Runnable onClose;

    /**
     * Prepares the card's content.
     *
     * @param messages the catalogue every word is drawn from
     * @param version the build version to report; {@code dev} in a build carrying no release version
     * @param onClose what the Close button does
     */
    AboutDialog(final Messages messages, final String version, final Runnable onClose) {
        this.messages = Objects.requireNonNull(messages, "messages");
        this.version = Objects.requireNonNull(version, "version");
        this.onClose = Objects.requireNonNull(onClose, "onClose");
    }

    /**
     * Builds a fresh card, so a second opening never inherits state from the first.
     *
     * @return the card's root node, ready for {@link ModalHost#show(Node, boolean)}
     */
    Node card() {
        final DialogPane card = new DialogPane();
        card.setId(CARD_ID);
        card.getStyleClass().addAll("dialog-card", "elevation-lg");
        card.setHeader(header());
        card.setContent(body());
        card.getButtonTypes().add(ButtonType.CLOSE);
        final Button close = (Button) card.lookupButton(ButtonType.CLOSE);
        close.setId(CLOSE_ID);
        close.setText(messages.get(MessageKey.COMMON_CLOSE));
        close.getStyleClass().add("btn-primary");
        close.setOnAction(event -> onClose.run());
        card.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        return card;
    }

    private Node header() {
        final Label title = new Label(messages.get(MessageKey.SHELL_TITLE));
        title.getStyleClass().add("dialog-title");
        final Label subtitle = new Label(messages.get(MessageKey.ABOUT_SUBTITLE, version));
        subtitle.getStyleClass().add("dialog-sub");
        final VBox header = new VBox(title, subtitle);
        header.getStyleClass().add("dialog-h");
        return header;
    }

    private Node body() {
        final Label description = new Label(messages.get(MessageKey.ABOUT_DESCRIPTION));
        description.setWrapText(true);
        description.getStyleClass().add("dialog-text");
        return description;
    }
}
