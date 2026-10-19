package ua.bookloom.ui.dialog;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import java.util.Objects;
import javafx.collections.FXCollections;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.ui.ModalHost;
import ua.bookloom.ui.control.Tips;
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
        final Label text = new Label(messages.get(MessageKey.DIALOG_NO_TARGET_TEXT, terms.size()));
        text.setWrapText(true);
        text.getStyleClass().add("dialog-text");
        final DialogPane card = card(new VBox(LIST_PADDING, text, list(terms)));
        wire(card, startAnyway);
        modalHost.show(card, false);
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

    private DialogPane card(final VBox body) {
        final DialogPane card = new ModalCard();
        card.setId(CARD_ID);
        card.getStyleClass().addAll("dialog-card", "elevation-lg");
        final Label title = new Label(messages.get(MessageKey.DIALOG_NO_TARGET_TITLE));
        title.getStyleClass().add("dialog-title");
        final VBox header = new VBox(title);
        header.getStyleClass().add("dialog-h");
        card.setHeader(header);
        card.setContent(body);
        card.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        return card;
    }

    private void wire(final DialogPane card, final Runnable startAnyway) {
        final ButtonType reviewType =
                new ButtonType(messages.get(MessageKey.DIALOG_NO_TARGET_REVIEW), ButtonBar.ButtonData.CANCEL_CLOSE);
        final ButtonType startType =
                new ButtonType(messages.get(MessageKey.DIALOG_NO_TARGET_START), ButtonBar.ButtonData.OK_DONE);
        card.getButtonTypes().setAll(reviewType, startType);
        final Button review = (Button) card.lookupButton(reviewType);
        review.setId(REVIEW_ID);
        Tips.install(messages, review, MessageKey.DIALOG_NO_TARGET_REVIEW_TIP);
        review.getStyleClass().add("btn-secondary");
        review.setOnAction(event -> {
            log.debug("going back to the names");
            modalHost.hide();
        });
        final Button start = (Button) card.lookupButton(startType);
        start.setId(START_ID);
        Tips.install(messages, start, MessageKey.DIALOG_NO_TARGET_START_TIP);
        start.getStyleClass().add("btn-primary");
        start.setOnAction(event -> {
            log.debug("starting with entries that have no target");
            modalHost.hide();
            startAnyway.run();
        });
    }
}
