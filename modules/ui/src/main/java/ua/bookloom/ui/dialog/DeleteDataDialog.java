package ua.bookloom.ui.dialog;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.ui.ModalHost;
import ua.bookloom.ui.control.Wrapped;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;

/**
 * The {@link DeleteDataPrompt} shown as a card in the shell's modal host: what is deleted, what is never touched, and
 * that a translation at work is stopped first. Cancel is the default and takes the focus, because it changes nothing.
 */
@Slf4j
@Singleton
public final class DeleteDataDialog implements DeleteDataPrompt {

    static final String CARD_ID = "delete-data-card";
    static final String CANCEL_ID = "delete-data-cancel";
    static final String BACKUP_ID = "delete-data-backup";
    static final String YES_ID = "delete-data-yes";
    static final String RUNNING_ID = "delete-data-running";

    private final ModalHost modalHost;
    private final Messages messages;

    /**
     * Creates the dialog.
     *
     * @param modalHost the shared overlay the card is shown in
     * @param messages the catalogue every word of the card comes from
     */
    @Inject
    public DeleteDataDialog(final ModalHost modalHost, final Messages messages) {
        this.modalHost = Objects.requireNonNull(modalHost, "modalHost");
        this.messages = Objects.requireNonNull(messages, "messages");
    }

    @Override
    public void ask(final boolean stopsRun, final Runnable backupFirst, final Runnable delete) {
        Objects.requireNonNull(backupFirst, "backupFirst");
        Objects.requireNonNull(delete, "delete");
        log.debug("asking before deleting all data, a translation to stop first={}", stopsRun);
        final FormCard card = new FormCard(
                modalHost,
                messages,
                CARD_ID,
                ModalCard.Size.DIALOG,
                messages.get(MessageKey.CONFIRM_DELETE_DATA_TITLE));
        card.body(body(stopsRun).toArray(Node[]::new));
        final Button cancel = wire(card, backupFirst, delete);
        card.show();
        cancel.requestFocus();
    }

    private List<Node> body(final boolean stopsRun) {
        final List<Node> lines = new ArrayList<>();
        lines.add(Wrapped.label(messages.get(MessageKey.CONFIRM_DELETE_DATA_TEXT), "dialog-text"));
        lines.add(Wrapped.label(messages.get(MessageKey.CONFIRM_DELETE_DATA_KEPT), "dialog-text"));
        if (stopsRun) {
            final Node running = Wrapped.label(messages.get(MessageKey.CONFIRM_DELETE_DATA_RUNNING), "hint-warn");
            running.setId(RUNNING_ID);
            lines.add(running);
        }
        return lines;
    }

    private void backupButton(final FormCard card, final Runnable backupFirst) {
        card.button(
                BACKUP_ID,
                messages.get(MessageKey.CONFIRM_DELETE_DATA_BACKUP),
                MessageKey.CONFIRM_DELETE_DATA_BACKUP_TIP,
                ButtonBar.ButtonData.OTHER,
                "btn-secondary",
                () -> {
                    log.debug("delete all data: a backup first");
                    backupFirst.run();
                });
    }

    private Button wire(final FormCard card, final Runnable backupFirst, final Runnable delete) {
        final Button cancel = card.button(
                CANCEL_ID,
                messages.get(MessageKey.COMMON_CANCEL),
                MessageKey.COMMON_CANCEL_TIP,
                ButtonBar.ButtonData.CANCEL_CLOSE,
                "btn-secondary",
                () -> log.debug("delete all data was cancelled: nothing changes"));
        backupButton(card, backupFirst);
        card.button(
                        YES_ID,
                        messages.get(MessageKey.CONFIRM_DELETE_DATA_YES),
                        MessageKey.CONFIRM_DELETE_DATA_YES_TIP,
                        ButtonBar.ButtonData.OK_DONE,
                        "btn-danger-outline",
                        () -> {
                            log.info("delete all data was confirmed");
                            delete.run();
                        })
                .setDefaultButton(false);
        // The safe answer is the one Enter gives; set once every button is in, as each new one resets the default.
        cancel.setDefaultButton(true);
        return cancel;
    }
}
