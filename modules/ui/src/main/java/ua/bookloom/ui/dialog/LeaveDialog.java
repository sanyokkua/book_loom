package ua.bookloom.ui.dialog;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Objects;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.ui.ModalHost;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.state.UnsavedSubject;

/**
 * The questions asked before leaving a screen whose model work would otherwise be left behind: for short work, stop it
 * and leave, or leave and let it run on; for long work shown in its progress dialog, "Model work is running", stay or
 * let it continue in the background; for a form with unsaved changes, keep editing, discard or save. Escape closes the
 * card, stays on the screen and changes nothing.
 */
@Slf4j
@Singleton
public final class LeaveDialog {

    static final String CARD_ID = "leave-card";
    static final String STOP_ID = "leave-stop";
    static final String KEEP_ID = "leave-keep";
    static final String WORK_CARD_ID = "work-card";
    static final String WORK_STAY_ID = "work-stay";
    static final String WORK_BACKGROUND_ID = "work-background";
    static final String UNSAVED_CARD_ID = "unsaved-card";
    static final String UNSAVED_KEEP_ID = "unsaved-keep";
    static final String UNSAVED_DISCARD_ID = "unsaved-discard";
    static final String UNSAVED_SAVE_ID = "unsaved-save";

    private final ModalHost modalHost;
    private final Messages messages;

    /**
     * Creates the dialog.
     *
     * @param modalHost the shared overlay the card is shown in
     * @param messages the catalogue every word of the card comes from
     */
    @Inject
    public LeaveDialog(final ModalHost modalHost, final Messages messages) {
        this.modalHost = Objects.requireNonNull(modalHost, "modalHost");
        this.messages = Objects.requireNonNull(messages, "messages");
    }

    /**
     * Asks before leaving. FX thread only.
     *
     * @param activity the running work, already worded
     * @param stopAndLeave what stops the work and leaves
     * @param leaveRunning what leaves and lets the work run on
     */
    public void ask(final String activity, final Runnable stopAndLeave, final Runnable leaveRunning) {
        Objects.requireNonNull(activity, "activity");
        Objects.requireNonNull(stopAndLeave, "stopAndLeave");
        Objects.requireNonNull(leaveRunning, "leaveRunning");
        log.debug("asking before leaving the screen of {}", activity);
        final FormCard card = card(
                CARD_ID,
                messages.get(MessageKey.DIALOG_LEAVE_TITLE),
                messages.get(MessageKey.DIALOG_LEAVE_TEXT, activity));
        wire(card, stopAndLeave, leaveRunning);
        card.show();
    }

    private void wire(final FormCard card, final Runnable stopAndLeave, final Runnable leaveRunning) {
        card.button(
                KEEP_ID,
                messages.get(MessageKey.DIALOG_LEAVE_KEEP),
                MessageKey.DIALOG_LEAVE_KEEP_TIP,
                ButtonBar.ButtonData.OTHER,
                "btn-secondary",
                () -> {
                    log.debug("leaving with the work still running");
                    leaveRunning.run();
                });
        card.button(
                STOP_ID,
                messages.get(MessageKey.DIALOG_LEAVE_STOP),
                MessageKey.DIALOG_LEAVE_STOP_TIP,
                ButtonBar.ButtonData.OK_DONE,
                "btn-danger-outline",
                () -> {
                    log.debug("stopping the work and leaving");
                    stopAndLeave.run();
                });
    }

    /**
     * Asks before leaving the screen of long work whose progress dialog is shown: stay, or let it continue in the
     * background. Stay is the default and Escape stays. FX thread only.
     *
     * @param activity the running work, already worded
     * @param continueInBackground what hides the work and leaves
     */
    public void askModelWork(final String activity, final Runnable continueInBackground) {
        Objects.requireNonNull(activity, "activity");
        Objects.requireNonNull(continueInBackground, "continueInBackground");
        log.debug("asking before leaving the screen of {}, which runs shown", activity);
        final FormCard card = card(
                WORK_CARD_ID,
                messages.get(MessageKey.DIALOG_WORK_TITLE),
                messages.get(MessageKey.DIALOG_WORK_TEXT, activity));
        final Button stay = card.button(
                WORK_STAY_ID,
                messages.get(MessageKey.DIALOG_WORK_STAY),
                MessageKey.DIALOG_WORK_STAY_TIP,
                ButtonBar.ButtonData.CANCEL_CLOSE,
                "btn-secondary",
                () -> log.debug("staying with {} in view", activity));
        card.button(
                WORK_BACKGROUND_ID,
                messages.get(MessageKey.DIALOG_WORK_BACKGROUND),
                MessageKey.DIALOG_WORK_BACKGROUND_TIP,
                ButtonBar.ButtonData.OTHER,
                "btn-secondary",
                () -> {
                    log.info("leaving with {} continuing in the background", activity);
                    continueInBackground.run();
                });
        // Set once every button is in: each new one resets the default.
        stay.setDefaultButton(true);
        card.show();
        stay.requestFocus();
    }

    /**
     * Asks before leaving a screen whose form holds unsaved changes: Keep editing (the default, and what Escape does),
     * Discard, or Save. FX thread only.
     *
     * @param discardAndLeave what drops the changes and leaves
     * @param saveAndLeave what saves the changes and then leaves
     */
    public void askUnsaved(final Runnable discardAndLeave, final Runnable saveAndLeave) {
        askUnsaved(null, discardAndLeave, saveAndLeave);
    }

    /**
     * Asks before leaving a form that belongs to something with a name, and says which.
     *
     * @param subject what the changes belong to, with the words for it, or {@code null} for the general question
     * @param discardAndLeave what drops the changes and leaves
     * @param saveAndLeave what saves the changes and then leaves
     */
    public void askUnsaved(
            final @Nullable UnsavedSubject subject, final Runnable discardAndLeave, final Runnable saveAndLeave) {
        Objects.requireNonNull(discardAndLeave, "discardAndLeave");
        Objects.requireNonNull(saveAndLeave, "saveAndLeave");
        log.debug("asking before leaving a form with unsaved changes, named {}", subject != null);
        final FormCard card = subject == null
                ? card(
                        UNSAVED_CARD_ID,
                        messages.get(MessageKey.DIALOG_UNSAVED_TITLE),
                        messages.get(MessageKey.DIALOG_UNSAVED_TEXT))
                : card(UNSAVED_CARD_ID, messages.get(subject.title(), subject.name()), messages.get(subject.text()));
        final Button keep = wireUnsaved(card, discardAndLeave, saveAndLeave);
        card.show();
        keep.requestFocus();
    }

    private Button wireUnsaved(final FormCard card, final Runnable discardAndLeave, final Runnable saveAndLeave) {
        final Button keep = answer(
                card,
                UNSAVED_KEEP_ID,
                MessageKey.DIALOG_UNSAVED_KEEP,
                MessageKey.DIALOG_UNSAVED_KEEP_TIP,
                ButtonBar.ButtonData.CANCEL_CLOSE,
                () -> {});
        answer(
                card,
                UNSAVED_DISCARD_ID,
                MessageKey.DIALOG_UNSAVED_DISCARD,
                MessageKey.DIALOG_UNSAVED_DISCARD_TIP,
                ButtonBar.ButtonData.OTHER,
                discardAndLeave);
        answer(
                        card,
                        UNSAVED_SAVE_ID,
                        MessageKey.DIALOG_UNSAVED_SAVE,
                        MessageKey.DIALOG_UNSAVED_SAVE_TIP,
                        ButtonBar.ButtonData.OK_DONE,
                        saveAndLeave)
                .setDefaultButton(false);
        // Set once every button is in: each new one resets the default.
        keep.setDefaultButton(true);
        return keep;
    }

    private Button answer(
            final FormCard card,
            final String id,
            final MessageKey caption,
            final MessageKey tip,
            final ButtonBar.ButtonData data,
            final Runnable then) {
        return card.button(id, messages.get(caption), tip, data, "btn-secondary", () -> {
            log.debug("unsaved changes answered with {}", id);
            then.run();
        });
    }

    private FormCard card(final String id, final String heading, final String body) {
        final FormCard card = new FormCard(modalHost, messages, id, ModalCard.Size.DIALOG, heading);
        card.text(body);
        return card;
    }
}
