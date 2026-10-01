package ua.bookloom.ui.control;

import java.util.Objects;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.control.ScrollPane;
import lombok.extern.slf4j.Slf4j;

/**
 * Keeps a scroll pane's content at the distance from the top the person scrolled it to while the content grows and
 * shrinks under them, so a screen that refreshes as a run goes on never jumps.
 *
 * <p>JavaFX keeps the pixel distance while the content's height changes, but only within the new range: when the
 * content is briefly shorter (a live row hidden between two segments, a card swapped for another), the pane is clamped
 * to its new end and stays there when the content grows back, so the view lands somewhere else. This class remembers
 * where the person put the view — every change of the value while the content keeps its height is theirs — and puts it
 * back whenever the content's height or the viewport changes. Its listeners run on every scroll, so they log nothing.
 */
@Slf4j
public final class ScrollAnchor {

    private final ScrollPane pane;
    private double offset;
    private double knownHeight;
    private boolean restoring;
    private boolean attached;

    private ScrollAnchor(final ScrollPane pane) {
        this.pane = pane;
    }

    /**
     * Anchors a pane whose content node never changes.
     *
     * @param pane the pane; its content is observed once the pane has a skin, so that this class hears of a height
     *     change after the skin has made its own adjustment
     * @return the anchor, whose {@link #reset()} moves the view back to the top
     */
    public static ScrollAnchor install(final ScrollPane pane) {
        Objects.requireNonNull(pane, "pane");
        final ScrollAnchor anchor = new ScrollAnchor(pane);
        pane.vvalueProperty().addListener(observed -> anchor.onValue());
        pane.viewportBoundsProperty().addListener(observed -> anchor.restore());
        pane.skinProperty().addListener((observed, was, now) -> anchor.attach());
        anchor.attach();
        return anchor;
    }

    /** Moves the view to the top and forgets the distance kept, as a new screen in the pane must start there. */
    public void reset() {
        log.debug("scroll anchor of {} reset to the top", pane.getId());
        offset = 0;
        restoring = true;
        try {
            pane.setVvalue(pane.getVmin());
        } finally {
            restoring = false;
        }
    }

    private void attach() {
        final Node content = pane.getContent();
        if (attached || pane.getSkin() == null || content == null) {
            return;
        }
        attached = true;
        knownHeight = content.getLayoutBounds().getHeight();
        content.layoutBoundsProperty().addListener((observed, was, now) -> onBounds(now));
    }

    private void onBounds(final Bounds now) {
        if (now.getHeight() != knownHeight) {
            knownHeight = now.getHeight();
            restore();
        }
    }

    private void onValue() {
        final Node content = pane.getContent();
        if (restoring || content == null || content.getLayoutBounds().getHeight() != knownHeight) {
            return;
        }
        final double range = pane.getVmax() - pane.getVmin();
        final double overflow = overflow();
        offset = range <= 0 || overflow <= 0 ? 0 : (pane.getVvalue() - pane.getVmin()) / range * overflow;
    }

    private void restore() {
        final double overflow = overflow();
        final double range = pane.getVmax() - pane.getVmin();
        if (overflow <= 0 || range <= 0) {
            return;
        }
        restoring = true;
        try {
            pane.setVvalue(pane.getVmin() + Math.clamp(offset / overflow, 0, 1) * range);
        } finally {
            restoring = false;
        }
    }

    private double overflow() {
        final Node content = pane.getContent();
        return content == null ? 0 : knownHeight - pane.getViewportBounds().getHeight();
    }
}
