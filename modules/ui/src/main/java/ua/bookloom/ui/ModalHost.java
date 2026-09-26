package ua.bookloom.ui;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javafx.event.EventHandler;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;

/**
 * The shell's overlay for one dialog at a time: a dimming layer with the dialog card centred over it.
 *
 * <p>The scrim and the card are siblings rather than parent and child so a click inside the card can never be
 * mistaken for a click on the dimmed area. While a card is shown a scene-level key filter keeps Escape working and
 * keeps Tab inside the card, wherever focus was when it opened, and the filter is removed again on hide so nothing
 * outlives the dialog. Focus goes back to whatever owned it before. Everything is FX-Application-Thread only, like
 * the scene graph it edits.
 */
@Slf4j
@Singleton
public final class ModalHost {

    static final String HOST_ID = "shell-modal-host";
    static final String SCRIM_ID = "shell-scrim";

    private final StackPane host = new StackPane();
    private final Region scrim = new Region();
    private final EventHandler<KeyEvent> keyFilter = this::filterKey;
    private boolean dismissOnOutsideClick;
    private @Nullable Node card;
    private @Nullable Scene filteredScene;
    private @Nullable Node previousFocus;

    /** Creates an empty, hidden host; the shell places {@link #view()} in the scene. */
    @Inject
    public ModalHost() {
        host.setId(HOST_ID);
        host.getStyleClass().add("shell-modal-host");
        scrim.setId(SCRIM_ID);
        scrim.getStyleClass().add("shell-scrim");
        scrim.setOnMouseClicked(event -> onScrimClicked());
        host.getChildren().add(scrim);
        host.setVisible(false);
        scrim.setVisible(false);
    }

    /**
     * The node to place in the scene, above everything the host covers.
     *
     * @return the host region; invisible until a dialog is shown
     */
    Node view() {
        return host;
    }

    /**
     * Shows a dialog card over the dimmed window, replacing any card already shown.
     *
     * @param dialog the dialog's root node; the first focusable control in it takes keyboard focus
     * @param dismissOnOutsideClick {@code true} if a click on the dimmed area closes the dialog, {@code false} for a
     *     dialog that must be answered through its own controls; Escape closes either way
     */
    public void show(final Node dialog, final boolean dismissOnOutsideClick) {
        Objects.requireNonNull(dialog, "dialog");
        log.debug("showing dialog card {}, dismissOnOutsideClick={}", dialog.getId(), dismissOnOutsideClick);
        if (card != null) {
            release();
        }
        this.dismissOnOutsideClick = dismissOnOutsideClick;
        card = dialog;
        host.getChildren().setAll(scrim, dialog);
        host.setVisible(true);
        scrim.setVisible(true);
        capture();
        focusFirstIn(dialog);
    }

    /** Removes the card and the dimming layer and returns focus; a host with nothing shown ignores it. */
    public void hide() {
        log.debug("hiding the modal host, card shown: {}", card != null);
        release();
        host.getChildren().setAll(scrim);
        host.setVisible(false);
        scrim.setVisible(false);
    }

    private void onScrimClicked() {
        if (dismissOnOutsideClick) {
            log.debug("dialog dismissed by a click on the scrim");
            hide();
        } else {
            log.debug("click on the scrim ignored: this dialog is not dismissed from outside");
        }
    }

    private void capture() {
        final Scene scene = host.getScene();
        if (scene != null) {
            previousFocus = scene.getFocusOwner();
            scene.addEventFilter(KeyEvent.KEY_PRESSED, keyFilter);
            filteredScene = scene;
        }
    }

    private void release() {
        final Scene scene = filteredScene;
        if (scene != null) {
            scene.removeEventFilter(KeyEvent.KEY_PRESSED, keyFilter);
            filteredScene = null;
        }
        final Node restore = previousFocus;
        previousFocus = null;
        card = null;
        if (restore != null && restore.getScene() != null) {
            restore.requestFocus();
        }
    }

    private void filterKey(final KeyEvent event) {
        if (event.getCode() == KeyCode.ESCAPE) {
            log.debug("dialog dismissed by Escape");
            event.consume();
            hide();
        } else if (event.getCode() == KeyCode.TAB) {
            event.consume();
            moveFocus(event.isShiftDown());
        }
    }

    private void moveFocus(final boolean backwards) {
        final Node dialog = card;
        final Scene scene = host.getScene();
        if (dialog == null || scene == null) {
            return;
        }
        final List<Node> stops = new ArrayList<>();
        collectStops(dialog, stops);
        if (stops.isEmpty()) {
            return;
        }
        final int at = stops.indexOf(scene.getFocusOwner());
        final int step = backwards ? -1 : 1;
        final int next = at < 0 ? 0 : Math.floorMod(at + step, stops.size());
        stops.get(next).requestFocus();
    }

    private void focusFirstIn(final Node dialog) {
        final List<Node> stops = new ArrayList<>();
        collectStops(dialog, stops);
        if (stops.isEmpty()) {
            dialog.setFocusTraversable(true);
            dialog.requestFocus();
        } else {
            stops.get(0).requestFocus();
        }
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
