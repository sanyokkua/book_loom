package ua.bookloom.ui;

import java.util.Objects;
import javafx.event.EventHandler;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.input.KeyEvent;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;

/**
 * What the work layer does with the keyboard while a progress dialog is shown: the dialog answers Enter, Escape and
 * Tab as every card does ({@link CardKeys}), the focus starts on its first control and never leaves it, and no key
 * reaches the window behind the scrim, where Enter or Space would press a button nobody can see. A question shown above
 * the dialog owns the keyboard. When the dialog goes, the focus returns to where it was. FX thread only.
 */
@Slf4j
final class BusyKeyboard {

    private final Node host;
    private final ModalHost modalHost;
    private final EventHandler<KeyEvent> filter = this::filterKey;
    private @Nullable Scene filteredScene;
    private @Nullable Node previousFocus;
    private @Nullable Node card;

    BusyKeyboard(final Node host, final ModalHost modalHost) {
        this.host = Objects.requireNonNull(host, "host");
        this.modalHost = Objects.requireNonNull(modalHost, "modalHost");
    }

    /**
     * Takes the keyboard for a dialog as it appears; a second call while it is held only moves the focus.
     *
     * @param shown the dialog now shown
     */
    void capture(final Node shown) {
        card = Objects.requireNonNull(shown, "shown");
        final Scene scene = host.getScene();
        if (scene != null && filteredScene == null) {
            previousFocus = scene.getFocusOwner();
            scene.addEventFilter(KeyEvent.ANY, filter);
            filteredScene = scene;
        }
        CardKeys.focusFirstIn(shown);
        log.debug("progress dialog {} holds the keyboard", shown.getId());
    }

    /** Gives the keyboard back as the dialog goes, and the focus to the node that held it before. */
    void release() {
        card = null;
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

    private void filterKey(final KeyEvent event) {
        final Node shown = card;
        final Scene scene = filteredScene;
        if (modalHost.isShowing() || shown == null || scene == null) {
            return;
        }
        if (event.getEventType() == KeyEvent.KEY_PRESSED) {
            CardKeys.handle(shown, scene, event, () -> log.debug("Escape ignored: the dialog has no escape action"));
            if (event.isConsumed()) {
                return;
            }
        }
        if (!CardKeys.isInside(shown, event.getTarget())) {
            event.consume();
        }
    }
}
