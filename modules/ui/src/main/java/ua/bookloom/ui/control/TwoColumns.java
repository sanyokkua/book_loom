package ua.bookloom.ui.control;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.css.CssMetaData;
import javafx.css.SimpleStyleableDoubleProperty;
import javafx.css.Styleable;
import javafx.css.StyleableDoubleProperty;
import javafx.css.StyleableProperty;
import javafx.css.converter.SizeConverter;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.layout.Region;
import lombok.extern.slf4j.Slf4j;

/**
 * Two cards that sit side by side while the control is wide enough and stack, left above right, when it is not.
 *
 * <p>The decision is made from the control's own laid-out width, so it follows the window without a listener, and the
 * children stay {@code [left, right]} in both layouts, which keeps the Tab order the reading order. The columns stack
 * whenever the width is below the breakpoint or below what the two columns need side by side — two equal columns as
 * wide as the wider minimum, and the gap; or, for {@link #withSideColumn}, the main column's minimum, the gap and the
 * side column's preferred width — so a control that cannot shrink (a segmented choice, a button) is never cut at the
 * right edge, whatever its words in the display language. They sit side by side again only once the width is 16
 * pixels past that boundary, the same hysteresis as {@link LayoutClass}: a minimum width that changes with the column
 * (a choice that puts its labels on two lines) or a scrollbar that comes and goes must not flip the layout back and
 * forth. The gap between the columns and between the stacked cards is the {@code -fx-gap} of {@code .two-columns} in
 * the theme. Nothing is logged in layout: it runs on every pass.
 */
@Slf4j
public final class TwoColumns extends Region {

    private static final CssMetaData<TwoColumns, Number> GAP_META =
            new CssMetaData<>("-fx-gap", SizeConverter.getInstance(), 0) {
                @Override
                public boolean isSettable(final TwoColumns control) {
                    return !control.gap.isBound();
                }

                @Override
                public StyleableProperty<Number> getStyleableProperty(final TwoColumns control) {
                    return control.gap;
                }
            };

    private static final List<CssMetaData<? extends Styleable, ?>> ALL_META_DATA = allMetaData();
    private static final double HYSTERESIS = 16;

    private final Node left;
    private final Node right;
    private final DoubleProperty breakpoint;
    private final BooleanProperty equalHeights;
    private final StyleableDoubleProperty gap;
    private final boolean sideColumn;
    private boolean stacked;

    /**
     * Builds the control.
     *
     * @param left the column shown first (the upper card when stacked)
     * @param right the column shown second (the lower card when stacked)
     * @param breakpointPx the narrowest width, in px, at which the columns sit side by side even when both would fit
     *     narrower: below it a column would be too narrow to read
     */
    public TwoColumns(final Node left, final Node right, final double breakpointPx) {
        this(left, right, breakpointPx, false);
    }

    private TwoColumns(final Node left, final Node right, final double breakpointPx, final boolean sideColumn) {
        this.sideColumn = sideColumn;
        this.left = Objects.requireNonNull(left, "left");
        this.right = Objects.requireNonNull(right, "right");
        this.breakpoint = new SimpleDoubleProperty(this, "breakpoint", breakpointPx) {
            @Override
            protected void invalidated() {
                requestLayout();
            }
        };
        this.equalHeights = new SimpleBooleanProperty(this, "equalHeights", false) {
            @Override
            protected void invalidated() {
                requestLayout();
            }
        };
        this.gap = new SimpleStyleableDoubleProperty(GAP_META, this, "gap", 0.0) {
            @Override
            protected void invalidated() {
                requestLayout();
            }
        };
        getStyleClass().add("two-columns");
        getChildren().addAll(left, right);
        log.debug("two columns built with breakpoint {} px, side column {}", breakpointPx, sideColumn);
    }

    /**
     * Builds a main column with a narrow side column at its preferred width on its right, both stacked, main above,
     * when the main column's minimum and the side column do not fit side by side.
     *
     * @param main the column that takes the rest of the width (the upper card when stacked)
     * @param side the column that keeps its preferred width beside the main one (the lower card when stacked)
     * @return the control, with no breakpoint of its own: only the content decides
     */
    public static TwoColumns withSideColumn(final Node main, final Node side) {
        return new TwoColumns(main, side, 0, true);
    }

    /**
     * The narrowest width at which the columns sit side by side when their content fits; changing it lays the control
     * out again.
     *
     * @return the breakpoint in px
     */
    public DoubleProperty breakpointProperty() {
        return breakpoint;
    }

    /**
     * Whether side-by-side columns are laid out as tall as the taller of the two, for a pair of equal cards; stacked
     * cards keep their own heights.
     *
     * @return the property; {@code false} by default
     */
    public BooleanProperty equalHeightsProperty() {
        return equalHeights;
    }

    /**
     * Whether the last layout stacked the columns.
     *
     * @return {@code true} if the right column sat below the left one, {@code false} if they sat side by side
     */
    public boolean isStacked() {
        return stacked;
    }

    @Override
    public Orientation getContentBias() {
        return Orientation.HORIZONTAL;
    }

    @Override
    protected double computeMinWidth(final double height) {
        return horizontalInsets() + Math.max(left.minWidth(-1), right.minWidth(-1));
    }

    @Override
    protected double computePrefWidth(final double height) {
        final double columns = sideColumn
                ? left.prefWidth(-1) + right.prefWidth(-1)
                : Math.max(left.prefWidth(-1), right.prefWidth(-1)) * 2;
        return horizontalInsets() + columns + gap.get();
    }

    @Override
    protected double computePrefHeight(final double width) {
        final Insets insets = getInsets();
        final double inner = (width < 0 ? computePrefWidth(-1) : width) - horizontalInsets();
        final boolean stack = isStackedAt(inner);
        final double leftHeight = left.prefHeight(leftWidth(inner, stack));
        final double rightHeight = right.prefHeight(rightWidth(inner, stack));
        final double content = stack ? leftHeight + gap.get() + rightHeight : Math.max(leftHeight, rightHeight);
        return insets.getTop() + content + insets.getBottom();
    }

    @Override
    protected void layoutChildren() {
        final Insets insets = getInsets();
        final double inner = getWidth() - horizontalInsets();
        stacked = isStackedAt(inner);
        final double leftWidth = leftWidth(inner, stacked);
        final double rightWidth = rightWidth(inner, stacked);
        final double leftHeight = left.prefHeight(leftWidth);
        final double rightHeight = right.prefHeight(rightWidth);
        final boolean level = equalHeights.get() && !stacked;
        final double tallest = Math.max(leftHeight, rightHeight);
        left.resizeRelocate(insets.getLeft(), insets.getTop(), leftWidth, level ? tallest : leftHeight);
        final double rightX = stacked ? insets.getLeft() : insets.getLeft() + leftWidth + gap.get();
        final double rightY = stacked ? insets.getTop() + leftHeight + gap.get() : insets.getTop();
        right.resizeRelocate(rightX, rightY, rightWidth, level ? tallest : rightHeight);
    }

    @Override
    public List<CssMetaData<? extends Styleable, ?>> getCssMetaData() {
        return getClassCssMetaData();
    }

    /**
     * The CSS properties this control adds to a region's.
     *
     * @return the region's metadata plus {@code -fx-gap}; never null
     */
    public static List<CssMetaData<? extends Styleable, ?>> getClassCssMetaData() {
        return ALL_META_DATA;
    }

    private static List<CssMetaData<? extends Styleable, ?>> allMetaData() {
        final List<CssMetaData<? extends Styleable, ?>> all = new ArrayList<>(Region.getClassCssMetaData());
        all.add(GAP_META);
        return List.copyOf(all);
    }

    // Read by computePrefHeight and layoutChildren alike, without changing the held state, so the height a parent asked
    // for is the height of the layout this control then draws.
    private boolean isStackedAt(final double innerWidth) {
        final double sideBySide = sideColumn
                ? left.minWidth(-1) + gap.get() + right.prefWidth(-1)
                : 2 * Math.max(left.minWidth(-1), right.minWidth(-1)) + gap.get();
        final double needed = Math.max(breakpoint.get(), sideBySide);
        return innerWidth < (stacked ? needed + HYSTERESIS : needed);
    }

    private double leftWidth(final double innerWidth, final boolean stack) {
        if (stack) {
            return innerWidth;
        }
        return sideColumn ? innerWidth - gap.get() - right.prefWidth(-1) : (innerWidth - gap.get()) / 2;
    }

    private double rightWidth(final double innerWidth, final boolean stack) {
        if (stack) {
            return innerWidth;
        }
        return sideColumn ? right.prefWidth(-1) : (innerWidth - gap.get()) / 2;
    }

    private double horizontalInsets() {
        final Insets insets = getInsets();
        return insets.getLeft() + insets.getRight();
    }
}
