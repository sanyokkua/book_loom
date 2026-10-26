package ua.bookloom.ui.dialog;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Objects;
import javafx.scene.control.Label;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.pipeline.ProjectOpening;
import ua.bookloom.ui.ModalHost;
import ua.bookloom.ui.control.Wrapped;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.state.ProjectRow;

/** The {@link FindBookPrompt} shown as a card in the shell's modal host; Cancel leaves the project closed. */
@Slf4j
@Singleton
public final class FindBookDialog implements FindBookPrompt {

    private static final String PREFIX = "findbook-";

    private final ModalHost modalHost;
    private final Messages messages;

    /**
     * Creates the dialog.
     *
     * @param modalHost the shared overlay the card is shown in
     * @param messages the catalogue every word of the card comes from
     */
    @Inject
    public FindBookDialog(final ModalHost modalHost, final Messages messages) {
        this.modalHost = Objects.requireNonNull(modalHost, "modalHost");
        this.messages = Objects.requireNonNull(messages, "messages");
    }

    @Override
    public void ask(
            final ProjectRow row,
            final ProjectOpening opening,
            final @Nullable String error,
            final Runnable choose,
            final Runnable cancelled) {
        Objects.requireNonNull(row, "row");
        Objects.requireNonNull(opening, "opening");
        Objects.requireNonNull(choose, "choose");
        Objects.requireNonNull(cancelled, "cancelled");
        log.debug(
                "asking to find the book of project {}: {}, error shown: {}",
                row.summary().project().id(),
                opening.getClass().getSimpleName(),
                error != null);
        final FormCard card = new FormCard(
                modalHost, messages, PREFIX, ModalCard.Size.DIALOG, messages.get(MessageKey.DIALOG_FIND_BOOK_TITLE));
        final MessageKey body = opening instanceof ProjectOpening.BookFileChanged
                ? MessageKey.DIALOG_FIND_BOOK_CHANGED
                : MessageKey.DIALOG_FIND_BOOK_MISSING;
        addButtons(card, choose, cancelled);
        card.onEscape(cancelled);
        card.body(Wrapped.label(messages.get(body, row.summary().project().name()), "dialog-text"), errorLine(error));
        card.show();
    }

    private void addButtons(final FormCard card, final Runnable choose, final Runnable cancelled) {
        card.button(
                PREFIX + "cancel",
                messages.get(MessageKey.COMMON_CANCEL),
                MessageKey.COMMON_CANCEL_TIP,
                FormCard.Kind.SECONDARY,
                cancelled);
        card.button(
                PREFIX + "choose",
                messages.get(MessageKey.DIALOG_FIND_BOOK_CHOOSE),
                MessageKey.DIALOG_FIND_BOOK_CHOOSE_TIP,
                FormCard.Kind.PRIMARY,
                choose);
    }

    private static Label errorLine(final @Nullable String error) {
        final Label problem = Wrapped.label(error == null ? "" : error, "dialog-error");
        problem.setId(PREFIX + "error");
        problem.setVisible(error != null);
        problem.setManaged(error != null);
        return problem;
    }
}
