package ua.bookloom.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.TraversalDirection;
import javafx.scene.control.Button;
import javafx.scene.control.TextArea;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.ui.dialog.ModalCard;

/**
 * Enter, Escape and Tab the same way on every card, whichever layer shows it: Enter runs the card's enabled default
 * button (Ctrl or Command with Enter inside a text area, where plain Enter adds a line), Escape runs the card's own
 * escape action or else the layer's, and Tab walks the card's controls and wraps. FX thread only.
 */
// Checkstyle's HideUtilityClassConstructor parses source text before Lombok's annotation processor runs, so it
// cannot see the private constructor @NoArgsConstructor generates (ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class CardKeys {

    /**
     * Answers one key press aimed at a shown card; any other key is left alone.
     *
     * @param card the card shown
     * @param scene the scene it is shown in
     * @param event the key press
     * @param otherwise what Escape does when the card has no escape action of its own
     */
    static void handle(final Node card, final Scene scene, final KeyEvent event, final Runnable otherwise) {
        Objects.requireNonNull(otherwise, "otherwise");
        if (event.getCode() == KeyCode.ESCAPE) {
            event.consume();
            final Runnable own = card instanceof ModalCard modal ? modal.onEscape() : null;
            log.debug("Escape on card {} runs {}", card.getId(), own == null ? "the layer's action" : "its own action");
            (own == null ? otherwise : own).run();
        } else if (event.getCode() == KeyCode.ENTER) {
            enter(card, event);
        } else if (event.getCode() == KeyCode.TAB) {
            event.consume();
            moveFocus(card, scene, event.isShiftDown());
        }
    }

    private static void enter(final Node card, final KeyEvent event) {
        final boolean inTextArea = event.getTarget() instanceof TextArea;
        if (inTextArea && !(event.isControlDown() || event.isMetaDown())) {
            log.debug("Enter left to the text area: it adds a line");
            return;
        }
        final Button main = defaultButton(card);
        if (main == null) {
            log.debug("Enter ignored: the card has no enabled default button");
            return;
        }
        event.consume();
        log.debug("Enter runs the default button {}", main.getId());
        main.fire();
    }

    private static @Nullable Button defaultButton(final Node node) {
        if (node instanceof Button button && button.isDefaultButton() && !button.isDisabled()) {
            return button;
        }
        if (node instanceof Parent parent) {
            for (final Node child : parent.getChildrenUnmodifiable()) {
                final Button found = defaultButton(child);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static void moveFocus(final Node card, final Scene scene, final boolean backwards) {
        final List<Node> stops = stopsIn(card);
        if (stops.isEmpty()) {
            return;
        }
        final int at = stops.indexOf(scene.getFocusOwner());
        final int step = backwards ? -1 : 1;
        final int next = at < 0 ? 0 : Math.floorMod(at + step, stops.size());
        focusFromKeyboard(stops, next);
    }

    // A focus moved by requestFocus() counts as a click and shows no ring; moved by traversal it shows one. So the
    // target is reached by traversing from its neighbour inside the card: forwards from the stop before it, or, for the
    // first stop (which has no stop before it inside the card), backwards from the stop after it.
    private static void focusFromKeyboard(final List<Node> stops, final int target) {
        final Node wanted = stops.get(target);
        if (stops.size() > 1) {
            final boolean fromBefore = target > 0;
            final Node neighbour = stops.get(fromBefore ? target - 1 : target + 1);
            neighbour.requestFocusTraversal(fromBefore ? TraversalDirection.NEXT : TraversalDirection.PREVIOUS);
        }
        if (wanted.getScene() != null && !wanted.isFocused()) {
            log.debug("traversal did not land on stop {} of the card; focusing it directly", target);
            wanted.requestFocus();
        }
    }

    /**
     * Puts the focus on the card's first control, or on the card itself when it has none.
     *
     * @param card the card shown
     */
    static void focusFirstIn(final Node card) {
        final List<Node> stops = stopsIn(card);
        if (stops.isEmpty()) {
            card.setFocusTraversable(true);
            card.requestFocus();
        } else {
            stops.get(0).requestFocus();
        }
    }

    /**
     * Whether a key event is aimed at a node inside the card.
     *
     * @param card the card shown
     * @param target the event's target
     * @return {@code true} when the target is the card or inside it, {@code false} otherwise
     */
    static boolean isInside(final Node card, final @Nullable Object target) {
        Node node = target instanceof Node shown ? shown : null;
        while (node != null && !node.equals(card)) {
            node = node.getParent();
        }
        return node != null;
    }

    private static List<Node> stopsIn(final Node card) {
        final List<Node> stops = new ArrayList<>();
        collectStops(card, stops);
        return stops;
    }

    private static void collectStops(final Node node, final List<Node> stops) {
        if (node instanceof Parent parent) {
            for (final Node child : parent.getChildrenUnmodifiable()) {
                if (child.isFocusTraversable() && child.isVisible() && !child.isDisabled()) {
                    stops.add(child);
                }
                collectStops(child, stops);
            }
        }
    }
}
