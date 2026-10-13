package ua.bookloom.ui.dialog;

import java.util.Objects;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.ui.control.Tips;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.state.RecurringTerms;

/**
 * The card that asks for one recurring term, opened by the + Add of the Recurring terms card as the Glossary's opens
 * the Add term card.
 *
 * <p>It closes as soon as a term is typed and confirmed: a term the list or the glossary already holds is refused in
 * the Names &amp; style banner, which the card would otherwise cover, and the person sees it there.
 */
@Slf4j
public final class AddRecurringTermDialog {

    static final String CARD_ID = "recurring-add-card";
    private static final double FIELD_SPACING = 4;
    private static final double CARD_WIDTH = 360;

    private final Messages messages;
    private final RecurringTerms recurring;
    private final Runnable onClose;
    private final TextField term = new TextField();

    /**
     * Prepares the card's empty field; one instance backs one opening.
     *
     * @param messages the catalogue every word is drawn from
     * @param recurring what the typed term is added through
     * @param onClose what Cancel does, and what follows a confirmed term
     */
    public AddRecurringTermDialog(final Messages messages, final RecurringTerms recurring, final Runnable onClose) {
        this.messages = Objects.requireNonNull(messages, "messages");
        this.recurring = Objects.requireNonNull(recurring, "recurring");
        this.onClose = Objects.requireNonNull(onClose, "onClose");
        term.setId("recurring-add-term");
        term.setPromptText(messages.get(MessageKey.RECURRING_ADD_PROMPT));
        Tips.install(messages, term, MessageKey.RECURRING_ADD_PROMPT_TIP);
        term.setOnAction(event -> confirm());
    }

    /**
     * The card holding the field.
     *
     * @return the card's root node, ready for {@code ModalHost.show}
     */
    public Node card() {
        final DialogPane card = new ModalCard();
        card.setId(CARD_ID);
        card.getStyleClass().addAll("dialog-card", "elevation-lg");
        card.setHeader(header());
        card.setContent(new VBox(FIELD_SPACING, term));
        buttons(card);
        card.setPrefWidth(CARD_WIDTH);
        card.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        return card;
    }

    private void confirm() {
        final String typed = term.getText().strip();
        log.debug("the recurring term card confirmed with {} characters typed", typed.length());
        if (typed.isEmpty()) {
            return;
        }
        recurring.add(typed);
        onClose.run();
    }

    private Node header() {
        final Label title = new Label(messages.get(MessageKey.DIALOG_ADD_RECURRING_TITLE));
        title.getStyleClass().add("dialog-title");
        final Label subtitle = new Label(messages.get(MessageKey.DIALOG_ADD_RECURRING_SUBTITLE));
        subtitle.getStyleClass().add("dialog-sub");
        final VBox header = new VBox(title, subtitle);
        header.getStyleClass().add("dialog-h");
        return header;
    }

    private void buttons(final DialogPane card) {
        final ButtonType cancelType =
                new ButtonType(messages.get(MessageKey.DIALOG_ADD_TERM_CANCEL), ButtonBar.ButtonData.CANCEL_CLOSE);
        final ButtonType addType =
                new ButtonType(messages.get(MessageKey.DIALOG_ADD_RECURRING_CONFIRM), ButtonBar.ButtonData.OK_DONE);
        card.getButtonTypes().setAll(cancelType, addType);
        final Button cancel = (Button) card.lookupButton(cancelType);
        cancel.setId("recurring-add-cancel");
        Tips.install(messages, cancel, MessageKey.DIALOG_ADD_TERM_CANCEL_TIP);
        cancel.getStyleClass().add("btn-ghost");
        cancel.setOnAction(event -> onClose.run());
        final Button add = (Button) card.lookupButton(addType);
        add.setId("recurring-add-confirm");
        Tips.install(messages, add, MessageKey.DIALOG_ADD_RECURRING_CONFIRM_TIP);
        add.getStyleClass().add("btn-primary");
        add.setOnAction(event -> confirm());
    }
}
