package ua.bookloom.ui.control;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javafx.event.Event;
import javafx.event.EventHandler;
import javafx.scene.Node;
import javafx.scene.input.ScrollEvent;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;

/**
 * Keeps a trackpad gesture going when the node it started on is replaced (a row rebuilt, a panel swapped) before the
 * gesture and its momentum end.
 *
 * <p>JavaFX fires every event of a gesture, momentum included, at the node picked at its start. Once that node has
 * left the scene its event route no longer passes the scene, so neither {@link ScrollFilter} nor any scroll pane sees
 * the rest of the gesture and the view stops dead under the finger. This filter, installed beside {@link ScrollFilter},
 * records each gesture's target and the scrollers above it at the start, and puts a filter on the target itself: while
 * the target is in the scene that filter does nothing; once it is not, it consumes each event and fires a copy at the
 * nearest recorded scroller still in the scene, where {@link ScrollFilter} moves it as usual. Each such event is
 * counted as "target detached" in {@link ScrollStats}. Only the last gesture's target is held, until the next gesture
 * starts. It runs on the FX thread per event, so it logs nothing per event above TRACE.
 */
@Slf4j
final class GestureRescue {

    private final Node root;
    private final ScrollStats stats;
    private final EventHandler<ScrollEvent> onTarget = this::onTargetEvent;
    private @Nullable Node target;
    private List<Node> scrollers = List.of();

    GestureRescue(final Node root, final ScrollStats stats) {
        this.root = Objects.requireNonNull(root, "root");
        this.stats = Objects.requireNonNull(stats, "stats");
    }

    void onGestureStart(final ScrollEvent event) {
        release();
        if (!(event.getTarget() instanceof Node start)) {
            return;
        }
        target = start;
        scrollers = scrollersAbove(start);
        start.addEventFilter(ScrollEvent.ANY, onTarget);
        if (stats.isPerEvent() && log.isTraceEnabled()) {
            log.trace("gesture started on {} under {} scroller(s)", start, scrollers.size());
        }
    }

    private void release() {
        final Node previous = target;
        if (previous != null) {
            previous.removeEventFilter(ScrollEvent.ANY, onTarget);
        }
        target = null;
        scrollers = List.of();
    }

    private List<Node> scrollersAbove(final Node start) {
        final List<Node> found = new ArrayList<>();
        for (Node node = start; node != null; node = node.getParent()) {
            if (Scroller.of(node) != null) {
                found.add(node);
            }
            if (node.equals(root)) {
                break;
            }
        }
        return List.copyOf(found);
    }

    private void onTargetEvent(final ScrollEvent event) {
        final Node gestureTarget = target;
        if (gestureTarget == null || event.getEventType() == ScrollEvent.SCROLL_STARTED || isUnderRoot(gestureTarget)) {
            return;
        }
        stats.targetDetached();
        final Node rescuer = nearestInScene();
        final ScrollEvent copy = event.copyFor(rescuer, rescuer);
        event.consume();
        if (stats.isPerEvent() && log.isTraceEnabled()) {
            log.trace("gesture target {} left the scene; event goes to {}", gestureTarget, rescuer);
        }
        Event.fireEvent(rescuer, copy);
    }

    // The root itself is the last resort: it is in the scene whenever this filter can matter at all.
    private Node nearestInScene() {
        return scrollers.stream().filter(this::isUnderRoot).findFirst().orElse(root);
    }

    private boolean isUnderRoot(final Node node) {
        for (Node at = node; at != null; at = at.getParent()) {
            if (at.equals(root)) {
                return true;
            }
        }
        return false;
    }
}
