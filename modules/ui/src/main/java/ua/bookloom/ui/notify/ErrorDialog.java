package ua.bookloom.ui.notify;

import java.util.Objects;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;
import ua.bookloom.api.AppError;
import ua.bookloom.ui.ModalHost;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;

/**
 * The card for one failure: its title and message, its details behind a toggle, and the buttons that answer it.
 *
 * <p>Built like the About card, as a {@link DialogPane} for the {@link ModalHost}, so the stylesheet and the active
 * theme reach it. The details expander is hand-built rather than the pane's own: that one carries an untranslated
 * link, and adding and removing the text area keeps the details out of the scene graph until they are asked for.
 * The failure's cause is never read, because it is the one part of an {@link AppError} that was never filtered.
 */
@Slf4j
final class ErrorDialog {

    static final String CARD_ID = "error-card";
    static final String TITLE_ID = "error-title";
    static final String MESSAGE_ID = "error-message";
    static final String TOGGLE_ID = "error-details-toggle";
    static final String DETAILS_ID = "error-details";
    static final String RETRY_ID = "error-retry";
    static final String DISMISS_ID = "error-dismiss";

    private static final int DETAILS_ROWS = 5;

    private final Messages messages;
    private final AppError error;
    private final @Nullable Runnable onRetry;
    private final Runnable onDismiss;

    /**
     * Prepares the card's content.
     *
     * @param messages the catalogue every word of the card's own is drawn from
     * @param error the failure; only its title, message, details and retryability are read
     * @param onRetry what Retry does, or {@code null} when the caller offered no way to repeat the action, in which
     *     case no Retry button is built even for a retryable failure
     * @param onDismiss what Dismiss does
     */
    ErrorDialog(
            final Messages messages, final AppError error, final @Nullable Runnable onRetry, final Runnable onDismiss) {
        this.messages = Objects.requireNonNull(messages, "messages");
        this.error = Objects.requireNonNull(error, "error");
        this.onRetry = onRetry;
        this.onDismiss = Objects.requireNonNull(onDismiss, "onDismiss");
    }

    /**
     * Whether a failure carries details worth folding behind a toggle.
     *
     * @param error the failure to inspect
     * @return {@code true} if its details are present and not blank, {@code false} otherwise
     */
    static boolean hasDetails(final AppError error) {
        final String details = error.details();
        return details != null && !details.isBlank();
    }

    /**
     * Builds a fresh card, so a second failure never inherits the expanded state of the first.
     *
     * @return the card's root node, ready for {@link ModalHost#show(Node, boolean)}
     */
    Node card() {
        final Runnable retryAction = error.retryable() ? onRetry : null;
        log.debug("building the error card: retryOffered={}, hasDetails={}", retryAction != null, hasDetails(error));
        final DialogPane card = new DialogPane();
        card.setId(CARD_ID);
        card.getStyleClass().addAll("dialog-card", "elevation-lg");
        card.setHeader(header());
        card.setContent(body());
        addButtons(card, retryAction);
        card.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        return card;
    }

    private Node header() {
        final FontIcon icon = new FontIcon(Feather.ALERT_OCTAGON);
        icon.getStyleClass().add("dialog-icon-err");
        final Label title = new Label(error.title());
        title.setId(TITLE_ID);
        title.setWrapText(true);
        title.getStyleClass().add("dialog-title");
        final HBox header = new HBox(icon, title);
        header.getStyleClass().addAll("dialog-h", "error-header");
        return header;
    }

    private Node body() {
        final Label message = new Label(error.message());
        message.setId(MESSAGE_ID);
        message.setWrapText(true);
        message.getStyleClass().add("dialog-text");
        final VBox body = new VBox(message);
        body.getStyleClass().add("error-body");
        if (hasDetails(error)) {
            body.getChildren().add(detailsToggle(body));
        }
        return body;
    }

    private Node detailsToggle(final VBox body) {
        final ToggleButton toggle = new ToggleButton(messages.get(MessageKey.ERROR_DETAILS_SHOW));
        toggle.setId(TOGGLE_ID);
        toggle.getStyleClass().add("btn-secondary");
        final TextArea details = detailsArea();
        toggle.setOnAction(event -> {
            final boolean expanded = toggle.isSelected();
            log.debug("error details {}", expanded ? "expanded" : "folded");
            toggle.setText(messages.get(expanded ? MessageKey.ERROR_DETAILS_HIDE : MessageKey.ERROR_DETAILS_SHOW));
            if (expanded) {
                body.getChildren().add(details);
            } else {
                body.getChildren().remove(details);
            }
        });
        return toggle;
    }

    private TextArea detailsArea() {
        final TextArea details = new TextArea(error.details());
        details.setId(DETAILS_ID);
        details.setEditable(false);
        details.setWrapText(true);
        details.setPrefRowCount(DETAILS_ROWS);
        details.getStyleClass().add("error-details");
        return details;
    }

    private void addButtons(final DialogPane card, final @Nullable Runnable retryAction) {
        final boolean retryOffered = retryAction != null;
        if (retryAction != null) {
            final ButtonType retryType =
                    new ButtonType(messages.get(MessageKey.ERROR_RETRY), ButtonBar.ButtonData.OK_DONE);
            card.getButtonTypes().add(retryType);
            final Button retry = (Button) card.lookupButton(retryType);
            retry.setId(RETRY_ID);
            retry.getStyleClass().add("btn-primary");
            retry.setOnAction(event -> retryAction.run());
        }
        card.getButtonTypes().add(ButtonType.CLOSE);
        final Button dismiss = (Button) card.lookupButton(ButtonType.CLOSE);
        dismiss.setId(DISMISS_ID);
        dismiss.setText(messages.get(MessageKey.COMMON_CLOSE));
        dismiss.getStyleClass().add(retryOffered ? "btn-secondary" : "btn-primary");
        dismiss.setOnAction(event -> onDismiss.run());
    }
}
