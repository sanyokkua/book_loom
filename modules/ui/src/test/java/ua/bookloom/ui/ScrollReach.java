package ua.bookloom.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import javafx.geometry.Bounds;
import javafx.geometry.Orientation;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Accordion;
import javafx.scene.control.Control;
import javafx.scene.control.Labeled;
import javafx.scene.control.ScrollBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TabPane;
import javafx.scene.control.TitledPane;
import javafx.scene.control.ToolBar;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;

/**
 * Finds the controls a person cannot see because they lie below the bottom of what is shown — under a scroll
 * viewport's edge, a tab's clip or the window's edge — and the scroll pane the wheel must move to bring them up.
 * Every method runs on the FX thread.
 */
// Checkstyle parses source text before Lombok runs, so it cannot see the private constructor (ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class ScrollReach {

    private static final double TOLERANCE = 1;
    private static final Set<Class<?>> CONTAINERS =
            Set.of(ScrollPane.class, TabPane.class, TitledPane.class, SplitPane.class, Accordion.class, ToolBar.class);

    /** A control drawn below the bottom of what is shown, with the scroll pane nearest it that can still move down. */
    record Hidden(
            Control control,
            double bottom,
            double visibleBottom,
            @Nullable ScrollPane pane) {

        String describe() {
            return name(control) + " bottom " + Math.round(bottom) + " > shown " + Math.round(visibleBottom);
        }
    }

    /** The part a person scrolls: the open dialog card on top, else the shell's content area. */
    static Node scope(final Scene scene) {
        final List<Node> cards = scene.getRoot().lookupAll(".dialog-card").stream()
                .filter(ScrollReach::isShown)
                .toList();
        return cards.isEmpty() ? scene.lookup("#shell-content-scroll") : cards.getLast();
    }

    /** Every shown control at or below {@code scope} whose bottom edge lies below the bottom of what is shown. */
    static List<Hidden> hiddenBelow(final Node scope) {
        final List<Hidden> found = new ArrayList<>();
        collect(scope, scope, found);
        return found;
    }

    private static void collect(final Node scope, final Node node, final List<Hidden> found) {
        if (!node.isVisible() || isClosedFold(node)) {
            return;
        }
        if (node instanceof Control control && !isContainer(control)) {
            if (!(control instanceof ScrollBar)) {
                hidden(scope, control, found);
            }
            return;
        }
        if (node instanceof Parent parent) {
            parent.getChildrenUnmodifiable().forEach(child -> collect(scope, child, found));
        }
    }

    private static void hidden(final Node scope, final Control control, final List<Hidden> found) {
        final Bounds box = control.localToScene(control.getLayoutBounds());
        if (box.getHeight() <= 0 || box.getWidth() <= 0) {
            return;
        }
        final double visibleBottom = visibleBottom(control);
        if (box.getMaxY() > visibleBottom + TOLERANCE) {
            found.add(new Hidden(control, box.getMaxY(), visibleBottom, paneWithRoom(scope, control)));
        }
    }

    // The lowest scene y a node can be seen at: every clip above it (a scroll viewport's, a tab's) and the window.
    private static double visibleBottom(final Node node) {
        double bottom = node.getScene().getHeight();
        for (Node above = node.getParent(); above != null; above = above.getParent()) {
            final Node clip = above.getClip();
            if (clip != null) {
                bottom = Math.min(
                        bottom, above.localToScene(clip.getBoundsInParent()).getMaxY());
            }
        }
        return bottom;
    }

    // The nearest scroll pane around the node, up to the scope, that shows a vertical bar and can still move down.
    private static @Nullable ScrollPane paneWithRoom(final Node scope, final Node node) {
        for (Node above = node.getParent(); above != null; above = above.getParent()) {
            if (above instanceof ScrollPane pane && hasVisibleBar(pane) && pane.getVvalue() < pane.getVmax()) {
                return pane;
            }
            if (above.equals(scope)) {
                return null;
            }
        }
        return null;
    }

    /** Whether the pane draws its vertical scroll bar, the sign a person has that there is more below. */
    static boolean hasVisibleBar(final ScrollPane pane) {
        return pane.lookupAll(".scroll-bar").stream()
                .anyMatch(bar -> bar instanceof ScrollBar scrollBar
                        && scrollBar.getOrientation() == Orientation.VERTICAL
                        && scrollBar.getParent() != null
                        && isOwnBar(pane, scrollBar)
                        && scrollBar.isVisible());
    }

    // A pane's lookup also finds the bars of the panes inside it; its own bars are children of its skin.
    private static boolean isOwnBar(final ScrollPane pane, final ScrollBar bar) {
        return pane.getChildrenUnmodifiable().contains(bar);
    }

    /** The middle of what is shown of the pane's viewport, on the screen, where the wheel is turned. */
    static Point2D pointerOver(final ScrollPane pane) {
        final Node viewport = pane.lookup(".viewport");
        final Node area = viewport == null ? pane : viewport;
        final Bounds box = area.localToScene(area.getLayoutBounds());
        final double bottom = Math.min(box.getMaxY(), visibleBottom(area));
        final double top = Math.max(box.getMinY(), 0);
        final Point2D middle = new Point2D(box.getMinX() + box.getWidth() / 2, (top + bottom) / 2);
        final Scene scene = pane.getScene();
        return new Point2D(
                scene.getWindow().getX() + scene.getX() + middle.getX(),
                scene.getWindow().getY() + scene.getY() + middle.getY());
    }

    /** What a stuck pane looked like: its viewport on the scene, the bottom shown and the heights it scrolls. */
    static String diagnose(final ScrollPane pane) {
        final Node viewport = pane.lookup(".viewport");
        final Node area = viewport == null ? pane : viewport;
        final Node content = pane.getContent();
        return name(pane) + " viewport " + area.localToScene(area.getLayoutBounds()) + " shown to "
                + Math.round(visibleBottom(area)) + " content "
                + (content == null ? 0 : Math.round(content.getLayoutBounds().getHeight())) + " in "
                + Math.round(pane.getViewportBounds().getHeight());
    }

    static String name(final Node node) {
        final String text = node instanceof Labeled labeled ? " '" + labeled.getText() + "'" : "";
        Node named = node;
        while (named != null && named.getId() == null) {
            named = named.getParent();
        }
        final String owner = named == null || named.equals(node) ? "" : " in #" + named.getId();
        return node.getClass().getSimpleName() + (node.getId() == null ? "" : "#" + node.getId()) + text + owner;
    }

    private static boolean isContainer(final Control control) {
        return CONTAINERS.stream().anyMatch(type -> type.isInstance(control));
    }

    private static boolean isShown(final Node node) {
        for (Node above = node; above != null; above = above.getParent()) {
            if (!above.isVisible()) {
                return false;
            }
        }
        return true;
    }

    // A closed fold keeps its content laid out at full size under a clip of no height; nothing in it is shown.
    private static boolean isClosedFold(final Node node) {
        final Parent parent = node.getParent();
        return parent != null
                && parent.getParent() instanceof TitledPane fold
                && !fold.isExpanded()
                && fold.getContent() != null
                && node.equals(fold.getContent());
    }
}
