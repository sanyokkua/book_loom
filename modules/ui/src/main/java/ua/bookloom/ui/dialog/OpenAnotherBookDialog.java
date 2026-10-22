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
 * The {@link OpenAnotherBookPrompt} shown as a card in the shell's modal host.
 *
 * <p>"Open as a new project" is the default because it deletes nothing; "Replace" is the destructive outline button.
 * Escape and Cancel close the card without an answer. While the run is still going an answer keeps the card open with
 * "Stopping the run…" and every button disabled until the guard dismisses it, so a half-finished switch cannot be
 * acted on twice.
 */
@Slf4j
@Singleton
public final class OpenAnotherBookDialog implements OpenAnotherBookPrompt {

    static final String CARD_ID = "open-another-card";
    static final String NEW_ID = "open-another-new";
    static final String REPLACE_ID = "open-another-replace";
    static final String CANCEL_ID = "open-another-cancel";
    static final String STOPPING_ID = "open-another-stopping";

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
    public OpenAnotherBookDialog(final ModalHost modalHost, final Messages messages) {
        this.modalHost = Objects.requireNonNull(modalHost, "modalHost");
        this.messages = Objects.requireNonNull(messages, "messages");
    }

    @Override
    public void ask(
            final String openProjectName,
            final String newFileName,
            final RunState state,
            final Runnable onOpenNew,
            final Runnable onReplace) {
        Objects.requireNonNull(openProjectName, "openProjectName");
        Objects.requireNonNull(newFileName, "newFileName");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(onOpenNew, "onOpenNew");
        Objects.requireNonNull(onReplace, "onReplace");
        log.debug("asking about opening {}, run {}", newFileName, state);
        log.trace("the open project is named {}", openProjectName);
        final Label text = new Label(messages.get(MessageKey.DIALOG_OPEN_ANOTHER_BODY, openProjectName, newFileName));
        text.setWrapText(true);
        text.getStyleClass().add("dialog-text");
        final VBox body = new VBox(text);
        if (state == RunState.RUNNING) {
            final Label running = new Label(messages.get(MessageKey.DIALOG_OPEN_ANOTHER_RUNNING));
            running.setWrapText(true);
            running.getStyleClass().add("dialog-sub");
            body.getChildren().add(running);
        }
        final DialogPane card = card(body);
        wire(card, body, state == RunState.RUNNING, onOpenNew, onReplace);
        shown = card;
        modalHost.show(card, false, true);
    }

    @Override
    public void dismiss() {
        final Node card = shown;
        shown = null;
        if (card != null && card.getScene() != null) {
            log.debug("closing the open-another-book question");
            modalHost.hide();
        } else {
            log.debug("dismiss ignored: the open-another-book question is no longer shown");
        }
    }

    private DialogPane card(final Node body) {
        final DialogPane card = new ModalCard(ModalCard.Size.DIALOG);
        card.setId(CARD_ID);
        card.getStyleClass().addAll("dialog-card", "elevation-lg");
        final Label title = new Label(messages.get(MessageKey.DIALOG_OPEN_ANOTHER_TITLE));
        title.getStyleClass().add("dialog-title");
        final VBox header = new VBox(title);
        header.getStyleClass().add("dialog-h");
        card.setHeader(header);
        card.setContent(body);
        card.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        return card;
    }

    private void wire(
            final DialogPane card,
            final VBox body,
            final boolean isRunning,
            final Runnable onOpenNew,
            final Runnable onReplace) {
        final ButtonType cancelType =
                new ButtonType(messages.get(MessageKey.DIALOG_OPEN_ANOTHER_CANCEL), ButtonBar.ButtonData.CANCEL_CLOSE);
        final ButtonType replaceType =
                new ButtonType(messages.get(MessageKey.DIALOG_OPEN_ANOTHER_REPLACE), ButtonBar.ButtonData.OTHER);
        final ButtonType newType =
                new ButtonType(messages.get(MessageKey.DIALOG_OPEN_ANOTHER_NEW), ButtonBar.ButtonData.OK_DONE);
        card.getButtonTypes().setAll(cancelType, replaceType, newType);
        final Button cancel = (Button) card.lookupButton(cancelType);
        final Button replace = (Button) card.lookupButton(replaceType);
        final Button openNew = (Button) card.lookupButton(newType);
        style(cancel, CANCEL_ID, MessageKey.DIALOG_OPEN_ANOTHER_CANCEL_TIP, "btn-secondary");
        style(replace, REPLACE_ID, MessageKey.DIALOG_OPEN_ANOTHER_REPLACE_TIP, "btn-danger-outline");
        style(openNew, NEW_ID, MessageKey.DIALOG_OPEN_ANOTHER_NEW_TIP, "btn-primary");
        cancel.setOnAction(event -> {
            log.debug("the open-another-book question was cancelled");
            modalHost.hide();
        });
        final Button[] all = {cancel, replace, openNew};
        replace.setOnAction(event -> answered("replace", isRunning, body, all, onReplace));
        openNew.setOnAction(event -> answered("open as a new project", isRunning, body, all, onOpenNew));
    }

    private void style(final Button button, final String id, final MessageKey tip, final String styleClass) {
        button.setId(id);
        Tips.install(messages, button, tip);
        button.getStyleClass().add(styleClass);
    }

    // A running run is stopped before the answer takes effect, so the card stays until the guard dismisses it.
    private void answered(
            final String answer,
            final boolean isRunning,
            final VBox body,
            final Button[] buttons,
            final Runnable action) {
        log.debug("the open-another-book question was answered with {}, run still going: {}", answer, isRunning);
        if (isRunning) {
            for (final Button button : buttons) {
                button.setDisable(true);
            }
            final Label stopping = new Label(messages.get(MessageKey.DIALOG_OPEN_ANOTHER_STOPPING));
            stopping.setId(STOPPING_ID);
            stopping.getStyleClass().add("dialog-sub");
            body.getChildren().add(stopping);
        } else {
            modalHost.hide();
        }
        action.run();
    }
}
