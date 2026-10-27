package ua.bookloom.ui.notify;

import java.util.Objects;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.kordamp.ikonli.feather.Feather;
import ua.bookloom.api.AppError;
import ua.bookloom.ui.ModalHost;
import ua.bookloom.ui.control.TabMovesFocus;
import ua.bookloom.ui.control.Tips;
import ua.bookloom.ui.control.Wrapped;
import ua.bookloom.ui.dialog.ModalCard;
import ua.bookloom.ui.dialog.TextClipboard;
import ua.bookloom.ui.i18n.ErrorWords;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;

/**
 * The card for one failure: its title and message, its details behind a toggle, and the buttons that answer it.
 *
 * <p>Built like the About card, as a {@link ModalCard} for the {@link ModalHost}, so the stylesheet and the active
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
    static final String NOTE_ID = "error-note";
    static final String DETAILS_ID = "error-details";
    static final String COPY_ID = "error-details-copy";
    static final String RETRY_ID = "error-retry";
    static final String DISMISS_ID = "error-dismiss";

    private static final int DETAILS_ROWS = 5;

    private final Messages messages;
    private final AppError error;
    private final @Nullable Runnable onRetry;
    private final Runnable onDismiss;
    private final @Nullable MessageKey note;
    private final boolean detailsOpen;
    private final TextClipboard clipboard;

    /**
     * Prepares the card's content.
     *
     * @param messages the catalogue every word of the card's own is drawn from
     * @param error the failure; only its title, message, details and retryability are read
     * @param onRetry what Retry does, or {@code null} when the caller offered no way to repeat the action, in which
     *     case no Retry button is built even for a retryable failure
     * @param onDismiss what Dismiss does
     * @param note a line shown beneath the message, or {@code null} for none
     * @param detailsOpen whether the details start expanded ("Show technical details" is on)
     * @param clipboard where Copy details puts the failure's safe details
     */
    ErrorDialog(
            final Messages messages,
            final AppError error,
            final @Nullable Runnable onRetry,
            final Runnable onDismiss,
            final @Nullable MessageKey note,
            final boolean detailsOpen,
            final TextClipboard clipboard) {
        this.messages = Objects.requireNonNull(messages, "messages");
        this.error = Objects.requireNonNull(error, "error");
        this.onRetry = onRetry;
        this.onDismiss = Objects.requireNonNull(onDismiss, "onDismiss");
        this.note = note;
        this.detailsOpen = detailsOpen;
        this.clipboard = Objects.requireNonNull(clipboard, "clipboard");
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
        final ModalCard card = new ModalCard(ModalCard.Size.WIDE);
        card.setId(CARD_ID);
        card.iconHeader(Feather.ALERT_OCTAGON, "dialog-icon-err", ErrorWords.title(messages, error))
                .setId(TITLE_ID);
        card.setContent(body());
        addButtons(card, retryAction);
        return card;
    }

    private Node body() {
        final Label message = Wrapped.label(ErrorWords.message(messages, error), "dialog-text");
        message.setId(MESSAGE_ID);
        final VBox body = new VBox(message);
        body.getStyleClass().add("error-body");
        if (note != null) {
            final Label line = Wrapped.label(messages.get(note), "dialog-text");
            line.setId(NOTE_ID);
            body.getChildren().add(line);
        }
        if (hasDetails(error)) {
            body.getChildren().add(detailsControls(body));
        }
        return body;
    }

    private Node detailsControls(final VBox body) {
        final HBox row = new HBox(detailsToggle(body), copyButton());
        row.getStyleClass().add("error-details-actions");
        return row;
    }

    // Copies the details field alone: it holds only the allowlisted values, never the cause or a secret.
    private Node copyButton() {
        final Button copy = new Button(messages.get(MessageKey.ERROR_DETAILS_COPY));
        copy.setId(COPY_ID);
        Tips.install(messages, copy, MessageKey.ERROR_DETAILS_COPY_TIP);
        copy.getStyleClass().add("btn-secondary");
        copy.setOnAction(event -> {
            log.debug("error details copied: code={}", error.code());
            clipboard.copy(Objects.requireNonNull(error.details(), "details"));
        });
        return copy;
    }

    private Node detailsToggle(final VBox body) {
        final ToggleButton toggle = new ToggleButton(messages.get(MessageKey.ERROR_DETAILS_SHOW));
        toggle.setId(TOGGLE_ID);
        Tips.install(messages, toggle, MessageKey.ERROR_DETAILS_SHOW_TIP);
        toggle.getStyleClass().add("btn-secondary");
        final TextArea details = detailsArea();
        toggle.setOnAction(event -> expand(toggle, body, details));
        if (detailsOpen) {
            toggle.setSelected(true);
            expand(toggle, body, details);
        }
        return toggle;
    }

    private void expand(final ToggleButton toggle, final VBox body, final TextArea details) {
        final boolean expanded = toggle.isSelected();
        log.debug("error details {}", expanded ? "expanded" : "folded");
        toggle.setText(messages.get(expanded ? MessageKey.ERROR_DETAILS_HIDE : MessageKey.ERROR_DETAILS_SHOW));
        if (expanded) {
            body.getChildren().add(details);
        } else {
            body.getChildren().remove(details);
        }
    }

    private TextArea detailsArea() {
        final TextArea details = TabMovesFocus.install(new TextArea(error.details()));
        details.setId(DETAILS_ID);
        details.setEditable(false);
        details.setWrapText(true);
        details.setPrefRowCount(DETAILS_ROWS);
        details.getStyleClass().add("error-details");
        return details;
    }

    private void addButtons(final ModalCard card, final @Nullable Runnable retryAction) {
        if (retryAction != null) {
            Tips.install(
                            messages,
                            card.button(
                                    messages.get(MessageKey.ERROR_RETRY),
                                    ButtonBar.ButtonData.OK_DONE,
                                    RETRY_ID,
                                    "btn-primary"),
                            MessageKey.ERROR_RETRY_TIP)
                    .setOnAction(event -> retryAction.run());
        }
        Tips.install(
                        messages,
                        card.button(
                                messages.get(MessageKey.COMMON_CLOSE),
                                ButtonBar.ButtonData.CANCEL_CLOSE,
                                DISMISS_ID,
                                retryAction != null ? "btn-secondary" : "btn-primary"),
                        MessageKey.COMMON_CLOSE_TIP)
                .setOnAction(event -> onDismiss.run());
    }
}
