package ua.bookloom.ui.dialog;

import java.util.List;
import java.util.Objects;
import ua.bookloom.ui.i18n.MessageKey;

/**
 * The question asked before something that cannot be undone. An interface so a guard can be tested against a recording
 * fake instead of a scene.
 */
public interface ConfirmPrompt {

    /**
     * One question.
     *
     * @param title the card's heading
     * @param text what happens if the person confirms
     * @param yes the confirming button's label, worded for the action
     * @param yesTip the confirming button's hover explanation
     * @param no the safe button's label; Cancel unless the question itself is about cancelling
     */
    record Question(MessageKey title, MessageKey text, MessageKey yes, MessageKey yesTip, MessageKey no) {

        /** Stopping the run. */
        public static final Question STOP_RUN = new Question(
                MessageKey.CONFIRM_STOP_TITLE,
                MessageKey.CONFIRM_STOP_TEXT,
                MessageKey.CONFIRM_STOP_YES,
                MessageKey.CONFIRM_STOP_YES_TIP,
                MessageKey.CONFIRM_STOP_NO);

        /** Opening another project while a translation is at work; the text names the book and the project. */
        public static final Question SWITCH_PROJECT = new Question(
                MessageKey.CONFIRM_SWITCH_TITLE,
                MessageKey.CONFIRM_SWITCH_TEXT,
                MessageKey.CONFIRM_SWITCH_YES,
                MessageKey.CONFIRM_SWITCH_YES_TIP);

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

        /** Deleting a provider and its per-model settings; the title and the text name the provider. */
        public static final Question DELETE_PROVIDER = new Question(
                MessageKey.CONFIRM_DELETE_PROVIDER_TITLE,
                MessageKey.CONFIRM_DELETE_PROVIDER_TEXT,
                MessageKey.CONFIRM_DELETE_PROVIDER_YES,
                MessageKey.CONFIRM_DELETE_PROVIDER_YES_TIP);

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

    /**
     * Asks, and runs {@code onConfirm} only if the person confirms.
     *
     * @param question what is asked
     * @param onConfirm what the confirming button does, after the card has closed
     */
    default void ask(final Question question, final Runnable onConfirm) {
        ask(question, List.of(), onConfirm);
    }

    /**
     * Asks with figures in the text, and runs {@code onConfirm} only if the person confirms.
     *
     * @param question what is asked
     * @param textArgs the arguments of the question's text, in its order ("74 of 142 segments are done")
     * @param onConfirm what the confirming button does, after the card has closed
     */
    default void ask(final Question question, final List<Object> textArgs, final Runnable onConfirm) {
        ask(question, List.of(), textArgs, onConfirm);
    }

    /**
     * Asks with figures in the heading and the text, and runs {@code onConfirm} only if the person confirms. FX thread
     * only.
     *
     * @param question what is asked
     * @param titleArgs the arguments of the question's heading, in its order (the project's name)
     * @param textArgs the arguments of the question's text, in its order
     * @param onConfirm what the confirming button does, after the card has closed
     */
    void ask(Question question, List<Object> titleArgs, List<Object> textArgs, Runnable onConfirm);
}
