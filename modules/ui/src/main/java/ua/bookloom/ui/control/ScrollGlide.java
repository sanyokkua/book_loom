package ua.bookloom.ui.control;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * The arithmetic behind smooth wheel scrolling, kept apart from the scene graph so each rule is checked on its own.
 *
 * <p>A wheel notch is turned into a distance in pixels and glided over instead of jumped; notches that arrive while a
 * glide is still running add to what is left of it, the way a browser keeps up with a fast spin. A trackpad already
 * scrolls in small, continuous steps with its own momentum, so its events are left to JavaFX.
 */
// Checkstyle's HideUtilityClassConstructor parses source text before Lombok's annotation processor runs, so it
// cannot see the private constructor @NoArgsConstructor generates (ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class ScrollGlide {

    /**
     * Whether a scroll event comes from a mouse wheel rather than a trackpad or a touch screen.
     *
     * @param direct whether the event comes from a touch screen
     * @param inertia whether the event is the momentum that follows a trackpad gesture
     * @param inGesture whether the event falls between the start and the end of a trackpad gesture
     * @param touchCount the number of fingers on the device, zero for a wheel
     * @param deltaY the vertical distance in pixels
     * @return {@code true} if the event is a wheel notch to glide, {@code false} if JavaFX should handle it as it comes
     */
    static boolean isDiscreteWheel(
            final boolean direct,
            final boolean inertia,
            final boolean inGesture,
            final int touchCount,
            final double deltaY) {
        return !direct && !inertia && !inGesture && touchCount == 0 && deltaY != 0;
    }

    /**
     * The distance a wheel event moves the view, in pixels: positive is down the content.
     *
     * @param deltaY the event's vertical distance, positive when the wheel turns up (towards the top of the content)
     * @return the distance to move, the same length JavaFX would jump
     */
    static double pixelsOf(final double deltaY) {
        return -deltaY;
    }

    /**
     * What a glide still has to travel after a further notch.
     *
     * @param pending the distance the running glide has not travelled yet, zero when none runs
     * @param added the new notch's distance
     * @return the sum when both go the same way; only the new notch when it turns back, so a reversal is answered at
     *     once instead of first finishing the old way
     */
    static double accumulate(final double pending, final double added) {
        return Math.signum(pending) == -Math.signum(added) ? added : pending + added;
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
