package ua.bookloom.ui.control;

import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.control.IndexedCell;
import javafx.scene.control.ScrollBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.skin.VirtualFlow;
import javafx.scene.input.ScrollEvent;
import org.jspecify.annotations.Nullable;

/** A view that can be moved by a distance in pixels: a scroll pane or the vertical flow of a list, table or tree. */
sealed interface Scroller permits Scroller.PaneScroller, Scroller.FlowScroller {

    /** The scroller for a node, or {@code null} when the node scrolls nothing vertically. */
    static @Nullable Scroller of(final Node node) {
        return switch (node) {
            case ScrollPane pane -> new PaneScroller(pane);
            case VirtualFlow<?> flow when flow.isVertical() -> new FlowScroller(flow);
            default -> null;
        };
    }

    Node node();

    /** How far JavaFX would move this view for the event, positive down the content. */
    double pixelsOf(ScrollEvent event);

    /**
     * Whether the view can still move the way {@code pixels} points.
     *
     * @param pixels the distance asked for, positive down the content
     * @param pending pixels already collected for this view and not yet moved, positive down the content; a view whose
     *     pending distance already reaches its end has no room, so the event goes on to the view around it
     */
    boolean hasRoom(double pixels, double pending);

    /** The visible height, which bounds how far a glide may run ahead. */
    double viewport();

    /** Moves by up to {@code pixels} and answers how far the view really moved. */
    double moveBy(double pixels);

    /** A scroll pane. */
    record PaneScroller(ScrollPane node) implements Scroller {

        private double overflow() {
            final Node content = node.getContent();
            return content == null
                    ? 0
                    : content.getLayoutBounds().getHeight()
                            - node.getViewportBounds().getHeight();
        }

        @Override
        public double pixelsOf(final ScrollEvent event) {
            return ScrollGlide.panePixels(event.getDeltaY());
        }

        @Override
        public boolean hasRoom(final double pixels, final double pending) {
            final double overflow = overflow();
            final double range = node.getVmax() - node.getVmin();
            if (overflow <= 0 || range <= 0 || pixels == 0) {
                return false;
            }
            final double fraction = pixels > 0 ? node.getVmax() - node.getVvalue() : node.getVvalue() - node.getVmin();
            final double already = Math.max(0, pending * Math.signum(pixels));
            return fraction / range * overflow > already;
        }

        @Override
        public double viewport() {
            return node.getViewportBounds().getHeight();
        }

        @Override
        public double moveBy(final double pixels) {
            final double overflow = overflow();
            final double before = node.getVvalue();
            final double after = ScrollGlide.paneValue(before, node.getVmin(), node.getVmax(), pixels, overflow);
            node.setVvalue(after);
            final double range = node.getVmax() - node.getVmin();
            return range <= 0 ? 0 : (after - before) * overflow / range;
        }
    }

    /** The vertical flow of a list, table or tree. */
    record FlowScroller(VirtualFlow<?> node) implements Scroller {

        @Override
        public double pixelsOf(final ScrollEvent event) {
            return ScrollGlide.flowPixels(event.getTextDeltaY(), ScrollGlide.lineSize(cellSize(), viewport()));
        }

        // JavaFX measures a line by the fixed cell size, else by the average height of the cells it has laid out.
        private double cellSize() {
            if (node.getFixedCellSize() > 0) {
                return node.getFixedCellSize();
            }
            final IndexedCell<?> first = node.getFirstVisibleCell();
            final IndexedCell<?> last = node.getLastVisibleCell();
            if (first == null || last == null || last.getIndex() < first.getIndex()) {
                return 0;
            }
            final double span = last.getLayoutY() + last.getHeight() - first.getLayoutY();
            return span / (last.getIndex() - first.getIndex() + 1);
        }

        // The flow's total length is not public, so only its position (not the pending distance) tells it has room.
        @Override
        public boolean hasRoom(final double pixels, final double pending) {
            final boolean scrolls = node.getChildrenUnmodifiable().stream()
                    .anyMatch(child -> child instanceof ScrollBar bar
                            && bar.getOrientation() == Orientation.VERTICAL
                            && bar.isVisible());
            return scrolls && ScrollGlide.hasRoom(node.getPosition(), 0, 1, pixels);
        }

        @Override
        public double viewport() {
            return node.getHeight();
        }

        @Override
        public double moveBy(final double pixels) {
            return node.scrollPixels(pixels);
        }
    }
}
