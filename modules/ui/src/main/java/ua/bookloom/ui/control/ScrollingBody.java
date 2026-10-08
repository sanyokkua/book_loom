package ua.bookloom.ui.control;

import javafx.geometry.Orientation;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.Region;

/**
 * The scrolling body of a context section: as tall as what it holds at the width it is given, up to
 * {@value #BODY_MAX} pixels, and scrolling inside beyond that.
 *
 * <p>A scroll pane measures its content at the content's own preferred width, which for wrapped text is one long
 * line, so the body asked for a line's height and showed one clipped line. It reports a horizontal content bias
 * instead, so the rows above pass it the width it is laid out at, and measures its content at that width; its
 * minimum is that height, so no parent can squeeze it.
 */
final class ScrollingBody extends ScrollPane {

    static final double BODY_MAX = 320;

    ScrollingBody(final Region content) {
        super(content);
        setFitToWidth(true);
        setHbarPolicy(ScrollBarPolicy.NEVER);
        setVbarPolicy(ScrollBarPolicy.AS_NEEDED);
        setMinHeight(Region.USE_PREF_SIZE);
        setMaxHeight(Region.USE_PREF_SIZE);
        setFocusTraversable(false);
    }

    @Override
    public Orientation getContentBias() {
        return Orientation.HORIZONTAL;
    }

    @Override
    protected double computePrefHeight(final double width) {
        final double sides = snappedLeftInset() + snappedRightInset();
        final double inner = width < 0 ? -1 : Math.max(0, width - sides);
        final double content = getContent().prefHeight(inner);
        return Math.min(content + snappedTopInset() + snappedBottomInset(), BODY_MAX);
    }
}
