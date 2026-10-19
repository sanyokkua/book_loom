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
 * then the foldable parts move into a menu the row shows in their place, one stage at a time in the order the stages
 * were named; then the droppable parts are hidden, the first named first; whatever is left shrinks only the elastic
 * part further, down to its own minimum width.
 *
 * <p>The owner says whether it wants a droppable part shown through {@link #want}; the row shows it only while it also
 * fits. The row asks its parent for room for every wanted part, so a window made wider again brings the hidden parts
 * back. Nothing is logged here: the row is laid out on every pulse that changes a text in it.
 */
public final class PriorityRow extends HBox {

    private final List<Node> dropOrder = new ArrayList<>();
    private final Map<Node, Boolean> wanted = new HashMap<>();
    private final Set<Node> dropped = new HashSet<>();
    private final List<List<Node>> foldStages = new ArrayList<>();
    private @Nullable Node foldMenu;
    private boolean folded;
    private @Nullable Region elastic;
    private double comfort;

    /** What the row shows for the room it has: the parts hidden, and whether the foldable ones sit in the menu. */
    private record Plan(Set<Node> hidden, boolean folded) {}

    /**
     * Builds an empty row.
     *
     * @param spacing the gap between two shown parts
     */
    public PriorityRow(final double spacing) {
        super(spacing);
    }

    /**
     * Names the part that gives way first and how far it gives way before any part is hidden.
     *
     * @param part a child of this row whose minimum width is zero
     * @param comfortWidth the width below which hiding other parts is preferred to shrinking it further
     */
    public void elastic(final Region part, final double comfortWidth) {
        elastic = Objects.requireNonNull(part, "part");
        comfort = comfortWidth;
    }

    /**
     * Names the parts that may be hidden whole, the one hidden first named first.
     *
     * @param parts children of this row
     */
    public void droppable(final Node... parts) {
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
    public void want(final Node part, final boolean shown) {
        // The owner repeats its wish on every refresh; only a change of it may lay the row out again.
        if (Boolean.valueOf(shown).equals(wanted.get(part))) {
            return;
        }
        wanted.put(part, shown);
        apply(part);
        requestLayout();
    }

    /**
     * Names parts that, when the row is too narrow for all of them, leave together and are replaced by a menu that
     * holds their actions. This happens before any droppable part is hidden, so what a person uses most stays. A
     * second call names a later stage, folded only when the first stage's departure was not enough, into the same
     * menu.
     *
     * @param menu a child of this row, shown only while some parts are folded away
     * @param parts children of this row, always wanted
     */
    public void fold(final Node menu, final Node... parts) {
        foldMenu = Objects.requireNonNull(menu, "menu");
        final List<Node> stage = new ArrayList<>();
        for (final Node part : parts) {
            stage.add(part);
            wanted.put(part, part.isVisible());
        }
        foldStages.add(List.copyOf(stage));
        wanted.put(menu, false);
        apply(menu);
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
        final Plan plan = plan(getWidth() - snappedLeftInset() - snappedRightInset());
        if (!plan.hidden().equals(dropped) || plan.folded() != folded) {
            dropped.clear();
            dropped.addAll(plan.hidden());
            folded = plan.folded();
            dropOrder.forEach(this::apply);
            foldStages.forEach(stage -> stage.forEach(this::apply));
            if (foldMenu != null) {
                apply(foldMenu);
            }
        }
        super.layoutChildren();
    }

    // The first fold stage leaves when the elastic part would have to go below its comfort width; each later stage
    // only when it would have to go below its own minimum width, so the part is never squeezed to nothing.
    private Plan plan(final double room) {
        final Set<Node> hidden = new HashSet<>();
        boolean fold = false;
        for (int stage = 0; stage < foldStages.size(); stage++) {
            final double elasticLimit = stage == 0 ? comfort : elasticFloor();
            if (needed(hidden, fold, elasticLimit) <= room) {
                break;
            }
            final List<Node> leaving =
                    foldStages.get(stage).stream().filter(this::isWanted).toList();
            hidden.addAll(leaving);
            fold = fold || !leaving.isEmpty();
        }
        for (final Node part : dropOrder) {
            if (needed(hidden, fold, comfort) <= room) {
                break;
            }
            if (isWanted(part)) {
                hidden.add(part);
            }
        }
        return new Plan(hidden, fold);
    }

    private double elasticFloor() {
        return elastic == null ? comfort : Math.min(comfort, snapSizeX(elastic.minWidth(-1)));
    }

    private double needed(final Set<Node> hidden, final boolean menuShown, final double elasticLimit) {
        double need = 0;
        int count = 0;
        for (final Node part : getChildren()) {
            final boolean counted = part.equals(foldMenu) ? menuShown : isWanted(part) && !hidden.contains(part);
            if (counted) {
                need += part.equals(elastic)
                        ? Math.min(snapSizeX(part.prefWidth(-1)), elasticLimit)
                        : snapSizeX(part.prefWidth(-1));
                count++;
            }
        }
        return need + gaps(count);
    }

    private boolean isWanted(final Node part) {
        final Boolean wish = wanted.get(part);
        return wish == null ? part.isManaged() : wish;
    }

    private void apply(final Node part) {
        final boolean show =
                part.equals(foldMenu) ? folded : Boolean.TRUE.equals(wanted.get(part)) && !dropped.contains(part);
        part.setVisible(show);
        part.setManaged(show);
    }

    private double gaps(final int count) {
        // Sized as HBox sizes its own children (each width and gap snapped to whole pixels), so the room asked for is
        // exactly the room the row's layout needs and the elastic part is never shortened by a rounding difference.
        return count > 1 ? snapSpaceX(getSpacing()) * (count - 1) : 0;
    }
}
