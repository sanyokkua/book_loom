package ua.bookloom.ui.dialog;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Objects;
import java.util.function.Consumer;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.ui.ModalHost;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;

/**
 * The card that asks for guidance and a lower temperature before a segment is retried, shown in the shell's modal
 * host like the other cards. Escape closes it, which the host guarantees, and means no retry.
 */
@Slf4j
@Singleton
public final class RetryWithNoteDialog {

    static final String CARD_ID = "retry-note-card";
    static final String NOTE_ID = "retry-note-text";
    static final String LOWER_ID = "retry-note-lower";
    static final String CANCEL_ID = "retry-note-cancel";
    static final String RETRY_ID = "retry-note-confirm";

    private static final int NOTE_ROWS = 4;

    private final ModalHost modalHost;
    private final Messages messages;

    /**
     * What the person chose.
     *
     * @param note the guidance for the model, or null when none was typed
     * @param lowerTemperature whether the retry samples at a lower temperature
     */
    public record Choice(@Nullable String note, boolean lowerTemperature) {}

    /**
     * Creates the dialog.
     *
     * @param modalHost the shared overlay the card is shown in
     * @param messages the catalogue every word of the card comes from
     */
    @Inject
    public RetryWithNoteDialog(final ModalHost modalHost, final Messages messages) {
        this.modalHost = Objects.requireNonNull(modalHost, "modalHost");
        this.messages = Objects.requireNonNull(messages, "messages");
    }

    /**
     * Shows the card for one segment. FX thread only.
     *
     * @param locator the non-null locator of the segment, shown under the title
     * @param onRetry told the choice when Retry is pressed and never when the card is cancelled
     */
    public void ask(final String locator, final Consumer<Choice> onRetry) {
        Objects.requireNonNull(locator, "locator");
        Objects.requireNonNull(onRetry, "onRetry");
        log.debug("asking for a note to retry segment at {}", locator);
        final TextArea note = new TextArea();
        note.setId(NOTE_ID);
        note.setPrefRowCount(NOTE_ROWS);
        note.setWrapText(true);
        final CheckBox lower = new CheckBox(messages.get(MessageKey.DIALOG_RETRY_LOWER));
        lower.setId(LOWER_ID);
        final Label caption = new Label(messages.get(MessageKey.DIALOG_RETRY_NOTE));
        caption.getStyleClass().add("dialog-text");
        final DialogPane card = card(locator, new VBox(caption, note, lower));
        wire(card, note, lower, onRetry);
        modalHost.show(card, false);
    }

    private DialogPane card(final String locator, final VBox body) {
        final DialogPane card = new DialogPane();
        card.setId(CARD_ID);
        card.getStyleClass().addAll("dialog-card", "elevation-lg");
        final Label title = new Label(messages.get(MessageKey.DIALOG_RETRY_TITLE));
        title.getStyleClass().add("dialog-title");
        final Label subtitle = new Label(locator);
        subtitle.getStyleClass().add("dialog-sub");
        final VBox header = new VBox(title, subtitle);
        header.getStyleClass().add("dialog-h");
        card.setHeader(header);
        card.setContent(body);
        card.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        return card;
    }

    private void wire(
            final DialogPane card, final TextArea note, final CheckBox lower, final Consumer<Choice> onRetry) {
        final ButtonType cancelType =
                new ButtonType(messages.get(MessageKey.DIALOG_RETRY_CANCEL), ButtonBar.ButtonData.CANCEL_CLOSE);
        final ButtonType retryType =
                new ButtonType(messages.get(MessageKey.DIALOG_RETRY_CONFIRM), ButtonBar.ButtonData.OK_DONE);
        card.getButtonTypes().setAll(cancelType, retryType);
        final Button cancel = (Button) card.lookupButton(cancelType);
        final Button retry = (Button) card.lookupButton(retryType);
        cancel.setId(CANCEL_ID);
        cancel.getStyleClass().add("btn-secondary");
        cancel.setOnAction(event -> {
            log.debug("the retry was cancelled");
            modalHost.hide();
        });
        retry.setId(RETRY_ID);
        retry.getStyleClass().add("btn-primary");
        retry.setOnAction(event -> {
            final String typed = note.getText().strip();
            log.debug(
                    "the retry was chosen: note given {}, lower temperature {}", !typed.isEmpty(), lower.isSelected());
            modalHost.hide();
            onRetry.accept(new Choice(typed.isEmpty() ? null : typed, lower.isSelected()));
        });
    }
}
