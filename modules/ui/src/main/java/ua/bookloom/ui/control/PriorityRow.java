package ua.bookloom.ui.control;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import javafx.scene.Node;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import org.jspecify.annotations.Nullable;

/**
 * A row that, when narrower than its parts want, hides whole parts in a set order instead of squeezing every one of
 * them into an unreadable stub. One elastic part gives way first, down to a comfortable width (it ends in an ellipsis);
 * then the droppable parts are hidden, the first named first; whatever is left shrinks only the elastic part further.
 *
 * <p>The owner says whether it wants a droppable part shown through {@link #want}; the row shows it only while it also
 * fits. The row asks its parent for room for every wanted part, so a window made wider again brings the hidden parts
 * back. Nothing is logged here: the row is laid out on every pulse that changes a text in it.
 */
final class PriorityRow extends HBox {

    private final List<Node> dropOrder = new ArrayList<>();
    private final Map<Node, Boolean> wanted = new HashMap<>();
    private final Set<Node> dropped = new HashSet<>();
    private @Nullable Region elastic;
    private double comfort;

    PriorityRow(final double spacing) {
        super(spacing);
    }

    /**
     * Names the part that gives way first and how far it gives way before any part is hidden.
     *
     * @param part a child of this row whose minimum width is zero
     * @param comfortWidth the width below which hiding other parts is preferred to shrinking it further
     */
    void elastic(final Region part, final double comfortWidth) {
        elastic = Objects.requireNonNull(part, "part");
        comfort = comfortWidth;
    }

    /**
     * Names the parts that may be hidden whole, the one hidden first named first.
     *
     * @param parts children of this row
     */
    void droppable(final Node... parts) {
        for (final Node part : parts) {
            dropOrder.add(part);
            wanted.put(part, part.isVisible());
        }
    }

    /**
     * Whether the owner wants a droppable part shown; it shows while it also fits.
     *
     * @param part a part named by {@link #droppable}
     * @param shown {@code true} if the owner wants it shown
     */
    void want(final Node part, final boolean shown) {
        // The owner repeats its wish on every refresh; only a change of it may lay the row out again.
        if (Boolean.valueOf(shown).equals(wanted.get(part))) {
            return;
        }
        wanted.put(part, shown);
        apply(part);
        requestLayout();
    }

    @Override
    protected double computePrefWidth(final double height) {
        double sum = 0;
        int count = 0;
        for (final Node part : getChildren()) {
            if (isWanted(part)) {
                sum += snapSizeX(part.prefWidth(height));
                count++;
            }
        }
        return snappedLeftInset() + snappedRightInset() + sum + gaps(count);
    }

    @Override
    protected double computeMinWidth(final double height) {
        double sum = 0;
        int count = 0;
        for (final Node part : getChildren()) {
            if (isWanted(part) && !wanted.containsKey(part)) {
                sum += snapSizeX(part.minWidth(height));
                count++;
            }
        }
        return snappedLeftInset() + snappedRightInset() + sum + gaps(count);
    }

    @Override
    protected void layoutChildren() {
        final Set<Node> drop = drops(getWidth() - snappedLeftInset() - snappedRightInset());
        if (!drop.equals(dropped)) {
            dropped.clear();
            dropped.addAll(drop);
            dropOrder.forEach(this::apply);
        }
        super.layoutChildren();
    }

    private Set<Node> drops(final double room) {
        double need = 0;
        int count = 0;
        for (final Node part : getChildren()) {
            if (isWanted(part)) {
                need += part.equals(elastic)
                        ? Math.min(snapSizeX(part.prefWidth(-1)), comfort)
                        : snapSizeX(part.prefWidth(-1));
                count++;
            }
        }
        need += gaps(count);
        final Set<Node> drop = new HashSet<>();
        for (final Node part : dropOrder) {
            if (need <= room) {
                break;
            }
            if (isWanted(part)) {
                drop.add(part);
                need -= snapSizeX(part.prefWidth(-1)) + snapSpaceX(getSpacing());
            }
        }
        return drop;
    }

    private boolean isWanted(final Node part) {
        final Boolean wish = wanted.get(part);
        return wish == null ? part.isManaged() : wish;
    }

    private void apply(final Node part) {
        final boolean show = Boolean.TRUE.equals(wanted.get(part)) && !dropped.contains(part);
        part.setVisible(show);
        part.setManaged(show);
    }

    private double gaps(final int count) {
        // Sized as HBox sizes its own children (each width and gap snapped to whole pixels), so the room asked for is
        // exactly the room the row's layout needs and the elastic part is never shortened by a rounding difference.
        return count > 1 ? snapSpaceX(getSpacing()) * (count - 1) : 0;
    }
}
