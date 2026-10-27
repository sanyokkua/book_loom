package ua.bookloom.ui.control;

import java.util.List;
import java.util.Objects;
import javafx.animation.PauseTransition;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Control;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseEvent;
import javafx.util.Duration;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;

/**
 * Shows the tooltip of a disabled control under the pointer, for the whole window at once.
 *
 * <p>JavaFX never picks a node whose {@code disable} is set, so a disabled button gets no pointer events and its
 * tooltip — which for Start says what is missing — never shows. The scene sees every move, though: the node picked is
 * the one behind the button, and the disabled control is looked for among its children under the pointer. One filter
 * on the scene covers every disabled control with a tooltip, so no screen wraps its buttons. Nothing is logged per
 * move; only a shown tooltip is.
 */
// Checkstyle's HideUtilityClassConstructor parses source text before Lombok's annotation processor runs, so it
// cannot see the private constructor @NoArgsConstructor generates (ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class DisabledTips {

    // The same wait as every other tooltip (Tips), so a passing pointer shows nothing.
    private static final Duration SHOW_DELAY = Duration.millis(400);
    private static final double POINTER_OFFSET = 12;

    /**
     * Starts showing disabled controls' tooltips in a scene. FX thread.
     *
     * @param scene the window's scene
     */
    public static void install(final Scene scene) {
        Objects.requireNonNull(scene, "scene");
        final Hover hover = new Hover();
        scene.addEventFilter(MouseEvent.MOUSE_MOVED, hover::moved);
        scene.addEventFilter(MouseEvent.MOUSE_PRESSED, event -> hover.clear());
        scene.addEventFilter(MouseEvent.MOUSE_EXITED_TARGET, event -> {
            if (scene.equals(event.getTarget())) {
                hover.clear();
            }
        });
    }

    /**
     * The disabled control with a tooltip under a point, looked for from the node the pointer picked down through the
     * children that hold the point, the topmost first.
     *
     * @param picked the node the pointer picked
     * @param sceneX the point's x in the scene
     * @param sceneY the point's y in the scene
     * @return the control, or {@code null} when none is under the point
     */
    static @Nullable Control disabledAt(final Node picked, final double sceneX, final double sceneY) {
        if (!picked.isVisible() || !picked.contains(picked.sceneToLocal(sceneX, sceneY))) {
            return null;
        }
        if (picked instanceof Control control && control.isDisabled() && control.getTooltip() != null) {
            return control;
        }
        if (!(picked instanceof Parent parent)) {
            return null;
        }
        final List<Node> children = parent.getChildrenUnmodifiable();
        for (int at = children.size() - 1; at >= 0; at--) {
            final Control found = disabledAt(children.get(at), sceneX, sceneY);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    /** The one tooltip pending or shown for a disabled control, and the pointer it follows. */
    private static final class Hover {
        private final PauseTransition delay = new PauseTransition(SHOW_DELAY);
        private @Nullable Control current;
        private Point2D pointer = Point2D.ZERO;

        void moved(final MouseEvent event) {
            pointer = new Point2D(event.getScreenX(), event.getScreenY());
            final Node picked = event.getPickResult().getIntersectedNode();
            final Control found = picked == null ? null : disabledAt(picked, event.getSceneX(), event.getSceneY());
            if (Objects.equals(found, current)) {
                return;
            }
            clear();
            if (found != null) {
                current = found;
                delay.setOnFinished(finished -> show(found));
                delay.playFromStart();
            }
        }

        private void show(final Control control) {
            final Tooltip tooltip = control.getTooltip();
            if (tooltip == null || !control.isDisabled() || control.getScene() == null) {
                return;
            }
            log.debug("showing the tooltip of disabled {}", control.getId());
            tooltip.show(control, pointer.getX() + POINTER_OFFSET, pointer.getY() + POINTER_OFFSET);
        }

        void clear() {
            delay.stop();
            final Tooltip shown = current == null ? null : current.getTooltip();
            current = null;
            if (shown != null && shown.isShowing()) {
                shown.hide();
            }
        }
    }
}
