package ua.bookloom.ui.dialog;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import java.util.Objects;
import javafx.collections.FXCollections;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ListView;
import javafx.scene.layout.VBox;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.ui.ModalHost;
import ua.bookloom.ui.control.Wrapped;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;

/**
 * The question asked before Start translation when glossary entries have no target: start anyway and let the model
 * choose their spelling, or go back to the names. Escape, which the host guarantees for every card, goes back.
 */
@Slf4j
@Singleton
public final class NoTargetDialog {

    static final String CARD_ID = "no-target-card";
    static final String LIST_ID = "no-target-list";
    static final String REVIEW_ID = "no-target-review";
    static final String START_ID = "no-target-start";

    private static final int VISIBLE_ROWS = 8;
    private static final double ROW_HEIGHT = 28;
    private static final double LIST_PADDING = 4;
    private static final double LIST_WIDTH = 360;

    private final ModalHost modalHost;
    private final Messages messages;

    /**
     * Creates the dialog.
     *
     * @param modalHost the shared overlay the card is shown in
     * @param messages the catalogue every word of the card comes from
     */
    @Inject
    public NoTargetDialog(final ModalHost modalHost, final Messages messages) {
        this.modalHost = Objects.requireNonNull(modalHost, "modalHost");
        this.messages = Objects.requireNonNull(messages, "messages");
    }

    /**
     * Asks whether to start with entries that have no target. FX thread only.
     *
     * @param terms the terms of the entries without a target, in table order; never empty
     * @param startAnyway what starts the translation; not run when the person goes back to the names
     */
    public void ask(final List<String> terms, final Runnable startAnyway) {
        Objects.requireNonNull(terms, "terms");
        Objects.requireNonNull(startAnyway, "startAnyway");
        log.debug("asking before starting with {} entries that have no target", terms.size());
        log.trace("entries without a target: {}", terms);
        final FormCard card = new FormCard(
                modalHost, messages, CARD_ID, ModalCard.Size.DIALOG, messages.get(MessageKey.DIALOG_NO_TARGET_TITLE));
        card.card()
                .setContent(new VBox(
                        LIST_PADDING,
                        Wrapped.label(messages.get(MessageKey.DIALOG_NO_TARGET_TEXT, terms.size()), "dialog-text"),
                        list(terms)));
        wire(card, startAnyway);
        card.show();
    }

    private void wire(final FormCard card, final Runnable startAnyway) {
        card.button(
                REVIEW_ID,
                messages.get(MessageKey.DIALOG_NO_TARGET_REVIEW),
                MessageKey.DIALOG_NO_TARGET_REVIEW_TIP,
                ButtonBar.ButtonData.CANCEL_CLOSE,
                "btn-secondary",
                () -> log.debug("going back to the names"));
        card.button(
                START_ID,
                messages.get(MessageKey.DIALOG_NO_TARGET_START),
                MessageKey.DIALOG_NO_TARGET_START_TIP,
                ButtonBar.ButtonData.OK_DONE,
                "btn-primary",
                () -> {
                    log.debug("starting with entries that have no target");
                    startAnyway.run();
                });
    }

    private ListView<String> list(final List<String> terms) {
        final ListView<String> list = new ListView<>(FXCollections.observableArrayList(terms));
        list.setId(LIST_ID);
        list.setFixedCellSize(ROW_HEIGHT);
        list.setPrefWidth(LIST_WIDTH);
        list.setPrefHeight(Math.min(terms.size(), VISIBLE_ROWS) * ROW_HEIGHT + 2);
        list.setFocusTraversable(false);
        return list;
    }
}
