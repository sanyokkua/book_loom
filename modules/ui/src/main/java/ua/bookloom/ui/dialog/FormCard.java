package ua.bookloom.ui.dialog;

import java.util.Objects;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import ua.bookloom.ui.ModalHost;
import ua.bookloom.ui.control.Tips;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;

/**
 * The shape the project cards share: a heading, a body, and a footer whose every button hides the card before it runs
 * its action, so the answer is never run under a card that is still shown. FX thread only.
 */
final class FormCard {

    private static final double BODY_SPACING = 12;

    /** How a footer button looks and where the footer puts it. */
    enum Kind {
        /** The confirming button. */
        PRIMARY(ButtonBar.ButtonData.OK_DONE, "btn-primary"),
        /** A button that leaves the card unanswered. */
        SECONDARY(ButtonBar.ButtonData.CANCEL_CLOSE, "btn-secondary"),
        /** A button of the footer's left side that leads elsewhere. */
        LEFT(ButtonBar.ButtonData.LEFT, "btn-ghost");

        private final ButtonBar.ButtonData data;
        private final String style;

        Kind(final ButtonBar.ButtonData data, final String style) {
            this.data = data;
            this.style = style;
        }
    }

    private final ModalHost host;
    private final Messages messages;
    private final ModalCard card;

    FormCard(
            final ModalHost host,
            final Messages messages,
            final String idPrefix,
            final ModalCard.Size size,
            final String title) {
        this.host = Objects.requireNonNull(host, "host");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.card = new ModalCard(size);
        card.setId(idPrefix + "card");
        card.getStyleClass().addAll("dialog-card", "elevation-lg");
        final Label heading = new Label(title);
        heading.getStyleClass().add("dialog-title");
        heading.setWrapText(true);
        final VBox header = new VBox(heading);
        header.getStyleClass().add("dialog-h");
        card.setHeader(header);
        card.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
    }

    void body(final Node... nodes) {
        card.setContent(new VBox(BODY_SPACING, nodes));
    }

    Button button(final String id, final String caption, final MessageKey tip, final Kind kind, final Runnable action) {
        final ButtonType type = new ButtonType(caption, kind.data);
        card.getButtonTypes().add(type);
        final Button button = (Button) card.lookupButton(type);
        button.setId(id);
        button.getStyleClass().add(kind.style);
        Tips.install(messages, button, tip);
        button.setOnAction(event -> {
            host.hide();
            action.run();
        });
        return button;
    }

    void onEscape(final Runnable cancelled) {
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

    void show() {
        host.show(card, false, true);
    }
}
