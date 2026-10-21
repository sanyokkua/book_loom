package ua.bookloom.ui;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import javafx.application.Platform;
import javafx.event.EventHandler;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.TraversalDirection;
import javafx.scene.control.Button;
import javafx.scene.control.TextArea;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.ui.control.Motion;
import ua.bookloom.ui.dialog.ModalCard;
import ua.bookloom.ui.state.WorkVisibility;

/**
 * The shell's overlay for one dialog at a time: a dimming layer with the dialog card centred over it.
 *
 * <p>The scrim and the card are siblings rather than parent and child so a click inside the card can never be
 * mistaken for a click on the dimmed area. While a card is shown a scene-level key filter keeps Escape working (for a card that may be closed) and
 * keeps Tab inside the card, wherever focus was when it opened, and the filter is removed again on hide so nothing
 * outlives the dialog. Focus goes back to whatever owned it before. Everything is FX-Application-Thread only, like
 * the scene graph it edits.
 *
 * <p>Enter runs the card's default button (Ctrl or Command with Enter inside a text area, where plain Enter adds a
 * line). Escape runs the card's own escape action when it has one ({@link ModalCard#setOnEscape}), else closes a card
 * that may be closed. A dialog nobody asked for goes through {@link #showWhenFree} and waits for the open card and for
 * visible model work.
 */
@Slf4j
@Singleton
public final class ModalHost {

    static final String HOST_ID = "shell-modal-host";
    static final String SCRIM_ID = "shell-scrim";

    private final StackPane host = new StackPane();
    private final Region scrim = new Region();
    private final EventHandler<KeyEvent> keyFilter = this::filterKey;
    private final WorkVisibility work;
    private final Deque<Node> waiting = new ArrayDeque<>();
    private boolean dismissOnOutsideClick;
    private boolean dismissOnEscape;
    private @Nullable Node card;
    private @Nullable Scene filteredScene;
    private @Nullable Node previousFocus;

    /**
     * Creates an empty, hidden host; the shell places {@link #view()} in the scene.
     *
     * @param work whether model work is visible, which holds back a dialog nobody asked for
     */
    @Inject
    public ModalHost(final WorkVisibility work) {
        this.work = Objects.requireNonNull(work, "work");
        host.setId(HOST_ID);
        host.getStyleClass().add("shell-modal-host");
        scrim.setId(SCRIM_ID);
        scrim.getStyleClass().add("shell-scrim");
        scrim.setOnMouseClicked(event -> onScrimClicked());
        host.getChildren().add(scrim);
        host.setVisible(false);
        scrim.setVisible(false);
        work.visibleProperty().addListener((observable, old, now) -> scheduleDrain());
        host.widthProperty().addListener((observable, old, now) -> fitCard());
        host.heightProperty().addListener((observable, old, now) -> fitCard());
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
     * Shows a dialog card over the dimmed window, replacing any card already shown. Escape closes the card.
     *
     * @param dialog the dialog's root node; the first focusable control in it takes keyboard focus
     * @param dismissOnOutsideClick {@code true} if a click on the dimmed area closes the dialog, {@code false} for a
     *     dialog that must be answered through its own controls
     */
    public void show(final Node dialog, final boolean dismissOnOutsideClick) {
        show(dialog, dismissOnOutsideClick, true);
    }

    /**
     * Shows a dialog card over the dimmed window, replacing any card already shown.
     *
     * @param dialog the dialog's root node; the first focusable control in it takes keyboard focus
     * @param dismissOnOutsideClick {@code true} if a click on the dimmed area closes the dialog
     * @param dismissOnEscape {@code true} if Escape closes the dialog, {@code false} for a card that must be answered
     *     through its own controls, because closing it unanswered would leave what waits on the answer waiting
     */
    public void show(final Node dialog, final boolean dismissOnOutsideClick, final boolean dismissOnEscape) {
        Objects.requireNonNull(dialog, "dialog");
        log.debug(
                "showing dialog card {}, dismissOnOutsideClick={}, dismissOnEscape={}",
                dialog.getId(),
                dismissOnOutsideClick,
                dismissOnEscape);
        if (card != null) {
            release();
        }
        this.dismissOnOutsideClick = dismissOnOutsideClick;
        this.dismissOnEscape = dismissOnEscape;
        card = dialog;
        fitCard();
        final boolean arriving = !host.isVisible();
        host.getChildren().setAll(scrim, dialog);
        host.setVisible(true);
        scrim.setVisible(true);
        if (arriving) {
            Motion.fadeIn(host, Motion.STANDARD);
        }
        Motion.popIn(dialog);
        capture();
        focusFirstIn(dialog);
    }

    /**
     * Shows a dialog nobody asked for, such as the review desk at the end of a run: at once when no card is open and no
     * model work is visible, else after the open card closes and the work has left. Cards wait in the order given.
     *
     * @param dialog the dialog's root node; it closes on Escape and not on an outside click
     */
    public void showWhenFree(final Node dialog) {
        Objects.requireNonNull(dialog, "dialog");
        if (isFree()) {
            log.debug("window is free: showing {} at once", dialog.getId());
            show(dialog, false, true);
        } else {
            log.debug("window is busy: {} waits its turn, {} already waiting", dialog.getId(), waiting.size());
            waiting.add(dialog);
        }
    }

    private boolean isFree() {
        return card == null && !work.isVisible();
    }

    // Deferred a turn: a dialog that closes and, in the same handler, opens its follow-up must win over the queue.
    private void scheduleDrain() {
        if (!waiting.isEmpty()) {
            Platform.runLater(this::drain);
        }
    }

    private void drain() {
        if (isFree() && !waiting.isEmpty()) {
            final Node next = waiting.poll();
            log.debug("window is free: showing the waiting dialog {}", next.getId());
            show(next, false, true);
        }
    }

    private void fitCard() {
        final Node dialog = card;
        if (dialog instanceof ModalCard modal) {
            final Scene scene = host.getScene();
            final double width = host.getWidth() > 0 || scene == null ? host.getWidth() : scene.getWidth();
            final double height = host.getHeight() > 0 || scene == null ? host.getHeight() : scene.getHeight();
            if (width > 0 && height > 0) {
                modal.fit(width, height);
            }
        }
    }

    /**
     * Whether a dialog card is shown now; a layer below that has its own keyboard handling stands back while one is.
     *
     * @return {@code true} while a card is shown
     */
    boolean isShowing() {
        return card != null;
    }

    /** Removes the card and the dimming layer and returns focus; a host with nothing shown ignores it. */
    public void hide() {
        log.debug("hiding the modal host, card shown: {}", card != null);
        release();
        host.getChildren().setAll(scrim);
        host.setVisible(false);
        scrim.setVisible(false);
        scheduleDrain();
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
            event.consume();
            escape();
        } else if (event.getCode() == KeyCode.ENTER) {
            enter(event);
        } else if (event.getCode() == KeyCode.TAB) {
            event.consume();
            moveFocus(event.isShiftDown());
        }
    }

    private void escape() {
        final Runnable own = card instanceof ModalCard modal ? modal.onEscape() : null;
        if (own != null) {
            log.debug("Escape runs the escape action of the card");
            own.run();
        } else if (dismissOnEscape) {
            log.debug("dialog dismissed by Escape");
            hide();
        } else {
            log.debug("Escape ignored: this dialog must be answered through its own controls");
        }
    }

    private void enter(final KeyEvent event) {
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

    private static @Nullable Button defaultButton(final @Nullable Node dialog) {
        if (dialog instanceof Button button && button.isDefaultButton() && !button.isDisabled()) {
            return button;
        }
        if (dialog instanceof Parent parent) {
            for (final Node child : parent.getChildrenUnmodifiable()) {
                final Button found = defaultButton(child);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
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
