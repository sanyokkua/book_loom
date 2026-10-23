package ua.bookloom.ui.dialog;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
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
     * @param no the safe button's label; Cancel unless the question itself is about cancelling
     */
    public record Question(MessageKey title, MessageKey text, MessageKey yes, MessageKey yesTip, MessageKey no) {

        /** Stopping the run. */
        public static final Question STOP_RUN = new Question(
                MessageKey.CONFIRM_STOP_TITLE,
                MessageKey.CONFIRM_STOP_TEXT,
                MessageKey.CONFIRM_STOP_YES,
                MessageKey.CONFIRM_STOP_YES_TIP);

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

        /** Cancelling the export from its progress dialog. */
        public static final Question CANCEL_EXPORT = new Question(
                MessageKey.EXPORT_CANCEL_TITLE,
                MessageKey.EXPORT_CANCEL_TEXT,
                MessageKey.EXPORT_CANCEL_YES,
                MessageKey.EXPORT_CANCEL_YES_TIP,
                MessageKey.EXPORT_CANCEL_NO);

        /** Replacing existing files with the export; the title counts them and the text names each. */
        public static final Question REPLACE_EXISTING = new Question(
                MessageKey.EXPORT_REPLACE_TITLE,
                MessageKey.EXPORT_REPLACE_TEXT,
                MessageKey.EXPORT_REPLACE_YES,
                MessageKey.EXPORT_REPLACE_YES_TIP);

        /** Ending the last check early and writing the file; the text says what was reread. */
        public static final Question WRITE_NOW = new Question(
                MessageKey.EXPORT_WRITE_NOW_TITLE,
                MessageKey.EXPORT_WRITE_NOW_TEXT,
                MessageKey.EXPORT_WRITE_NOW_YES,
                MessageKey.EXPORT_WRITE_NOW_YES_TIP,
                MessageKey.EXPORT_WRITE_NOW_NO);

        /** Ending the last check before it reread anything. */
        public static final Question WRITE_NOW_EARLY = new Question(
                MessageKey.EXPORT_WRITE_NOW_TITLE,
                MessageKey.EXPORT_WRITE_NOW_TEXT_EARLY,
                MessageKey.EXPORT_WRITE_NOW_YES,
                MessageKey.EXPORT_WRITE_NOW_YES_TIP,
                MessageKey.EXPORT_WRITE_NOW_NO);

        /** Removing a project and its saved work; its title names the project. */
        public static final Question REMOVE_PROJECT = new Question(
                MessageKey.CONFIRM_REMOVE_PROJECT_TITLE,
                MessageKey.CONFIRM_REMOVE_PROJECT_TEXT,
                MessageKey.CONFIRM_REMOVE_PROJECT_YES,
                MessageKey.CONFIRM_REMOVE_PROJECT_YES_TIP);

        /**
         * A question whose safe answer is Cancel.
         *
         * @param title the card's heading
         * @param text what happens if the person confirms
         * @param yes the confirming button's label
         * @param yesTip the confirming button's hover explanation
         */
        public Question(final MessageKey title, final MessageKey text, final MessageKey yes, final MessageKey yesTip) {
            this(title, text, yes, yesTip, MessageKey.COMMON_CANCEL);
        }

        /** Rejects a missing part. */
        public Question {
            Objects.requireNonNull(title, "title");
            Objects.requireNonNull(text, "text");
            Objects.requireNonNull(yes, "yes");
            Objects.requireNonNull(yesTip, "yesTip");
            Objects.requireNonNull(no, "no");
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
        ask(question, List.of(), onConfirm);
    }

    /**
     * Asks with figures in the text, and runs {@code onConfirm} only if the person confirms.
     *
     * @param question what is asked
     * @param textArgs the arguments of the question's text, in its order ("74 of 142 segments are done")
     * @param onConfirm what the confirming button does, after the card has closed
     */
    public void ask(final Question question, final List<Object> textArgs, final Runnable onConfirm) {
        ask(question, List.of(), textArgs, onConfirm);
    }

    /**
     * Asks with figures in the heading and the text, and runs {@code onConfirm} only if the person confirms.
     *
     * @param question what is asked
     * @param titleArgs the arguments of the question's heading, in its order (the project's name)
     * @param textArgs the arguments of the question's text, in its order
     * @param onConfirm what the confirming button does, after the card has closed
     */
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
        final Label text = new Label(messages.get(question.text(), textArgs.toArray()));
        text.setWrapText(true);
        text.getStyleClass().add("dialog-text");
        final DialogPane card = new ModalCard(ModalCard.Size.DIALOG);
        card.setId(CARD_ID);
        card.getStyleClass().addAll("dialog-card", "elevation-lg");
        final Label title = new Label(messages.get(question.title(), titleArgs.toArray()));
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
        final ButtonType cancelType = new ButtonType(messages.get(question.no()), ButtonBar.ButtonData.CANCEL_CLOSE);
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
