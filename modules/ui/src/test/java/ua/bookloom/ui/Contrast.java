package ua.bookloom.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.layout.Background;
import javafx.scene.layout.Border;
import javafx.scene.layout.BorderStroke;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * WCAG 2.x contrast on what a node really paints: the ratio of two colours, and the surface behind a node — the first
 * opaque fill found walking up from it, blending translucent fills on the way.
 */
// Checkstyle's HideUtilityClassConstructor parses source text before Lombok's annotation processor runs (ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class Contrast {

    private static final double LINEAR_CUTOFF = 0.03928;
    private static final double LINEAR_DIVISOR = 12.92;
    private static final double GAMMA_OFFSET = 0.055;
    private static final double GAMMA_SCALE = 1.055;
    private static final double GAMMA = 2.4;
    private static final double RED_WEIGHT = 0.2126;
    private static final double GREEN_WEIGHT = 0.7152;
    private static final double BLUE_WEIGHT = 0.0722;
    private static final double FLARE = 0.05;

    static double ratio(final Color first, final Color second) {
        final double a = luminance(first);
        final double b = luminance(second);
        return (Math.max(a, b) + FLARE) / (Math.min(a, b) + FLARE);
    }

    static Color surfaceBehind(final Node node) {
        final List<Color> fills = new ArrayList<>();
        Node walk = node;
        while (walk != null && !opaque(fills)) {
            if (walk instanceof Region region) {
                fillOf(region.getBackground()).ifPresent(fills::add);
            }
            walk = walk.getParent();
        }
        return flatten(fills);
    }

    /** Every node that is drawn: visible, at full opacity all the way down from {@code root}, and with a size. */
    static List<Node> shown(final Node root) {
        final List<Node> nodes = new ArrayList<>();
        collect(root, nodes);
        return nodes;
    }

    /** Every colour any side of any stroke of {@code border} paints, transparent ones left out. */
    static List<Color> strokeColours(final Border border) {
        final List<Color> colours = new ArrayList<>();
        if (border == null) {
            return colours;
        }
        for (final BorderStroke stroke : border.getStrokes()) {
            for (final Paint side : List.of(
                    stroke.getTopStroke(), stroke.getRightStroke(), stroke.getBottomStroke(), stroke.getLeftStroke())) {
                if (side instanceof Color color && color.getOpacity() > 0) {
                    colours.add(color);
                }
            }
        }
        return colours;
    }

    private static void collect(final Node node, final List<Node> into) {
        if (!node.isVisible() || node.getOpacity() < 1) {
            return;
        }
        if (node.getLayoutBounds().getWidth() > 0 && node.getLayoutBounds().getHeight() > 0) {
            into.add(node);
        }
        if (node instanceof Parent parent) {
            parent.getChildrenUnmodifiable().forEach(child -> collect(child, into));
        }
    }

    private static Optional<Color> fillOf(final Background background) {
        if (background == null || background.getFills().isEmpty()) {
            return Optional.empty();
        }
        final Paint fill =
                background.getFills().get(background.getFills().size() - 1).getFill();
        return fill instanceof Color color && color.getOpacity() > 0 ? Optional.of(color) : Optional.empty();
    }

    private static boolean opaque(final List<Color> fills) {
        return !fills.isEmpty() && fills.get(fills.size() - 1).getOpacity() >= 1;
    }

    private static Color flatten(final List<Color> topDown) {
        Color result = Color.WHITE;
        for (int i = topDown.size() - 1; i >= 0; i--) {
            final Color over = topDown.get(i);
            final double a = over.getOpacity();
            result = new Color(
                    over.getRed() * a + result.getRed() * (1 - a),
                    over.getGreen() * a + result.getGreen() * (1 - a),
                    over.getBlue() * a + result.getBlue() * (1 - a),
                    1);
        }
        return result;
    }

    private static double luminance(final Color colour) {
        return RED_WEIGHT * channel(colour.getRed())
                + GREEN_WEIGHT * channel(colour.getGreen())
                + BLUE_WEIGHT * channel(colour.getBlue());
    }

    private static double channel(final double value) {
        return value <= LINEAR_CUTOFF ? value / LINEAR_DIVISOR : Math.pow((value + GAMMA_OFFSET) / GAMMA_SCALE, GAMMA);
    }
}
