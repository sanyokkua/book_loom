package ua.bookloom.ui.control;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javafx.beans.property.DoubleProperty;
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
 * children stay {@code [left, right]} in both layouts, which keeps the Tab order the reading order. The gap between
 * the columns and between the stacked cards is the {@code -fx-gap} of {@code .two-columns} in the theme.
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

    private final Node left;
    private final Node right;
    private final DoubleProperty breakpoint;
    private final StyleableDoubleProperty gap;
    private boolean stacked;

    /**
     * Builds the control.
     *
     * @param left the column shown first (the upper card when stacked)
     * @param right the column shown second (the lower card when stacked)
     * @param breakpointPx the width, in px, at or above which the columns sit side by side
     */
    public TwoColumns(final Node left, final Node right, final double breakpointPx) {
        this.left = Objects.requireNonNull(left, "left");
        this.right = Objects.requireNonNull(right, "right");
        this.breakpoint = new SimpleDoubleProperty(this, "breakpoint", breakpointPx) {
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
        log.debug("two columns built with breakpoint {} px", breakpointPx);
    }

    /**
     * The content width at or above which the columns sit side by side; changing it lays the control out again.
     *
     * @return the breakpoint in px
     */
    public DoubleProperty breakpointProperty() {
        return breakpoint;
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
        return horizontalInsets() + Math.max(left.prefWidth(-1), right.prefWidth(-1)) * 2 + gap.get();
    }

    @Override
    protected double computePrefHeight(final double width) {
        final Insets insets = getInsets();
        final double inner = (width < 0 ? computePrefWidth(-1) : width) - horizontalInsets();
        final boolean stack = isStackedAt(inner);
        final double columnWidth = columnWidth(inner, stack);
        final double leftHeight = left.prefHeight(columnWidth);
        final double rightHeight = right.prefHeight(columnWidth);
        final double content = stack ? leftHeight + gap.get() + rightHeight : Math.max(leftHeight, rightHeight);
        return insets.getTop() + content + insets.getBottom();
    }

    @Override
    protected void layoutChildren() {
        final Insets insets = getInsets();
        final double inner = getWidth() - horizontalInsets();
        stacked = isStackedAt(inner);
        final double columnWidth = columnWidth(inner, stacked);
        final double leftHeight = left.prefHeight(columnWidth);
        final double rightHeight = right.prefHeight(columnWidth);
        left.resizeRelocate(insets.getLeft(), insets.getTop(), columnWidth, leftHeight);
        final double rightX = stacked ? insets.getLeft() : insets.getLeft() + columnWidth + gap.get();
        final double rightY = stacked ? insets.getTop() + leftHeight + gap.get() : insets.getTop();
        right.resizeRelocate(rightX, rightY, columnWidth, rightHeight);
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

    private boolean isStackedAt(final double innerWidth) {
        return innerWidth < breakpoint.get();
    }

    private double columnWidth(final double innerWidth, final boolean stack) {
        return stack ? innerWidth : (innerWidth - gap.get()) / 2;
    }

    private double horizontalInsets() {
        final Insets insets = getInsets();
        return insets.getLeft() + insets.getRight();
    }
}
