package ua.bookloom.ui.dialog;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Objects;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.ui.ModalHost;
import ua.bookloom.ui.control.Tips;
import ua.bookloom.ui.control.Wrapped;
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
        final VBox body = new VBox(Wrapped.label(
                messages.get(MessageKey.DIALOG_OPEN_ANOTHER_BODY, openProjectName, newFileName), "dialog-text"));
        if (state == RunState.RUNNING) {
            body.getChildren().add(Wrapped.label(messages.get(MessageKey.DIALOG_OPEN_ANOTHER_RUNNING), "dialog-sub"));
        }
        final FormCard card = new FormCard(
                modalHost,
                messages,
                CARD_ID,
                ModalCard.Size.DIALOG,
                messages.get(MessageKey.DIALOG_OPEN_ANOTHER_TITLE));
        card.card().setContent(body);
        wire(card, body, state == RunState.RUNNING, onOpenNew, onReplace);
        shown = card.card();
        card.show();
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

    private void wire(
            final FormCard card,
            final VBox body,
            final boolean isRunning,
            final Runnable onOpenNew,
            final Runnable onReplace) {
        final Button cancel = card.button(
                CANCEL_ID,
                messages.get(MessageKey.DIALOG_OPEN_ANOTHER_CANCEL),
                MessageKey.DIALOG_OPEN_ANOTHER_CANCEL_TIP,
                ButtonBar.ButtonData.CANCEL_CLOSE,
                "btn-secondary",
                () -> log.debug("the open-another-book question was cancelled"));
        // An answer hides the card itself, or keeps it while a running run stops.
        final Button replace = Tips.install(
                messages,
                card.card()
                        .button(
                                messages.get(MessageKey.DIALOG_OPEN_ANOTHER_REPLACE),
                                ButtonBar.ButtonData.OTHER,
                                REPLACE_ID,
                                "btn-danger-outline"),
                MessageKey.DIALOG_OPEN_ANOTHER_REPLACE_TIP);
        final Button openNew = Tips.install(
                messages,
                card.card()
                        .button(
                                messages.get(MessageKey.DIALOG_OPEN_ANOTHER_NEW),
                                ButtonBar.ButtonData.OK_DONE,
                                NEW_ID,
                                "btn-primary"),
                MessageKey.DIALOG_OPEN_ANOTHER_NEW_TIP);
        final Button[] all = {cancel, replace, openNew};
        replace.setOnAction(event -> answered("replace", isRunning, body, all, onReplace));
        openNew.setOnAction(event -> answered("open as a new project", isRunning, body, all, onOpenNew));
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
