package ua.bookloom.ui.dialog;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Objects;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.ui.ModalHost;
import ua.bookloom.ui.control.Tips;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;

/**
 * The question asked before something that cannot be undone: a card with the consequence in words, a Cancel that is the
 * default button and takes the focus, and a confirming button worded for what it does. Cancel, Escape and a click
 * outside change nothing; only the confirming button runs the action. FX thread only.
 */
@Slf4j
@Singleton
public final class ConfirmDialog {

    static final String CARD_ID = "confirm-card";
    static final String CANCEL_ID = "confirm-cancel";
    static final String YES_ID = "confirm-yes";

    /**
     * One question.
     *
     * @param title the card's heading
     * @param text what happens if the person confirms
     * @param yes the confirming button's label, worded for the action
     * @param yesTip the confirming button's hover explanation
     */
    public record Question(MessageKey title, MessageKey text, MessageKey yes, MessageKey yesTip) {

        /** Stopping the run. */
        public static final Question STOP_RUN = new Question(
                MessageKey.CONFIRM_STOP_TITLE,
                MessageKey.CONFIRM_STOP_TEXT,
                MessageKey.CONFIRM_STOP_YES,
                MessageKey.CONFIRM_STOP_YES_TIP);

        /** Throwing away the person's own edit of a segment. */
        public static final Question REVERT_EDIT = new Question(
                MessageKey.CONFIRM_REVERT_TITLE,
                MessageKey.CONFIRM_REVERT_TEXT,
                MessageKey.CONFIRM_REVERT_YES,
                MessageKey.CONFIRM_REVERT_YES_TIP);

        /** Cancelling an import whose project already holds work. */
        public static final Question DISCARD_PROJECT = new Question(
                MessageKey.CONFIRM_IMPORT_TITLE,
                MessageKey.CONFIRM_IMPORT_TEXT,
                MessageKey.CONFIRM_IMPORT_YES,
                MessageKey.CONFIRM_IMPORT_YES_TIP);

        /** Closing the window while work runs. */
        public static final Question QUIT = new Question(
                MessageKey.CONFIRM_QUIT_TITLE,
                MessageKey.CONFIRM_QUIT_TEXT,
                MessageKey.CONFIRM_QUIT_YES,
                MessageKey.CONFIRM_QUIT_YES_TIP);

        /** Closing the window on a translation that ended and was never exported. */
        public static final Question QUIT_UNEXPORTED = new Question(
                MessageKey.CONFIRM_QUIT_UNEXPORTED_TITLE,
                MessageKey.CONFIRM_QUIT_UNEXPORTED_TEXT,
                MessageKey.CONFIRM_QUIT_UNEXPORTED_YES,
                MessageKey.CONFIRM_QUIT_UNEXPORTED_YES_TIP);

        /** Rejects a missing part. */
        public Question {
            Objects.requireNonNull(title, "title");
            Objects.requireNonNull(text, "text");
            Objects.requireNonNull(yes, "yes");
            Objects.requireNonNull(yesTip, "yesTip");
        }
    }

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

    /**
     * Asks, and runs {@code onConfirm} only if the person confirms.
     *
     * @param question what is asked
     * @param onConfirm what the confirming button does, after the card has closed
     */
    public void ask(final Question question, final Runnable onConfirm) {
        Objects.requireNonNull(question, "question");
        Objects.requireNonNull(onConfirm, "onConfirm");
        log.debug("asking: {}", question.title());
        final Label text = new Label(messages.get(question.text()));
        text.setWrapText(true);
        text.getStyleClass().add("dialog-text");
        final DialogPane card = new ModalCard();
        card.setId(CARD_ID);
        card.getStyleClass().addAll("dialog-card", "elevation-lg");
        final Label title = new Label(messages.get(question.title()));
        title.getStyleClass().add("dialog-title");
        final VBox header = new VBox(title);
        header.getStyleClass().add("dialog-h");
        card.setHeader(header);
        card.setContent(new VBox(text));
        card.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        final Button cancel = wire(card, question, onConfirm);
        modalHost.show(card, false, true);
        cancel.requestFocus();
    }

    private Button wire(final DialogPane card, final Question question, final Runnable onConfirm) {
        final ButtonType cancelType =
                new ButtonType(messages.get(MessageKey.COMMON_CANCEL), ButtonBar.ButtonData.CANCEL_CLOSE);
        final ButtonType yesType = new ButtonType(messages.get(question.yes()), ButtonBar.ButtonData.OK_DONE);
        card.getButtonTypes().setAll(cancelType, yesType);
        final Button cancel = (Button) card.lookupButton(cancelType);
        cancel.setId(CANCEL_ID);
        Tips.install(messages, cancel, MessageKey.COMMON_CANCEL_TIP);
        cancel.getStyleClass().add("btn-secondary");
        // The safe answer is the one Enter gives.
        cancel.setDefaultButton(true);
        cancel.setOnAction(event -> {
            log.debug("the question {} was answered no", question.title());
            modalHost.hide();
        });
        final Button yes = (Button) card.lookupButton(yesType);
        yes.setId(YES_ID);
        Tips.install(messages, yes, question.yesTip());
        yes.getStyleClass().add("btn-danger-outline");
        yes.setDefaultButton(false);
        yes.setOnAction(event -> {
            log.info("the question {} was confirmed", question.title());
            modalHost.hide();
            onConfirm.run();
        });
        return cancel;
    }
}
