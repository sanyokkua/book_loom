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
import org.jspecify.annotations.Nullable;
import ua.bookloom.ui.ModalHost;
import ua.bookloom.ui.control.Tips;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;

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
        final DialogPane card = card(
                CARD_ID,
                messages.get(MessageKey.DIALOG_LEAVE_TITLE),
                messages.get(MessageKey.DIALOG_LEAVE_TEXT, activity));
        wire(card, stopAndLeave, leaveRunning);
        modalHost.show(card, false);
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
        final DialogPane card = card(
                WORK_CARD_ID,
                messages.get(MessageKey.DIALOG_WORK_TITLE),
                messages.get(MessageKey.DIALOG_WORK_TEXT, activity));
        final Button stay = wireWork(card, activity, continueInBackground);
        modalHost.show(card, false, true);
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
     * @param subject the name of the thing being edited, or {@code null} for the general question
     * @param discardAndLeave what drops the changes and leaves
     * @param saveAndLeave what saves the changes and then leaves
     */
    public void askUnsaved(
            final @Nullable String subject, final Runnable discardAndLeave, final Runnable saveAndLeave) {
        Objects.requireNonNull(discardAndLeave, "discardAndLeave");
        Objects.requireNonNull(saveAndLeave, "saveAndLeave");
        log.debug("asking before leaving a form with unsaved changes, named {}", subject != null);
        final DialogPane card = subject == null
                ? card(
                        UNSAVED_CARD_ID,
                        messages.get(MessageKey.DIALOG_UNSAVED_TITLE),
                        messages.get(MessageKey.DIALOG_UNSAVED_TEXT))
                : card(
                        UNSAVED_CARD_ID,
                        messages.get(MessageKey.DIALOG_UNSAVED_PROVIDER_TITLE, subject),
                        messages.get(MessageKey.DIALOG_UNSAVED_PROVIDER_TEXT));
        final ButtonType keepType =
                new ButtonType(messages.get(MessageKey.DIALOG_UNSAVED_KEEP), ButtonBar.ButtonData.CANCEL_CLOSE);
        final ButtonType discardType =
                new ButtonType(messages.get(MessageKey.DIALOG_UNSAVED_DISCARD), ButtonBar.ButtonData.OTHER);
        final ButtonType saveType =
                new ButtonType(messages.get(MessageKey.DIALOG_UNSAVED_SAVE), ButtonBar.ButtonData.OK_DONE);
        card.getButtonTypes().setAll(keepType, discardType, saveType);
        final Button keep = answer(card, keepType, UNSAVED_KEEP_ID, MessageKey.DIALOG_UNSAVED_KEEP_TIP, () -> {});
        keep.setDefaultButton(true);
        answer(card, discardType, UNSAVED_DISCARD_ID, MessageKey.DIALOG_UNSAVED_DISCARD_TIP, discardAndLeave)
                .setDefaultButton(false);
        answer(card, saveType, UNSAVED_SAVE_ID, MessageKey.DIALOG_UNSAVED_SAVE_TIP, saveAndLeave)
                .setDefaultButton(false);
        modalHost.show(card, false, true);
        keep.requestFocus();
    }

    // Each answer closes the card first, then does what it says.
    private Button answer(
            final DialogPane card, final ButtonType type, final String id, final MessageKey tip, final Runnable then) {
        final Button button = (Button) card.lookupButton(type);
        button.setId(id);
        button.getStyleClass().add("btn-secondary");
        Tips.install(messages, button, tip);
        button.setOnAction(event -> {
            log.debug("unsaved changes answered with {}", id);
            modalHost.hide();
            then.run();
        });
        return button;
    }

    private Button wireWork(final DialogPane card, final String activity, final Runnable continueInBackground) {
        final ButtonType backgroundType =
                new ButtonType(messages.get(MessageKey.DIALOG_WORK_BACKGROUND), ButtonBar.ButtonData.OTHER);
        final ButtonType stayType =
                new ButtonType(messages.get(MessageKey.DIALOG_WORK_STAY), ButtonBar.ButtonData.CANCEL_CLOSE);
        card.getButtonTypes().setAll(stayType, backgroundType);
        final Button background = (Button) card.lookupButton(backgroundType);
        background.setId(WORK_BACKGROUND_ID);
        background.getStyleClass().add("btn-secondary");
        Tips.install(messages, background, MessageKey.DIALOG_WORK_BACKGROUND_TIP);
        background.setDefaultButton(false);
        background.setOnAction(event -> {
            log.info("leaving with {} continuing in the background", activity);
            modalHost.hide();
            continueInBackground.run();
        });
        final Button stay = (Button) card.lookupButton(stayType);
        stay.setId(WORK_STAY_ID);
        stay.getStyleClass().add("btn-secondary");
        Tips.install(messages, stay, MessageKey.DIALOG_WORK_STAY_TIP);
        stay.setDefaultButton(true);
        stay.setOnAction(event -> {
            log.debug("staying with {} in view", activity);
            modalHost.hide();
        });
        return stay;
    }

    private DialogPane card(final String id, final String heading, final String body) {
        final Label text = new Label(body);
        text.setWrapText(true);
        text.getStyleClass().add("dialog-text");
        final DialogPane card = new ModalCard(ModalCard.Size.DIALOG);
        card.setId(id);
        card.getStyleClass().addAll("dialog-card", "elevation-lg");
        final Label title = new Label(heading);
        title.getStyleClass().add("dialog-title");
        final VBox header = new VBox(title);
        header.getStyleClass().add("dialog-h");
        card.setHeader(header);
        card.setContent(new VBox(text));
        card.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        return card;
    }

    private void wire(final DialogPane card, final Runnable stopAndLeave, final Runnable leaveRunning) {
        final ButtonType keepType =
                new ButtonType(messages.get(MessageKey.DIALOG_LEAVE_KEEP), ButtonBar.ButtonData.OTHER);
        final ButtonType stopType =
                new ButtonType(messages.get(MessageKey.DIALOG_LEAVE_STOP), ButtonBar.ButtonData.OK_DONE);
        card.getButtonTypes().setAll(keepType, stopType);
        final Button keep = (Button) card.lookupButton(keepType);
        keep.setId(KEEP_ID);
        Tips.install(messages, keep, MessageKey.DIALOG_LEAVE_KEEP_TIP);
        keep.getStyleClass().add("btn-secondary");
        keep.setOnAction(event -> {
            log.debug("leaving with the work still running");
            modalHost.hide();
            leaveRunning.run();
        });
        final Button stop = (Button) card.lookupButton(stopType);
        stop.setId(STOP_ID);
        Tips.install(messages, stop, MessageKey.DIALOG_LEAVE_STOP_TIP);
        stop.getStyleClass().add("btn-danger-outline");
        stop.setOnAction(event -> {
            log.debug("stopping the work and leaving");
            modalHost.hide();
            stopAndLeave.run();
        });
    }
}
