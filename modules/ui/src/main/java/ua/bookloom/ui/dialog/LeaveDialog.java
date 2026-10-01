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
 * The question asked before leaving a screen whose model work would otherwise be left behind: stop it and leave, or
 * leave and let it run on. Escape, which the host guarantees for every card, stays on the screen and changes nothing.
 */
@Slf4j
@Singleton
public final class LeaveDialog {

    static final String CARD_ID = "leave-card";
    static final String STOP_ID = "leave-stop";
    static final String KEEP_ID = "leave-keep";

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
        final Label text = new Label(messages.get(MessageKey.DIALOG_LEAVE_TEXT, activity));
        text.setWrapText(true);
        text.getStyleClass().add("dialog-text");
        final DialogPane card = new ModalCard();
        card.setId(CARD_ID);
        card.getStyleClass().addAll("dialog-card", "elevation-lg");
        final Label title = new Label(messages.get(MessageKey.DIALOG_LEAVE_TITLE));
        title.getStyleClass().add("dialog-title");
        final VBox header = new VBox(title);
        header.getStyleClass().add("dialog-h");
        card.setHeader(header);
        card.setContent(new VBox(text));
        card.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        wire(card, stopAndLeave, leaveRunning);
        modalHost.show(card, false);
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
