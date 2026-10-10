package ua.bookloom.ui.control;

import javafx.event.EventTarget;
import javafx.scene.Node;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;

/**
 * The event filter {@link SmoothScroll} installs: finds the scroller an event belongs to and hands the distance to its
 * glide (wheel) or its per-pulse sum (trackpad). One writer moves a view for a gesture: a pixel event is either
 * consumed here and applied by the sum, or (a sideways swipe) left whole to JavaFX, never both. What a sum asks of a
 * view beyond its end goes on to the views around it.
 *
 * <p>It runs on the FX thread for every event, a couple of hundred a second, so it logs nothing per event unless
 * {@value ScrollStats#PER_EVENT_PROPERTY} is set; {@link ScrollStats} reports a total once a second instead.
 */
@Slf4j
@RequiredArgsConstructor
final class ScrollFilter {

    private static final String GLIDE_KEY = "bookloom.smoothScroll.glide";
    private static final String FLUSH_KEY = "bookloom.smoothScroll.flush";
    /** Less than this is rounding left by a scroll pane's value arithmetic, not a distance a view refused. */
    private static final double HAND_OUT_PIXELS = 1e-3;

    private final Node root;
    private final PulseClock clock;
    private final PixelMode mode;
    private final ScrollStats stats;

    void onScroll(final ScrollEvent event) {
        final boolean wheel = ScrollGlide.isDiscreteWheel(
                event.getTextDeltaYUnits(), event.getTouchCount(), event.isDirect(), event.isInertia());
        traceEvent(event, wheel);
        final boolean pixel = !wheel && isVerticalPixels(event);
        if (!(wheel || pixel) || event.getDeltaY() == 0 || (pixel && mode == PixelMode.NATIVE)) {
            return;
        }
        final Move move = moveWithRoom(event, wheel);
        if (move == null) {
            return;
        }
        stats.eventIn(move.pixels());
        if (wheel) {
            glideOf(move.scroller()).add(move.pixels());
        } else {
            flushOf(move.scroller()).add(move.pixels());
        }
        event.consume();
    }

    // macOS adds a sideways part of a pixel or two to a vertical swipe, so the larger part decides the direction; only
    // a mostly sideways swipe is left to JavaFX.
    private static boolean isVerticalPixels(final ScrollEvent event) {
        return ScrollGlide.isPixelScroll(event.getTextDeltaYUnits(), event.isDirect())
                && Math.abs(event.getDeltaY()) >= Math.abs(event.getDeltaX());
    }

    private void traceEvent(final ScrollEvent event, final boolean wheel) {
        if (stats.isPerEvent() && log.isTraceEnabled()) {
            log.trace(
                    "scroll event: direct {}, inertia {}, touches {}, deltaX {}, deltaY {}, textDeltaY {}, units {},"
                            + " multiplier {}, wheel {}",
                    event.isDirect(),
                    event.isInertia(),
                    event.getTouchCount(),
                    event.getDeltaX(),
                    event.getDeltaY(),
                    event.getTextDeltaY(),
                    event.getTextDeltaYUnits(),
                    event.getMultiplierY(),
                    wheel);
        }
    }

    private @Nullable Move moveWithRoom(final ScrollEvent event, final boolean wheel) {
        final EventTarget target = event.getTarget();
        for (Node node = target instanceof Node start ? start : null; node != null; node = node.getParent()) {
            final Scroller scroller = Scroller.of(node);
            if (scroller != null) {
                final double pixels = wheel ? scroller.pixelsOf(event) : ScrollGlide.panePixels(event.getDeltaY());
                if (scroller.hasRoom(pixels, wheel ? 0 : pendingOf(scroller))) {
                    return new Move(scroller, pixels);
                }
            }
            if (node.equals(root)) {
                return null;
            }
        }
        return null;
    }

    private static double pendingOf(final Scroller scroller) {
        return scroller.node().getProperties().get(FLUSH_KEY) instanceof PixelFlush flush ? flush.pending() : 0;
    }

    private static Glide glideOf(final Scroller scroller) {
        return (Glide) scroller.node().getProperties().computeIfAbsent(GLIDE_KEY, key -> new Glide(scroller));
    }

    private PixelFlush flushOf(final Scroller scroller) {
        return (PixelFlush) scroller.node()
                .getProperties()
                .computeIfAbsent(FLUSH_KEY, key -> new PixelFlush(pixels -> moveOrHandOut(scroller, pixels), clock));
    }

    // A list's room is judged from its estimated position, so a flush can ask it for more than it has left; what it
    // could not move goes to the scrollers around it, nearest first, instead of being lost.
    private void moveOrHandOut(final Scroller scroller, final double pixels) {
        double left = pixels - moved(scroller.moveBy(pixels));
        for (Node node = scroller.node().getParent();
                node != null && Math.abs(left) > HAND_OUT_PIXELS;
                node = node.getParent()) {
            final Scroller outer = Scroller.of(node);
            if (outer != null && outer.hasRoom(left, 0)) {
                left -= moved(outer.moveBy(left));
            }
            if (node.equals(root)) {
                return;
            }
        }
    }

    private double moved(final double pixels) {
        stats.moved(pixels);
        return pixels;
    }

    void stopGlides(final MouseEvent event) {
        final EventTarget target = event.getTarget();
        for (Node node = target instanceof Node start ? start : null; node != null; node = node.getParent()) {
            if (node.getProperties().get(GLIDE_KEY) instanceof Glide glide) {
                glide.halt();
            }
            if (node.getProperties().get(FLUSH_KEY) instanceof PixelFlush flush) {
                flush.halt();
            }
            if (node.equals(root)) {
                return;
            }
        }
    }

    /** A scroller and how far one event moves it. */
    private record Move(Scroller scroller, double pixels) {}
}
