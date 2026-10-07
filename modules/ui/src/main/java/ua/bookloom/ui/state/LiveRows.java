package ua.bookloom.ui.state;

import org.jspecify.annotations.Nullable;

/**
 * The live panel's two rows.
 *
 * @param lastDecided the segment decided most recently, or {@code null} before the first decision
 * @param current the oldest segment started and not yet decided, which the run is working on, or {@code null}
 *     between segments
 */
public record LiveRows(
        @Nullable LiveRow lastDecided, @Nullable LiveRow current) {

    /** The panel of a run that has announced nothing. */
    public static final LiveRows EMPTY = new LiveRows(null, null);
}
