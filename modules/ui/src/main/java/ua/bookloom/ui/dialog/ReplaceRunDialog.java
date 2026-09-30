package ua.bookloom.ui.dialog;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Objects;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.ui.ModalHost;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.state.RunState;

/**
 * The {@link ReplaceRunPrompt} shown as a card in the shell's modal host.
 *
 * <p>The card is shown without dismissal from outside, so the question is answered through its own buttons; Escape
 * still closes it, which the host guarantees for every card, and means keeping the run.
 */
@Slf4j
@Singleton
public final class ReplaceRunDialog implements ReplaceRunPrompt {

    static final String CARD_ID = "replace-run-card";
    static final String KEEP_ID = "replace-run-keep";
    static final String CONFIRM_ID = "replace-run-confirm";
    static final String STOPPING_ID = "replace-run-stopping";

    private final ModalHost modalHost;
    private final Messages messages;
    private @Nullable Node shown;

    /**
     * Creates the dialog.
     *
     * @param modalHost the shared overlay the card is shown in
     * @param messages the catalogue every word of the card comes from
     */
    @Inject
    public ReplaceRunDialog(final ModalHost modalHost, final Messages messages) {
        this.modalHost = Objects.requireNonNull(modalHost, "modalHost");
        this.messages = Objects.requireNonNull(messages, "messages");
    }

    @Override
    public void ask(
            final String runFileName, final String newFileName, final RunState state, final Runnable onConfirm) {
        Objects.requireNonNull(runFileName, "runFileName");
        Objects.requireNonNull(newFileName, "newFileName");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(onConfirm, "onConfirm");
        log.debug("asking whether to replace the {} run of {} with {}", state, runFileName, newFileName);
        final boolean stopped = state == RunState.STOPPED;
        final Label text = new Label(messages.get(
                stopped ? MessageKey.DIALOG_REPLACE_RUN_STOPPED : MessageKey.DIALOG_REPLACE_RUN_ACTIVE,
                runFileName,
                newFileName));
        text.setWrapText(true);
        text.getStyleClass().add("dialog-text");
        final VBox body = new VBox(text);
        final DialogPane card = card(body);
        wire(card, body, stopped, onConfirm);
        shown = card;
        modalHost.show(card, false);
    }

    @Override
    public void dismiss() {
        final Node card = shown;
        shown = null;
        if (card != null && card.getScene() != null) {
            log.debug("closing the replace-run question");
            modalHost.hide();
        } else {
            log.debug("dismiss ignored: the replace-run question is no longer shown");
        }
    }

    private DialogPane card(final Node body) {
        final DialogPane card = new ModalCard();
        card.setId(CARD_ID);
        card.getStyleClass().addAll("dialog-card", "elevation-lg");
        final Label title = new Label(messages.get(MessageKey.DIALOG_REPLACE_RUN_TITLE));
        title.getStyleClass().add("dialog-title");
        final VBox header = new VBox(title);
        header.getStyleClass().add("dialog-h");
        card.setHeader(header);
        card.setContent(body);
        card.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        return card;
    }

    private void wire(final DialogPane card, final VBox body, final boolean stopped, final Runnable onConfirm) {
        final ButtonType keepType =
                new ButtonType(messages.get(MessageKey.DIALOG_REPLACE_RUN_KEEP), ButtonBar.ButtonData.CANCEL_CLOSE);
        final ButtonType confirmType =
                new ButtonType(messages.get(MessageKey.DIALOG_REPLACE_RUN_CONFIRM), ButtonBar.ButtonData.OK_DONE);
        card.getButtonTypes().setAll(keepType, confirmType);
        final Button keep = (Button) card.lookupButton(keepType);
        final Button confirm = (Button) card.lookupButton(confirmType);
        keep.setId(KEEP_ID);
        keep.getStyleClass().add("btn-secondary");
        keep.setOnAction(event -> {
            log.debug("the run is kept");
            modalHost.hide();
        });
        confirm.setId(CONFIRM_ID);
        confirm.getStyleClass().add("btn-danger-outline");
        confirm.setOnAction(event -> {
            log.debug("the run is to be discarded, run already stopped: {}", stopped);
            if (!stopped) {
                showStopping(body, keep, confirm);
            }
            onConfirm.run();
        });
    }

    private void showStopping(final VBox body, final Button keep, final Button confirm) {
        keep.setDisable(true);
        confirm.setDisable(true);
        final Label stopping = new Label(messages.get(MessageKey.DIALOG_REPLACE_RUN_STOPPING));
        stopping.setId(STOPPING_ID);
        stopping.getStyleClass().add("dialog-sub");
        body.getChildren().add(stopping);
    }
}
