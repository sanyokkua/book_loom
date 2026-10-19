package ua.bookloom.ui.screen;

import java.util.Objects;
import javafx.beans.value.ObservableBooleanValue;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;

/**
 * The pieces every card of the brief is put together from: the card itself, a labelled field and a hint. The export
 * screen builds its cards from the same pieces.
 */
// Checkstyle's HideUtilityClassConstructor parses source text before Lombok's annotation processor runs, so it
// cannot see the private constructor @NoArgsConstructor generates (ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class BriefCards {

    private static final double CARD_SPACING = 12;
    private static final double FIELD_SPACING = 5;

    static VBox card(final String id, final Messages messages, final MessageKey title, final Node... body) {
        return card(id, messages, title, false, body);
    }

    /** A card with its heading, and the tag that says nothing reads it yet when {@code unused}. */
    static VBox card(
            final String id,
            final Messages messages,
            final MessageKey title,
            final boolean unused,
            final Node... body) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(messages, "messages");
        Objects.requireNonNull(title, "title");
        final Label heading = new Label(messages.get(title));
        heading.getStyleClass().add("card-title");
        final Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        final HBox header = new HBox(heading, spacer);
        header.setAlignment(Pos.CENTER_LEFT);
        if (unused) {
            final Label tag = new Label(messages.get(MessageKey.BRIEF_SOON));
            tag.getStyleClass().add("tag-note");
            header.getChildren().add(tag);
        }
        final VBox card = new VBox(CARD_SPACING, header);
        card.getChildren().addAll(body);
        card.setId(id);
        card.getStyleClass().add("card");
        return card;
    }

    /** A label above the control it names, with an optional hint beneath. */
    static VBox field(final Messages messages, final MessageKey label, final Node control, final MessageKey hint) {
        final VBox field = field(messages, label, control);
        field.getChildren().add(hint(messages, hint));
        return field;
    }

    static VBox field(final Messages messages, final MessageKey label, final Node control) {
        final Label name = new Label(messages.get(label));
        name.getStyleClass().add("muted");
        return new VBox(FIELD_SPACING, name, control);
    }

    static Label hint(final Messages messages, final MessageKey key) {
        final Label hint = new Label(messages.get(key));
        hint.getStyleClass().add("hint");
        hint.setWrapText(true);
        return hint;
    }

    /** Shows the node only while {@code shown} says so, and takes no room otherwise. */
    static <T extends Node> T shownWhile(final T node, final ObservableBooleanValue shown) {
        node.visibleProperty().bind(shown);
        node.managedProperty().bind(node.visibleProperty());
        return node;
    }
}
