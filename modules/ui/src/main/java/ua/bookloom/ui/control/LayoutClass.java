package ua.bookloom.ui.control;

import java.util.Objects;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.WeakChangeListener;
import javafx.css.PseudoClass;
import javafx.scene.Node;
import javafx.scene.layout.Region;
import org.jspecify.annotations.Nullable;

/**
 * The one place that decides how the window's parts arrange themselves for the width they have.
 *
 * <p>An area installed as a host (the window's root and the content area) is classified compact, regular or wide and
 * carries the class to CSS as a pseudo-class, so a stylesheet rule can differ per width without a media query. A node
 * inside reads its area's class with {@link #of} and hears of a change through {@link #follow}; a control that decides
 * from its own content (two columns that stack, a form whose labels go above) asks {@link #isNarrow}. Every decision
 * follows that one rule: the narrow form comes as soon as the width is below what the wide form needs, so nothing is
 * cut, and the wide form comes back only 16 pixels past it, at least a scrollbar's width, so a scrollbar or a minimum
 * that changes with the layout cannot flip it to and fro. A class is entered at its floor and kept until 16 pixels
 * below it. Nothing is logged here: the width listener runs on every layout pass that resizes the host.
 */
public final class LayoutClass {

    /** The width classes, narrowest first. */
    public enum Width {
        /** Below 1040 pixels: the smallest windows; pairs of cards stack. */
        COMPACT("compact", 0),
        /** From 1040 up to 1439 pixels. */
        REGULAR("regular", 1040),
        /** From 1440 pixels: room for the widest arrangements. */
        WIDE("wide", 1440);

        private final String pseudoClass;
        private final double floor;

        Width(final String pseudoClass, final double floor) {
            this.pseudoClass = pseudoClass;
            this.floor = floor;
        }

        PseudoClass pseudoClass() {
            return PseudoClass.getPseudoClass(pseudoClass);
        }

        /** {@return the smallest width of the class, in pixels} */
        public double floor() {
            return floor;
        }

        /**
         * Whether this class is narrower than another.
         *
         * @param other the class to compare with
         * @return {@code true} if this class's floor is below the other's, {@code false} otherwise
         */
        public boolean isBelow(final Width other) {
            return floor < other.floor;
        }
    }

    private static final double HYSTERESIS = 16;
    private static final String HOST_KEY = LayoutClass.class.getName() + ".host";
    private static final String FOLLOWER_KEY = LayoutClass.class.getName() + ".follower";

    private final Region host;
    private final ReadOnlyObjectWrapper<Width> current = new ReadOnlyObjectWrapper<>(Width.COMPACT);

    private LayoutClass(final Region host) {
        this.host = host;
    }

    /**
     * Starts classifying a region's width and keeps exactly one of the pseudo-classes {@code compact}, {@code regular}
     * and {@code wide} set on it.
     *
     * @param host the region whose width is the area's; it holds the classifier, so the result may be dropped
     * @return the live class, for code that needs the value as well as the style
     */
    public static LayoutClass install(final Region host) {
        Objects.requireNonNull(host, "host");
        final LayoutClass layout = new LayoutClass(host);
        host.getProperties().put(HOST_KEY, layout);
        host.widthProperty().addListener((observed, was, now) -> layout.update(now.doubleValue()));
        layout.update(host.getWidth());
        return layout;
    }

    /**
     * The class now in force.
     *
     * @return a read-only property, {@link Width#COMPACT} until the host has a width
     */
    public ReadOnlyObjectProperty<Width> widthClass() {
        return current.getReadOnlyProperty();
    }

    /**
     * The rule every responsive decision follows.
     *
     * @param wasNarrow whether the narrow form is shown now
     * @param width the width there is, in pixels
     * @param wideNeeds the width the wide form needs, in pixels
     * @return {@code true} if the narrow form is to be shown: at once below {@code wideNeeds}, and while narrow until
     *     the width is 16 pixels past it
     */
    public static boolean isNarrow(final boolean wasNarrow, final double width, final double wideNeeds) {
        return width < (wasNarrow ? wideNeeds + HYSTERESIS : wideNeeds);
    }

    /**
     * The class of the area a node sits in: that of the nearest host at or above it.
     *
     * @param node any node
     * @return the class, or {@link Width#WIDE} for a node in no host (a part shown on its own has all the room it is
     *     given)
     */
    public static Width of(final Node node) {
        Objects.requireNonNull(node, "node");
        final LayoutClass host = hostOf(node);
        return host == null ? Width.WIDE : host.current.get();
    }

    /**
     * Runs {@code onChange} whenever the class of the area a node sits in changes, and once each time the node enters
     * a scene, so the node can arrange itself again. One follower per node; the host holds it weakly.
     *
     * @param node the node that arranges itself
     * @param onChange what to run; it reads the class with {@link #of}
     */
    public static void follow(final Node node, final Runnable onChange) {
        Objects.requireNonNull(node, "node");
        Objects.requireNonNull(onChange, "onChange");
        final Follower follower = new Follower(node, onChange);
        node.getProperties().put(FOLLOWER_KEY, follower);
        node.sceneProperty().addListener((observed, was, now) -> follower.attach());
        follower.attach();
    }

    /**
     * The class for a width, given the class held before it.
     *
     * @param previous the class in force, or {@code null} when there is none yet
     * @param width the area's width in pixels
     * @return the widest class the width reaches: a class is entered at its floor and kept until 16 px below it, the
     *     one {@link #isNarrow} rule with the floor as the point the class is lost
     */
    static Width next(final @Nullable Width previous, final double width) {
        Width found = Width.COMPACT;
        for (final Width candidate : Width.values()) {
            final boolean wasBelow = previous == null || previous.isBelow(candidate);
            // A class floor is where the wider class is reached at once; it is kept until 16 px below the floor.
            if (!isNarrow(wasBelow, width, candidate.floor() - HYSTERESIS)) {
                found = candidate;
            }
        }
        return found;
    }

    private static @Nullable LayoutClass hostOf(final Node node) {
        for (Node at = node; at != null; at = at.getParent()) {
            if (at.getProperties().get(HOST_KEY) instanceof LayoutClass host) {
                return host;
            }
        }
        return null;
    }

    private void update(final double width) {
        final Width now = next(current.get(), width);
        for (final Width each : Width.values()) {
            host.pseudoClassStateChanged(each.pseudoClass(), each == now);
        }
        current.set(now);
    }

    /** A node's tie to the host it sits in, moved whenever the node changes scene. */
    private static final class Follower {
        private final Node node;
        private final ChangeListener<Width> onClass;
        private final WeakChangeListener<Width> weak;
        private final Runnable onChange;
        private @Nullable LayoutClass host;

        Follower(final Node node, final Runnable onChange) {
            this.node = node;
            this.onChange = onChange;
            this.onClass = (observed, was, now) -> onChange.run();
            this.weak = new WeakChangeListener<>(onClass);
        }

        void attach() {
            final LayoutClass was = host;
            if (was != null) {
                was.current.removeListener(weak);
            }
            host = node.getScene() == null ? null : hostOf(node);
            final LayoutClass now = host;
            if (now != null) {
                now.current.addListener(weak);
            }
            if (node.getScene() != null) {
                onChange.run();
            }
        }
    }
}
