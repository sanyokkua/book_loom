package ua.bookloom.ui.control;

import javafx.scene.input.ScrollEvent;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * The arithmetic behind smooth wheel scrolling, kept apart from the scene graph so each rule is checked on its own.
 *
 * <p>A wheel notch travels exactly as far as JavaFX would have jumped it, only spread over a few frames: a list moves
 * by lines of its own row height, a scroll pane by the event's pixels. Notches that arrive while a glide still runs add
 * to what is left of it, up to a few screens, and a notch the other way replaces it. A trackpad, a touch screen and the
 * momentum after a gesture already scroll in small continuous steps, so their events are left to JavaFX.
 */
// Checkstyle's HideUtilityClassConstructor parses source text before Lombok's annotation processor runs, so it
// cannot see the private constructor @NoArgsConstructor generates (ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class ScrollGlide {

    /** JavaFX's own floor: a list scrolls at least this many lines per page, so a line is never taller than this. */
    static final int MIN_LINES_PER_PAGE = 8;

    /** How long the follower takes to cover about two thirds of what is left; a notch settles in about 0.25 s. */
    static final double TAU_SECONDS = 0.06;

    /** The furthest a glide runs ahead of the view, in viewports, so a long spin never sails on after the hand stops. */
    static final double AHEAD_VIEWPORTS = 3;

    /** Within this distance the follower lands on the target instead of creeping towards it. */
    static final double LANDING_PIXELS = 1;

    /**
     * Whether a scroll event is a mouse-wheel notch rather than a trackpad, a touch screen or a gesture's momentum.
     *
     * @param units the event's vertical text units; a wheel reports {@code LINES}
     * @param touchCount the fingers on the device, zero for a wheel
     * @param direct whether the event comes from a touch screen
     * @param inertia whether the event is the momentum that follows a gesture
     * @return {@code true} if the event is a wheel notch to glide, {@code false} if JavaFX should handle it as it comes
     */
    static boolean isDiscreteWheel(
            final ScrollEvent.VerticalTextScrollUnits units,
            final int touchCount,
            final boolean direct,
            final boolean inertia) {
        return units == ScrollEvent.VerticalTextScrollUnits.LINES && touchCount == 0 && !direct && !inertia;
    }

    /**
     * The height of one wheel line in a list, the way JavaFX's own list scrolling measures it.
     *
     * @param cellSize the fixed cell size, or else the average height of the cells shown; at most zero when unknown
     * @param viewport the list's visible height in pixels
     * @return the cell size, but never more than an eighth of the viewport; an eighth of the viewport when the cell
     *     size is unknown
     */
    static double lineSize(final double cellSize, final double viewport) {
        final double floor = viewport / MIN_LINES_PER_PAGE;
        return cellSize <= 0 ? floor : Math.min(cellSize, floor);
    }

    /**
     * The distance a wheel event moves a list, in pixels: positive is down the content.
     *
     * @param textDeltaY the event's lines, positive when the wheel turns towards the top of the content
     * @param lineSize the height of one line, from {@link #lineSize}
     * @return the distance JavaFX would have jumped the list
     */
    static double flowPixels(final double textDeltaY, final double lineSize) {
        return -textDeltaY * lineSize;
    }

    /**
     * The distance a wheel event moves a scroll pane, in pixels: positive is down the content.
     *
     * @param deltaY the event's pixels, positive when the wheel turns towards the top of the content
     * @return the distance JavaFX would have jumped the pane
     */
    static double panePixels(final double deltaY) {
        return -deltaY;
    }

    /**
     * What a glide still has to travel after a further notch.
     *
     * @param pending the distance the running glide has not travelled yet, zero when none runs
     * @param added the new notch's distance
     * @param limit the furthest a glide may run ahead, positive
     * @return the sum when both go the same way, only the new notch when it turns back, never longer than {@code limit}
     */
    static double retarget(final double pending, final double added, final double limit) {
        final double sum = Math.signum(pending) == -Math.signum(added) ? added : pending + added;
        return Math.clamp(sum, -limit, limit);
    }

    /**
     * The part of the remaining distance one frame covers: an exponential follow, which never overshoots.
     *
     * <p>The steps are whole pixels until the last, which takes what is left, so the steps add up to exactly the
     * distance asked for: subtracting whole numbers from it loses no bits, where fractional steps would leave the view a
     * hair short of where JavaFX's own jump lands.
     *
     * @param remaining the distance still to travel
     * @param seconds the time since the previous frame, positive
     * @return a step of the same sign and at most {@code remaining}: a share of it rounded to whole pixels, at least one,
     *     and all of it once at most a pixel is left
     */
    static double followStep(final double remaining, final double seconds) {
        if (Math.abs(remaining) <= LANDING_PIXELS) {
            return remaining;
        }
        final double step = Math.rint(remaining * (1 - Math.exp(-seconds / TAU_SECONDS)));
        return step == 0 ? Math.signum(remaining) : step;
    }

    /**
     * A scroll pane's next value after moving a distance in pixels.
     *
     * @param value the current value, between {@code min} and {@code max}
     * @param min the smallest value
     * @param max the largest value
     * @param pixels the distance to move, positive down the content
     * @param overflow how much taller the content is than the viewport, in pixels; at most zero when nothing scrolls
     * @return the new value, held within {@code min} and {@code max}; {@code value} when nothing scrolls
     */
    static double paneValue(
            final double value, final double min, final double max, final double pixels, final double overflow) {
        if (overflow <= 0 || max <= min) {
            return value;
        }
        final double moved = value + pixels * (max - min) / overflow;
        return Math.clamp(moved, min, max);
    }

    /**
     * Whether a position can still move the given way.
     *
     * @param position the current position
     * @param min the position at the top
     * @param max the position at the bottom
     * @param pixels the distance asked for, positive down the content
     * @return {@code true} if there is room in that direction, {@code false} at the edge or for no movement
     */
    static boolean hasRoom(final double position, final double min, final double max, final double pixels) {
        return pixels > 0 ? position < max : pixels < 0 && position > min;
    }
}
