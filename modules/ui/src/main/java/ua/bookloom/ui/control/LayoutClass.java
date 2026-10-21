package ua.bookloom.ui.control;

import java.util.Objects;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.css.PseudoClass;
import javafx.scene.layout.Region;
import org.jspecify.annotations.Nullable;

/**
 * Classifies the width of the content area as compact, regular or wide and carries the class to CSS as a pseudo-class
 * on the region, so a stylesheet rule can differ per width without a media query.
 *
 * <p>The class changes back only after the width has moved 16 pixels past the boundary it crossed, which is at least a
 * scrollbar's width: a scrollbar that appears at the edge of a screen just wide enough must not flip the layout.
 * Regions still decide their own two-column switch from their own width. Nothing is logged here: the width listener
 * runs on every layout pass that resizes the region.
 */
public final class LayoutClass {

    /** The width classes, narrowest first. */
    public enum Width {
        /** Below 1040 pixels. */
        COMPACT("compact", 0, null),
        /** From 1040 up to 1439 pixels. */
        REGULAR("regular", 1040, COMPACT),
        /** From 1440 pixels. */
        WIDE("wide", 1440, REGULAR);

        private final String pseudoClass;
        private final double floor;
        private final @Nullable Width narrower;

        Width(final String pseudoClass, final double floor, final @Nullable Width narrower) {
            this.pseudoClass = pseudoClass;
            this.floor = floor;
            this.narrower = narrower;
        }

        PseudoClass pseudoClass() {
            return PseudoClass.getPseudoClass(pseudoClass);
        }

        double floor() {
            return floor;
        }
    }

    private static final double HYSTERESIS = 16;

    private final Region host;
    private final ReadOnlyObjectWrapper<Width> current = new ReadOnlyObjectWrapper<>(Width.COMPACT);

    private LayoutClass(final Region host) {
        this.host = host;
    }

    /**
     * Starts classifying a region's width and keeps exactly one of the pseudo-classes {@code compact}, {@code regular}
     * and {@code wide} set on it.
     *
     * @param host the region whose width is the content area's; it holds the listener, so the result may be dropped
     * @return the live class, for code that needs the value as well as the style
     */
    public static LayoutClass install(final Region host) {
        Objects.requireNonNull(host, "host");
        final LayoutClass layout = new LayoutClass(host);
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
     * The class for a width, given the class held before it.
     *
     * @param previous the class in force, or {@code null} when there is none yet
     * @param width the content width in pixels
     * @return the class to hold now: a higher class at once, a lower one only 16 pixels below its boundary
     */
    static Width next(final @Nullable Width previous, final double width) {
        final Width plain = plain(width);
        if (previous == null || plain.floor() > previous.floor()) {
            return plain;
        }
        Width held = previous;
        while (held.narrower != null && held.floor() > plain.floor() && width < held.floor() - HYSTERESIS) {
            held = held.narrower;
        }
        return held;
    }

    private static Width plain(final double width) {
        Width found = Width.COMPACT;
        for (final Width candidate : Width.values()) {
            if (width >= candidate.floor()) {
                found = candidate;
            }
        }
        return found;
    }

    private void update(final double width) {
        final Width now = next(current.get(), width);
        for (final Width each : Width.values()) {
            host.pseudoClassStateChanged(each.pseudoClass(), each == now);
        }
        current.set(now);
    }
}
