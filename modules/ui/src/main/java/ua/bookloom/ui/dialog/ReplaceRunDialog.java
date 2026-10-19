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
import ua.bookloom.ui.control.Tips;
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
    static final String EARLIER_CARD_ID = "earlier-work-card";
    static final String CONTINUE_ID = "earlier-work-continue";
    static final String START_OVER_ID = "earlier-work-start-over";

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
        // Must be answered: closing it unanswered, or while the run is stopping, would leave the import waiting.
        modalHost.show(card, false, false);
    }

    @Override
    public void askEarlierWork(
            final String fileName, final int decidedSegments, final Runnable onContinue, final Runnable onStartOver) {
        Objects.requireNonNull(fileName, "fileName");
        Objects.requireNonNull(onContinue, "onContinue");
        Objects.requireNonNull(onStartOver, "onStartOver");
        log.debug(
                "asking whether to continue the earlier translation of {}: {} decided segment(s)",
                fileName,
                decidedSegments);
        final DialogPane card = earlierCard(fileName, decidedSegments, onContinue, onStartOver);
        shown = card;
        // Escape closes the card and runs neither action, so backing out never discards or opens anything.
        modalHost.show(card, false, true);
    }

    private DialogPane earlierCard(
            final String fileName, final int decidedSegments, final Runnable onContinue, final Runnable onStartOver) {
        final Label text = new Label(messages.get(MessageKey.DIALOG_EARLIER_WORK_BODY, fileName, decidedSegments));
        text.setWrapText(true);
        text.getStyleClass().add("dialog-text");
        final DialogPane card = card(new VBox(text), MessageKey.DIALOG_EARLIER_WORK_TITLE, EARLIER_CARD_ID);
        final ButtonType startOverType =
                new ButtonType(messages.get(MessageKey.DIALOG_EARLIER_WORK_START_OVER), ButtonBar.ButtonData.OTHER);
        final ButtonType continueType =
                new ButtonType(messages.get(MessageKey.DIALOG_EARLIER_WORK_CONTINUE), ButtonBar.ButtonData.OK_DONE);
        card.getButtonTypes().setAll(startOverType, continueType);
        answer(
                (Button) card.lookupButton(startOverType),
                START_OVER_ID,
                MessageKey.DIALOG_EARLIER_WORK_START_OVER_TIP,
                "btn-danger-outline",
                onStartOver);
        answer(
                (Button) card.lookupButton(continueType),
                CONTINUE_ID,
                MessageKey.DIALOG_EARLIER_WORK_CONTINUE_TIP,
                "btn-primary",
                onContinue);
        return card;
    }

    private void answer(
            final Button button,
            final String id,
            final MessageKey tip,
            final String styleClass,
            final Runnable action) {
        button.setId(id);
        Tips.install(messages, button, tip);
        button.getStyleClass().add(styleClass);
        button.setOnAction(event -> {
            log.debug("earlier-translation question answered with {}", id);
            modalHost.hide();
            action.run();
        });
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
        return card(body, MessageKey.DIALOG_REPLACE_RUN_TITLE, CARD_ID);
    }

    private DialogPane card(final Node body, final MessageKey titleKey, final String id) {
        final DialogPane card = new ModalCard();
        card.setId(id);
        card.getStyleClass().addAll("dialog-card", "elevation-lg");
        final Label title = new Label(messages.get(titleKey));
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
        Tips.install(messages, keep, MessageKey.DIALOG_REPLACE_RUN_KEEP_TIP);
        keep.getStyleClass().add("btn-secondary");
        keep.setOnAction(event -> {
            log.debug("the run is kept");
            modalHost.hide();
        });
        confirm.setId(CONFIRM_ID);
        Tips.install(messages, confirm, MessageKey.DIALOG_REPLACE_RUN_CONFIRM_TIP);
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
