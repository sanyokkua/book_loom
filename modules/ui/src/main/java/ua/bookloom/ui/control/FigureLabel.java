package ua.bookloom.ui.control;

import javafx.scene.control.Label;
import javafx.scene.layout.Region;

/**
 * A label for a figure that changes every second or so — a call's clock, the time elapsed and left, a rate — whose
 * new text lays out only the label itself, not the screen around it.
 *
 * <p>A plain label asks its parent, and so every ancestor up to the window, to lay out again whenever its text changes,
 * which re-measures a whole screen once a second during a run. This label reserves the widest width any of its texts
 * has needed (plus room for a wider digit) and, while a new text still fits in it, marks only itself as needing a
 * layout pass: it is its own layout root for a change of text. A text wider than the reservation widens it once and
 * asks the parent as a plain label would. Single-line texts only; the reservation never shrinks.
 */
public final class FigureLabel extends Label {

    // Digits of most fonts are tabular, but not of all; half an em absorbs a wider digit without a relayout.
    private static final double DIGIT_ROOM_EM = 0.5;

    private double reserved;

    /** Builds an empty label. */
    public FigureLabel() {
        setMinWidth(Region.USE_PREF_SIZE);
        setMaxWidth(Region.USE_PREF_SIZE);
    }

    @Override
    public void requestLayout() {
        if (reserved > 0 && getParent() != null) {
            // The parent's view of this label's size stays as it is; the label lays its text out in its own pass and
            // widens the reservation there if the text no longer fits.
            setNeedsLayout(true);
            return;
        }
        super.requestLayout();
    }

    @Override
    protected double computePrefWidth(final double height) {
        reserved = Math.max(reserved, Math.ceil(natural(height)));
        return reserved;
    }

    @Override
    protected void layoutChildren() {
        if (Math.ceil(natural(-1)) > reserved) {
            reserved = 0;
            super.requestLayout();
        }
        super.layoutChildren();
    }

    private double natural(final double height) {
        return super.computePrefWidth(height) + getFont().getSize() * DIGIT_ROOM_EM;
    }
}
