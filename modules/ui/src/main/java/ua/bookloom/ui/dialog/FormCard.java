package ua.bookloom.ui.dialog;

import java.util.Objects;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import ua.bookloom.ui.ModalHost;
import ua.bookloom.ui.control.Tips;
import ua.bookloom.ui.control.Wrapped;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;

/**
 * The card every question shares: a heading, a body, and a footer whose every button hides the card before it runs
 * its action, so the answer is never run under a card that is still shown. FX thread only.
 */
public final class FormCard {

    private static final double BODY_SPACING = 12;

    private final ModalHost host;
    private final Messages messages;
    private final ModalCard card;

    /**
     * Builds the card with its heading.
     *
     * @param host the shared overlay the card is shown in and hidden from
     * @param messages the catalogue the buttons' hover explanations come from
     * @param cardId the card's node id
     * @param size the card's width class
     * @param title the heading's words; a long heading wraps
     */
    public FormCard(
            final ModalHost host,
            final Messages messages,
            final String cardId,
            final ModalCard.Size size,
            final String title) {
        this.host = Objects.requireNonNull(host, "host");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.card = new ModalCard(size);
        card.setId(Objects.requireNonNull(cardId, "cardId"));
        final Label heading = ModalCard.title(title);
        heading.setWrapText(true);
        card.header(heading);
    }

    /**
     * The card itself, for what only a few questions need (Escape firing a button, waiting for a free window).
     *
     * @return the card
     */
    public ModalCard card() {
        return card;
    }

    /**
     * Sets the body: the nodes stacked with the cards' usual gap.
     *
     * @param nodes the body's parts, in order
     */
    public void body(final Node... nodes) {
        card.setContent(new VBox(BODY_SPACING, nodes));
    }

    /**
     * Sets a body that is one paragraph, as a question's is.
     *
     * @param text the paragraph
     */
    public void text(final String text) {
        card.setContent(new VBox(Wrapped.label(text, "dialog-text")));
    }

    /**
     * Adds a footer button that hides the card, then runs its action.
     *
     * @param id the button's node id
     * @param caption the button's words
     * @param tip its hover explanation
     * @param data where the footer puts it
     * @param styleClass how it looks
     * @param action what it does once the card is hidden
     * @return the button, for a card that makes it the default or focuses it
     */
    public Button button(
            final String id,
            final String caption,
            final MessageKey tip,
            final ButtonBar.ButtonData data,
            final String styleClass,
            final Runnable action) {
        Objects.requireNonNull(action, "action");
        final Button button = Tips.install(messages, card.button(caption, data, id, styleClass), tip);
        button.setOnAction(event -> {
            host.hide();
            action.run();
        });
        return button;
    }

    /**
     * Makes Escape hide the card and run {@code cancelled}.
     *
     * @param cancelled what an unanswered card does
     */
    public void onEscape(final Runnable cancelled) {
        card.setOnEscape(() -> {
            host.hide();
            cancelled.run();
        });
    }

    // The body scrolls, and a scroll pane puts its content into the scene only once its skin is built, so the field is
    // not in the scene yet when the card is shown: focus it when it arrives.
    static void focusWhenShown(final TextField field, final boolean selectAll) {
        final Runnable focus = () -> {
            field.requestFocus();
            if (selectAll) {
                field.selectAll();
            }
        };
        if (field.getScene() != null) {
            focus.run();
            return;
        }
        field.sceneProperty().addListener(new ChangeListener<Scene>() {
            @Override
            public void changed(final ObservableValue<? extends Scene> observable, final Scene old, final Scene now) {
                if (now != null) {
                    field.sceneProperty().removeListener(this);
                    focus.run();
                }
            }
        });
    }

    /** Shows the card; Escape closes it, a click outside does not. */
    public void show() {
        host.show(card, false, true);
    }
}
