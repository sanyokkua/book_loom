package ua.bookloom.ui.control;

import java.util.Objects;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.event.EventTarget;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.control.ScrollBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.skin.VirtualFlow;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.util.Duration;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;

/**
 * Glides a mouse-wheel notch over a short animation instead of jumping by it, the way a browser or a file manager
 * scrolls, for every scroll pane, list, table and tree under the node it is installed on.
 *
 * <p>Installed once high in the scene (the shell's content and navigation), it finds the innermost scrollable under
 * the pointer that still has room in the wheel's direction and moves that one, so a list inside a scrolling screen
 * scrolls first and the screen takes over at the list's end. A trackpad or touch screen scrolls in small steps with its
 * own momentum already, so those events are left to JavaFX. Pressing a mouse button over a gliding view (to drag its
 * scroll bar, say) stops the glide where it is. The handlers log nothing: they run on every wheel notch.
 */
// Checkstyle's HideUtilityClassConstructor parses source text before Lombok's annotation processor runs, so it
// cannot see the private constructor @NoArgsConstructor generates (ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@Slf4j
public final class SmoothScroll {

    /** Long enough to read as movement, short enough that a fast spin never trails behind the hand. */
    static final Duration GLIDE = Duration.millis(150);

    private static final String GLIDE_KEY = "bookloom.smoothScroll.glide";

    /**
     * Makes wheel scrolling smooth for every scrollable under {@code root}.
     *
     * @param root the node whose descendants (and itself) glide; installing twice on one node is harmless but wasteful
     * @param <N> the node's type, returned unchanged so a call can wrap a construction
     * @return {@code root}
     */
    public static <N extends Node> N install(final N root) {
        Objects.requireNonNull(root, "root");
        log.debug("smooth wheel scrolling installed on {}", root.getId());
        final GestureState gesture = new GestureState();
        root.addEventFilter(ScrollEvent.SCROLL_STARTED, event -> gesture.inGesture = true);
        root.addEventFilter(ScrollEvent.SCROLL_FINISHED, event -> gesture.inGesture = false);
        root.addEventFilter(ScrollEvent.SCROLL, event -> onScroll(root, gesture, event));
        root.addEventFilter(MouseEvent.MOUSE_PRESSED, event -> stopGlides(root, event.getTarget()));
        return root;
    }

    private static void onScroll(final Node root, final GestureState gesture, final ScrollEvent event) {
        final boolean wheel = ScrollGlide.isDiscreteWheel(
                event.isDirect(), event.isInertia(), gesture.inGesture, event.getTouchCount(), event.getDeltaY());
        if (!wheel) {
            return;
        }
        final double pixels = ScrollGlide.pixelsOf(event.getDeltaY());
        final Scroller scroller = scrollerWithRoom(root, event.getTarget(), pixels);
        if (scroller == null) {
            return;
        }
        glideOf(scroller).add(pixels);
        event.consume();
    }

    private static @Nullable Scroller scrollerWithRoom(
            final Node root, final @Nullable EventTarget target, final double pixels) {
        for (Node node = target instanceof Node start ? start : null; node != null; node = node.getParent()) {
            final Scroller scroller = scrollerOf(node);
            if (scroller != null && scroller.hasRoom(pixels)) {
                return scroller;
            }
            if (node.equals(root)) {
                return null;
            }
        }
        return null;
    }

    private static @Nullable Scroller scrollerOf(final Node node) {
        return switch (node) {
            case ScrollPane pane -> new PaneScroller(pane);
            case VirtualFlow<?> flow when flow.isVertical() -> new FlowScroller(flow);
            default -> null;
        };
    }

    private static Glide glideOf(final Scroller scroller) {
        return (Glide) scroller.node().getProperties().computeIfAbsent(GLIDE_KEY, key -> new Glide(scroller));
    }

    private static void stopGlides(final Node root, final @Nullable EventTarget target) {
        for (Node node = target instanceof Node start ? start : null; node != null; node = node.getParent()) {
            if (node.getProperties().get(GLIDE_KEY) instanceof Glide glide) {
                glide.stop();
            }
            if (node.equals(root)) {
                return;
            }
        }
    }

    /** Whether a trackpad gesture is under way, so the events between its start and its end pass untouched. */
    private static final class GestureState {
        private boolean inGesture;
    }

    /** A view that can be moved by a distance in pixels. */
    private sealed interface Scroller permits PaneScroller, FlowScroller {

        Node node();

        boolean hasRoom(double pixels);

        /** Moves by up to {@code pixels} and answers how far the view really moved. */
        double moveBy(double pixels);
    }

    private record PaneScroller(ScrollPane node) implements Scroller {

        private double overflow() {
            final Node content = node.getContent();
            return content == null
                    ? 0
                    : content.getLayoutBounds().getHeight()
                            - node.getViewportBounds().getHeight();
        }

        @Override
        public boolean hasRoom(final double pixels) {
            return overflow() > 0 && ScrollGlide.hasRoom(node.getVvalue(), node.getVmin(), node.getVmax(), pixels);
        }

        @Override
        public double moveBy(final double pixels) {
            final double overflow = overflow();
            final double before = node.getVvalue();
            final double after = ScrollGlide.paneValue(before, node.getVmin(), node.getVmax(), pixels, overflow);
            node.setVvalue(after);
            final double range = node.getVmax() - node.getVmin();
            return range <= 0 ? 0 : (after - before) * overflow / range;
        }
    }

    private record FlowScroller(VirtualFlow<?> node) implements Scroller {

        @Override
        public boolean hasRoom(final double pixels) {
            final boolean scrolls = node.getChildrenUnmodifiable().stream()
                    .anyMatch(child -> child instanceof ScrollBar bar
                            && bar.getOrientation() == Orientation.VERTICAL
                            && bar.isVisible());
            return scrolls && ScrollGlide.hasRoom(node.getPosition(), 0, 1, pixels);
        }

        @Override
        public double moveBy(final double pixels) {
            return node.scrollPixels(pixels);
        }
    }

    /**
     * One view's running glide: an eased distance from zero to the total, applied to the view as it grows, so the
     * view moves by the steps between frames and anything else that moves it meanwhile is not undone.
     */
    private static final class Glide {

        private final Scroller scroller;
        private final DoubleProperty travelled = new SimpleDoubleProperty();
        private final Timeline timeline = new Timeline();
        private double total;
        private double applied;

        Glide(final Scroller scroller) {
            this.scroller = scroller;
            travelled.addListener((observed, was, now) -> step(now.doubleValue()));
        }

        void add(final double pixels) {
            timeline.stop();
            total = ScrollGlide.accumulate(total - applied, pixels);
            applied = 0;
            travelled.set(0);
            timeline.getKeyFrames().setAll(new KeyFrame(GLIDE, new KeyValue(travelled, total, Interpolator.EASE_OUT)));
            timeline.playFromStart();
        }

        void stop() {
            timeline.stop();
            total = applied;
        }

        private void step(final double now) {
            final double delta = now - applied;
            if (delta == 0) {
                return;
            }
            applied = now;
            if (scroller.moveBy(delta) == 0) {
                stop();
            }
        }
    }
}
