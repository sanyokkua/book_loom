package ua.bookloom.ui.control;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
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
 * Two cards that sit side by side while there is room and stack, left above right, when there is not.
 *
 * <p>The children stay {@code [left, right]} in both layouts, which keeps the Tab order the reading order. Whether
 * there is room is decided by {@link LayoutClass}, the one place that decides arrangements for a width: the columns
 * stack while the area they sit in is narrower than the class they are built for, and while the control's own width
 * is below what its content needs side by side — two equal columns as wide as the wider minimum, and the gap; or, for
 * {@link #withSideColumn}, the main column's minimum, the gap and the side column's preferred width — by
 * {@link LayoutClass#isNarrow}, so a control that cannot shrink is never cut at the right edge in any language. The
 * gap between the columns and between the stacked cards is the {@code -fx-gap} of {@code .two-columns} in the theme.
 * Nothing is logged in layout: it runs on every pass.
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
    private final LayoutClass.Width sideBySideFrom;
    private final BooleanProperty equalHeights;
    private final StyleableDoubleProperty gap;
    private final boolean sideColumn;
    private boolean stacked;

    /**
     * Builds a pair that sits side by side whenever its content fits, whatever the area's class.
     *
     * @param left the column shown first (the upper card when stacked)
     * @param right the column shown second (the lower card when stacked)
     */
    public TwoColumns(final Node left, final Node right) {
        this(left, right, LayoutClass.Width.COMPACT, false);
    }

    /**
     * Builds a pair that sits side by side only in an area of at least {@code sideBySideFrom} and while its content
     * fits.
     *
     * @param left the column shown first (the upper card when stacked)
     * @param right the column shown second (the lower card when stacked)
     * @param sideBySideFrom the narrowest area class in which the columns are readable side by side
     */
    public TwoColumns(final Node left, final Node right, final LayoutClass.Width sideBySideFrom) {
        this(left, right, sideBySideFrom, false);
    }

    private TwoColumns(
            final Node left, final Node right, final LayoutClass.Width sideBySideFrom, final boolean sideColumn) {
        this.sideColumn = sideColumn;
        this.left = Objects.requireNonNull(left, "left");
        this.right = Objects.requireNonNull(right, "right");
        this.sideBySideFrom = Objects.requireNonNull(sideBySideFrom, "sideBySideFrom");
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
        // A capped screen keeps its width when the area crosses a class floor, so the class change asks for a layout.
        LayoutClass.follow(this, this::requestLayout);
        log.debug("two columns built side by side from {}, side column {}", sideBySideFrom, sideColumn);
    }

    /**
     * Builds a main column with a narrow side column at its preferred width on its right; both stack, main above, in
     * an area narrower than {@code sideBySideFrom} or when the main column's minimum and the side column do not fit.
     *
     * @param main the column that takes the rest of the width (the upper card when stacked)
     * @param side the column that keeps its preferred width beside the main one (the lower card when stacked)
     * @param sideBySideFrom the narrowest area class in which the two sit side by side
     * @return the control
     */
    public static TwoColumns withSideColumn(final Node main, final Node side, final LayoutClass.Width sideBySideFrom) {
        return new TwoColumns(main, side, sideBySideFrom, true);
    }

    /**
     * The narrowest area class in which the columns sit side by side.
     *
     * @return the class given when the control was built
     */
    public LayoutClass.Width sideBySideFrom() {
        return sideBySideFrom;
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
        return LayoutClass.of(this).isBelow(sideBySideFrom) || LayoutClass.isNarrow(stacked, innerWidth, sideBySide);
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
