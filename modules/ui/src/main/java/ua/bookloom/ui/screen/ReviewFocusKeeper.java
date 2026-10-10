package ua.bookloom.ui.screen;

import java.util.Objects;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableBooleanValue;
import javafx.beans.value.WeakChangeListener;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.ui.control.ScrollAnchor;

/**
 * Gives a review panel's focus back when a retry has run. While the retry is in flight the panel's buttons are
 * disabled, which hands the focus to a control somewhere else on the page; once it is over the keeper looks the control
 * the person had up by its id (the compare is rewritten for the new text, so the node may be another one) and focuses
 * it with the page held where it was.
 *
 * <p>The in-flight flag outlives the panel, so it is observed weakly through a listener this keeper holds in a field,
 * and the keeper is kept alive by the panel's properties. Nothing here logs on a high-frequency path: it fires twice per retry.
 */
@Slf4j
final class ReviewFocusKeeper {

    private final Parent panel;
    private @Nullable String focusedId;
    private @Nullable Scene watched;
    private final ChangeListener<Boolean> onFlight = (observed, was, now) -> flight(now);
    private final ChangeListener<@Nullable Node> onFocus = (observed, was, now) -> moved(was, now);

    /** Keeps the focus of a panel across its retries; the panel holds the keeper, as the listeners hold it weakly. */
    static void install(final Parent panel, final ObservableBooleanValue retryInFlight) {
        panel.getProperties().put(ReviewFocusKeeper.class, new ReviewFocusKeeper(panel, retryInFlight));
    }

    private ReviewFocusKeeper(final Parent panel, final ObservableBooleanValue retryInFlight) {
        this.panel = Objects.requireNonNull(panel, "panel");
        Objects.requireNonNull(retryInFlight, "retryInFlight").addListener(new WeakChangeListener<>(onFlight));
        panel.sceneProperty().addListener((observed, was, now) -> watch(now));
        watch(panel.getScene());
    }

    // The scene outlives a panel rebuilt in it, so it holds this keeper's listener weakly.
    private void watch(final @Nullable Scene scene) {
        if (scene != null && !scene.equals(watched)) {
            watched = scene;
            scene.focusOwnerProperty().addListener(new WeakChangeListener<>(onFocus));
        }
    }

    // The control the person last focused in the panel. A move off a control that was just disabled is the scene's
    // doing, not the person's, so it is not taken; not logged, it follows every focus change.
    private void moved(final @Nullable Node was, final @Nullable Node now) {
        final String entered = idInPanel(now);
        if (entered != null) {
            focusedId = entered;
        } else if (was != null && !was.isDisabled()) {
            focusedId = null;
        }
    }

    private @Nullable String idInPanel(final @Nullable Node node) {
        if (node == null) {
            return null;
        }
        Node walk = node;
        while (walk != null) {
            if (walk.equals(panel)) {
                return node.getId();
            }
            walk = walk.getParent();
        }
        return null;
    }

    private void flight(final boolean inFlight) {
        if (!inFlight && focusedId != null) {
            final String id = focusedId;
            log.debug("a retry ended; the focus was on {}", id);
            // After the buttons are enabled again, which this change of the flag causes in the same pulse.
            Platform.runLater(() -> restore(id));
        }
    }

    private void restore(final String id) {
        final Node control = panel.lookup("#" + id);
        if (control == null || control.isDisabled() || !control.isVisible() || control.getScene() == null) {
            log.debug("the control {} to focus after the retry is gone or not usable", id);
            return;
        }
        log.debug("giving the focus back to {} after the retry", id);
        ScrollAnchor.holdWhile(control, control::requestFocus);
    }
}
