package ua.bookloom.ui.control;

import java.util.Objects;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.WeakChangeListener;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.ScrollPane;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;

/**
 * Keeps a scroll pane's content at the distance from the top the person scrolled it to while the content grows and
 * shrinks under them, so a screen that refreshes as a run goes on never jumps.
 *
 * <p>JavaFX keeps the pixel distance while the content's height changes, but only within the new range: when the
 * content is briefly shorter (a live row hidden between two segments, a card swapped for another), the pane is clamped
 * to its new end and stays there when the content grows back, so the view lands somewhere else. This class remembers
 * where the person put the view — every change of the value while the content keeps its height is theirs — and puts it
 * back whenever the content's height or the viewport changes. Its listeners run on every scroll, so they log nothing.
 *
 * <p>One change of the value is not the person's although the height stays: when the focused control goes away (a
 * banner's action hidden once the model answers, a row's control replaced), JavaFX moves the focus to the next control
 * and the pane scrolls that one into view, which can be a screen away. From the moment the focus leaves a control that
 * lost its place until the next pulse, the anchor puts the view back where the person had it.
 */
@Slf4j
public final class ScrollAnchor {

    private final ScrollPane pane;
    private double offset;
    private double knownHeight;
    private boolean restoring;
    private boolean attached;
    private boolean holding;
    private @Nullable Scene watched;
    private final ChangeListener<@Nullable Node> onFocus = (observed, was, now) -> focusMoved(was);

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
        pane.sceneProperty().addListener((observed, was, now) -> anchor.watchFocus(now));
        anchor.attach();
        anchor.watchFocus(pane.getScene());
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

    // The scene outlives a shell rebuilt in it, so it holds this anchor's listener weakly.
    private void watchFocus(final @Nullable Scene scene) {
        if (scene == null || scene.equals(watched)) {
            return;
        }
        watched = scene;
        scene.focusOwnerProperty().addListener(new WeakChangeListener<>(onFocus));
    }

    private void focusMoved(final @Nullable Node was) {
        if (was == null || holding || !hasLostItsPlace(was)) {
            return;
        }
        log.debug("the focused {} of {} went away: keeping the view where it was", was.getId(), pane.getId());
        holding = true;
        Platform.runLater(() -> holding = false);
    }

    private static boolean hasLostItsPlace(final Node node) {
        if (node.getScene() == null || node.isDisabled()) {
            return true;
        }
        Node walk = node;
        while (walk != null) {
            if (!walk.isVisible()) {
                return true;
            }
            walk = walk.getParent();
        }
        return false;
    }

    private void onBounds(final Bounds now) {
        if (now.getHeight() != knownHeight) {
            knownHeight = now.getHeight();
            restore();
        }
    }

    private void onValue() {
        if (restoring) {
            return;
        }
        if (holding) {
            restore();
            return;
        }
        final Node content = pane.getContent();
        if (content == null || content.getLayoutBounds().getHeight() != knownHeight) {
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
