package ua.bookloom.ui.dialog;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import java.util.Objects;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.ui.ModalHost;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;

/**
 * The {@link ConfirmPrompt} shown as a card in the shell's modal host: the consequence in words, a Cancel that is the
 * default button and takes the focus, and a confirming button worded for what it does. Cancel, Escape and a click
 * outside change nothing; only the confirming button runs the action. FX thread only.
 */
@Slf4j
@Singleton
public final class ConfirmDialog implements ConfirmPrompt {

    static final String CARD_ID = "confirm-card";
    static final String CANCEL_ID = "confirm-cancel";
    static final String YES_ID = "confirm-yes";

    private final ModalHost modalHost;
    private final Messages messages;

    /**
     * Creates the dialog.
     *
     * @param modalHost the shared overlay the card is shown in
     * @param messages the catalogue every word of the card comes from
     */
    @Inject
    public ConfirmDialog(final ModalHost modalHost, final Messages messages) {
        this.modalHost = Objects.requireNonNull(modalHost, "modalHost");
        this.messages = Objects.requireNonNull(messages, "messages");
    }

    @Override
    public void ask(
            final Question question,
            final List<Object> titleArgs,
            final List<Object> textArgs,
            final Runnable onConfirm) {
        Objects.requireNonNull(question, "question");
        Objects.requireNonNull(titleArgs, "titleArgs");
        Objects.requireNonNull(textArgs, "textArgs");
        Objects.requireNonNull(onConfirm, "onConfirm");
        log.debug("asking: {} with {}", question.title(), textArgs);
        final FormCard card = new FormCard(
                modalHost,
                messages,
                CARD_ID,
                ModalCard.Size.DIALOG,
                messages.get(question.title(), titleArgs.toArray()));
        card.text(messages.get(question.text(), textArgs.toArray()));
        final Button cancel = wire(card, question, onConfirm);
        card.show();
        cancel.requestFocus();
    }

    private Button wire(final FormCard card, final Question question, final Runnable onConfirm) {
        final Button cancel = card.button(
                CANCEL_ID,
                messages.get(question.no()),
                MessageKey.COMMON_CANCEL_TIP,
                ButtonBar.ButtonData.CANCEL_CLOSE,
                "btn-secondary",
                () -> log.debug("the question {} was answered no", question.title()));
        card.button(
                        YES_ID,
                        messages.get(question.yes()),
                        question.yesTip(),
                        ButtonBar.ButtonData.OK_DONE,
                        "btn-danger-outline",
                        () -> {
                            log.info("the question {} was confirmed", question.title());
                            onConfirm.run();
                        })
                .setDefaultButton(false);
        // The safe answer is the one Enter gives; set once every button is in, as each new one resets the default.
        cancel.setDefaultButton(true);
        return cancel;
    }
}
