package ua.bookloom.ui;

import java.util.Objects;
import javafx.event.EventHandler;
import javafx.event.EventTarget;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;

/**
 * What the busy card does with the keyboard while it is shown: the focus moves onto its Cancel, or onto the card itself
 * when the work cannot be stopped, and no key reaches the window behind the scrim, where Enter or Space would press a
 * button nobody can see. Only the card's own Cancel takes a key; Escape does nothing and Tab never leaves the card. A
 * dialog above the card owns the keyboard. When the card goes, the focus returns to where it was. FX thread only.
 */
@Slf4j
final class BusyKeyboard {

    private final Node host;
    private final Node card;
    private final Button cancel;
    private final ModalHost modalHost;
    private final EventHandler<KeyEvent> filter = this::filterKey;
    private @Nullable Scene filteredScene;
    private @Nullable Node previousFocus;

    BusyKeyboard(final Node host, final Node card, final Button cancel, final ModalHost modalHost) {
        this.host = Objects.requireNonNull(host, "host");
        this.card = Objects.requireNonNull(card, "card");
        this.cancel = Objects.requireNonNull(cancel, "cancel");
        this.modalHost = Objects.requireNonNull(modalHost, "modalHost");
    }

    /** Takes the keyboard as the card appears; a second call while it is held does nothing but focus the card. */
    void capture() {
        final Scene scene = host.getScene();
        if (scene != null && filteredScene == null) {
            previousFocus = scene.getFocusOwner();
            scene.addEventFilter(KeyEvent.ANY, filter);
            filteredScene = scene;
        }
        focusCard();
    }

    /** Gives the keyboard back as the card goes, and the focus to the node that held it before. */
    void release() {
        final Scene scene = filteredScene;
        if (scene != null) {
            scene.removeEventFilter(KeyEvent.ANY, filter);
            filteredScene = null;
        }
        final Node restore = previousFocus;
        previousFocus = null;
        if (restore != null && restore.getScene() != null) {
            restore.requestFocus();
        }
    }

    private void focusCard() {
        if (cancel.isVisible() && !cancel.isDisabled()) {
            cancel.requestFocus();
        } else {
            card.requestFocus();
        }
        log.debug("busy card holds the focus on {}", cancel.isFocused() ? "Cancel" : "the card");
    }

    private void filterKey(final KeyEvent event) {
        if (modalHost.isShowing()) {
            return;
        }
        final KeyCode code = event.getCode();
        final boolean dismissing = code == KeyCode.ESCAPE || code == KeyCode.TAB;
        if (isOnCard(event.getTarget()) && !dismissing) {
            return;
        }
        event.consume();
        if (event.getEventType() == KeyEvent.KEY_PRESSED) {
            log.debug("busy card swallows {}", code);
            if (code == KeyCode.TAB) {
                focusCard();
            }
        }
    }

    private boolean isOnCard(final @Nullable EventTarget target) {
        Node node = target instanceof Node shown ? shown : null;
        while (node != null && !node.equals(card)) {
            node = node.getParent();
        }
        return node != null;
    }
}
