package ua.bookloom.ui.control;

import java.util.Objects;
import javafx.animation.AnimationTimer;
import javafx.event.EventTarget;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.control.IndexedCell;
import javafx.scene.control.ScrollBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.skin.VirtualFlow;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;

/**
 * Glides a mouse-wheel notch over a few frames instead of jumping by it, for every scroll pane, list, table and tree
 * under the node it is installed on, and moves it exactly as far as JavaFX would have jumped.
 *
 * <p>Installed once high in the scene, it finds the innermost scrollable under the pointer that still has room in the
 * wheel's direction and moves that one, so a list inside a scrolling screen scrolls first and the screen takes over at
 * the list's end. Only a wheel notch is glided ({@link ScrollGlide#isDiscreteWheel}); every other scroll event, and the
 * start and end of a gesture, pass on untouched, and no state is kept between events beyond each view's own glide.
 * Pressing a mouse button over a gliding view (to drag its scroll bar, say) stops the glide where it is. The system
 * property {@code bookloom.smoothScroll=false} turns the whole thing off. Each event is logged at TRACE only.
 */
// Checkstyle's HideUtilityClassConstructor parses source text before Lombok's annotation processor runs, so it
// cannot see the private constructor @NoArgsConstructor generates (ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@Slf4j
public final class SmoothScroll {

    /** The system property that turns gliding off when set to {@code false}. */
    public static final String SWITCH_PROPERTY = "bookloom.smoothScroll";

    private static final String GLIDE_KEY = "bookloom.smoothScroll.glide";
    private static final double NANOS_PER_SECOND = 1e9;
    private static final double FIRST_FRAME_SECONDS = 1.0 / 60;

    /**
     * Makes wheel scrolling smooth for every scrollable under {@code root}, unless the switch property turns it off.
     *
     * @param root the node whose descendants (and itself) glide; installing twice on one node is harmless but wasteful
     * @param <N> the node's type, returned unchanged so a call can wrap a construction
     * @return {@code root}
     */
    public static <N extends Node> N install(final N root) {
        Objects.requireNonNull(root, "root");
        if (!isSwitchedOn()) {
            log.debug("smooth wheel scrolling switched off by {}; {} scrolls as JavaFX does", SWITCH_PROPERTY, root);
            return root;
        }
        log.debug("smooth wheel scrolling installed on {}", root.getId());
        root.addEventFilter(ScrollEvent.SCROLL, event -> onScroll(root, event));
        root.addEventFilter(MouseEvent.MOUSE_PRESSED, event -> stopGlides(root, event.getTarget()));
        return root;
    }

    static boolean isSwitchedOn() {
        return !"false"
                .equalsIgnoreCase(System.getProperty(SWITCH_PROPERTY, "true").strip());
    }

    private static void onScroll(final Node root, final ScrollEvent event) {
        final boolean wheel = ScrollGlide.isDiscreteWheel(
                event.getTextDeltaYUnits(), event.getTouchCount(), event.isDirect(), event.isInertia());
        if (log.isTraceEnabled()) {
            log.trace(
                    "scroll event: direct {}, inertia {}, touches {}, deltaY {}, textDeltaY {}, units {}, multiplier {},"
                            + " wheel {}",
                    event.isDirect(),
                    event.isInertia(),
                    event.getTouchCount(),
                    event.getDeltaY(),
                    event.getTextDeltaY(),
                    event.getTextDeltaYUnits(),
                    event.getMultiplierY(),
                    wheel);
        }
        if (!wheel || event.getDeltaY() == 0) {
            return;
        }
        final Move move = moveWithRoom(root, event.getTarget(), event);
        if (move == null) {
            return;
        }
        log.trace("gliding {} by {} px", move.scroller().node().getClass().getSimpleName(), move.pixels());
        glideOf(move.scroller()).add(move.pixels());
        event.consume();
    }

    private static @Nullable Move moveWithRoom(
            final Node root, final @Nullable EventTarget target, final ScrollEvent event) {
        for (Node node = target instanceof Node start ? start : null; node != null; node = node.getParent()) {
            final Scroller scroller = scrollerOf(node);
            if (scroller != null) {
                final double pixels = scroller.pixelsOf(event);
                if (scroller.hasRoom(pixels)) {
                    return new Move(scroller, pixels);
                }
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
                glide.halt();
            }
            if (node.equals(root)) {
                return;
            }
        }
    }

    /** A scroller and how far one event moves it. */
    private record Move(Scroller scroller, double pixels) {}

    /** A view that can be moved by a distance in pixels. */
    private sealed interface Scroller permits PaneScroller, FlowScroller {

        Node node();

        /** How far JavaFX would move this view for the event, positive down the content. */
        double pixelsOf(ScrollEvent event);

        boolean hasRoom(double pixels);

        /** The visible height, which bounds how far a glide may run ahead. */
        double viewport();

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
        public double pixelsOf(final ScrollEvent event) {
            return ScrollGlide.panePixels(event.getDeltaY());
        }

        @Override
        public boolean hasRoom(final double pixels) {
            return overflow() > 0 && ScrollGlide.hasRoom(node.getVvalue(), node.getVmin(), node.getVmax(), pixels);
        }

        @Override
        public double viewport() {
            return node.getViewportBounds().getHeight();
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
        public double pixelsOf(final ScrollEvent event) {
            return ScrollGlide.flowPixels(event.getTextDeltaY(), ScrollGlide.lineSize(cellSize(), viewport()));
        }

        // JavaFX measures a line by the fixed cell size, else by the average height of the cells it has laid out.
        private double cellSize() {
            if (node.getFixedCellSize() > 0) {
                return node.getFixedCellSize();
            }
            final IndexedCell<?> first = node.getFirstVisibleCell();
            final IndexedCell<?> last = node.getLastVisibleCell();
            if (first == null || last == null || last.getIndex() < first.getIndex()) {
                return 0;
            }
            final double span = last.getLayoutY() + last.getHeight() - first.getLayoutY();
            return span / (last.getIndex() - first.getIndex() + 1);
        }

        @Override
        public boolean hasRoom(final double pixels) {
            final boolean scrolls = node.getChildrenUnmodifiable().stream()
                    .anyMatch(child -> child instanceof ScrollBar bar
                            && bar.getOrientation() == Orientation.VERTICAL
                            && bar.isVisible());
            return scrolls && ScrollGlide.hasRoom(node.getPosition(), 0, 1, pixels);
        }

        @Override
        public double viewport() {
            return node.getHeight();
        }

        @Override
        public double moveBy(final double pixels) {
            return node.scrollPixels(pixels);
        }
    }

    /**
     * One view's running glide: a target distance followed exponentially, frame by frame, so the view eases towards
     * where the notches add up to and stops exactly there, or at the view's end, whichever comes first.
     */
    private static final class Glide extends AnimationTimer {

        private final Scroller scroller;
        private double remaining;
        private long lastFrame;
        private boolean running;

        Glide(final Scroller scroller) {
            this.scroller = scroller;
        }

        void add(final double pixels) {
            remaining = ScrollGlide.retarget(
                    remaining, pixels, Math.max(1, scroller.viewport()) * ScrollGlide.AHEAD_VIEWPORTS);
            if (!running) {
                running = true;
                lastFrame = 0;
                start();
            }
        }

        void halt() {
            stop();
            running = false;
            remaining = 0;
        }

        @Override
        public void handle(final long now) {
            final double seconds = lastFrame == 0 ? FIRST_FRAME_SECONDS : (now - lastFrame) / NANOS_PER_SECOND;
            lastFrame = now;
            final double step = ScrollGlide.followStep(remaining, Math.max(seconds, FIRST_FRAME_SECONDS / 4));
            remaining -= step;
            final double moved = scroller.moveBy(step);
            if (remaining == 0 || moved == 0) {
                halt();
            }
        }
    }
}
