package ua.bookloom.ui.control;

import java.util.Objects;
import javafx.scene.Node;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Glides a mouse-wheel notch over a few frames instead of jumping by it, for every scroll pane, list, table and tree
 * under the node it is installed on, and moves it exactly as far as JavaFX would have jumped.
 *
 * <p>Installed once high in the scene, it finds the innermost scrollable under the pointer that still has room in the
 * wheel's direction and moves that one, so a list inside a scrolling screen scrolls first and the screen takes over at
 * the list's end. A wheel notch is glided ({@link ScrollGlide#isDiscreteWheel}); the pixel deltas of a trackpad or its momentum
 * (macOS reports every event so) are added up and applied once per animation pulse by {@link PixelFlush}, unglided;
 * the start and end of a gesture, a touch screen and a mostly sideways swipe pass on untouched, and no state is kept between events beyond each view's own glide or pending sum. A pixel event counts as vertical when its vertical part is the larger, because macOS adds a pixel or two of sideways noise to a vertical swipe.
 * Pressing a mouse button over a gliding view (to drag its scroll bar, say) stops the glide where it is. The system
 * property {@code bookloom.smoothScroll=false} turns the whole thing off. Events are summed into one DEBUG line a second ({@link ScrollStats}); {@code -Dbookloom.log.scroll=true} logs each at TRACE.
 */
// Checkstyle's HideUtilityClassConstructor parses source text before Lombok's annotation processor runs, so it
// cannot see the private constructor @NoArgsConstructor generates (ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@Slf4j
public final class SmoothScroll {

    /** The system property that turns gliding off when set to {@code false}. */
    public static final String SWITCH_PROPERTY = "bookloom.smoothScroll";

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
        return install(root, PulseClock.animation(), PixelMode.BATCH, ScrollStats.system());
    }

    static <N extends Node> N install(
            final N root, final PulseClock clock, final PixelMode mode, final ScrollStats stats) {
        final ScrollFilter filter = new ScrollFilter(root, clock, mode, stats);
        root.addEventFilter(ScrollEvent.SCROLL, filter::onScroll);
        root.addEventFilter(MouseEvent.MOUSE_PRESSED, filter::stopGlides);
        return root;
    }

    static boolean isSwitchedOn() {
        return !"false"
                .equalsIgnoreCase(System.getProperty(SWITCH_PROPERTY, "true").strip());
    }
}
