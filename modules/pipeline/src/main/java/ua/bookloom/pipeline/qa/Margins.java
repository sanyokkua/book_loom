package ua.bookloom.pipeline.qa;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** The one margin-clamping rule every passing soft check shares: a margin never exceeds 1.0. */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class Margins {

    private static final double MAX_MARGIN = 1.0;

    /**
     * Caps a computed margin at 1.0.
     *
     * @param value the computed margin; expected non-negative when a check calls this only on its passing branch
     * @return {@code value}, or {@code 1.0} when it exceeds it
     */
    static double clamp(final double value) {
        return Math.min(value, MAX_MARGIN);
    }
}
