package ua.bookloom.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Labeled;
import javafx.scene.control.TextField;
import javafx.scene.layout.Background;
import javafx.scene.layout.Border;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import org.controlsfx.control.ToggleSwitch;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.kordamp.ikonli.javafx.FontIcon;
import ua.bookloom.ui.ConformanceCases.Screen;
import ua.bookloom.ui.theme.ThemeMode;

/**
 * WCAG AA on what is really painted: for every screen of {@link ConformanceCases} in both value blocks, each shown
 * piece of text (a label, a button's words, a field's text or its placeholder) contrasts 4.5:1 with the surface it is
 * painted on, each icon and each edge that tells a control apart (an input's border, a switch's track and thumb) 3:1,
 * and no informative text is smaller than 12px.
 *
 * <p>The surface is the first opaque fill found walking up from the node itself, blending translucent fills on the
 * way. Card outlines and dividers are decoration — the card is told by its fill and its content — so they are not
 * held to 3:1. A dimmed (disabled) control is exempt, as WCAG exempts inactive components.
 */
class ContrastTest extends ConformanceTestBase {

    private static final double TEXT_RATIO = 4.5;
    private static final double UI_RATIO = 3.0;
    private static final double MIN_TEXT_PX = 12.0;
    private static final double MIN_BADGE_PX = 11.0;
    // A badge repeats what the text beside it says (the step's place, "suggested", a formatting token), so it may be
    // a point smaller than informative text.
    private static final Set<String> BADGES = Set.of("nav-step", "glossary-suggested-badge", "placeholder-chip");
    private static final double SIZE_TOLERANCE = 0.01;
    private static final double LINEAR_CUTOFF = 0.03928;
    private static final double LINEAR_DIVISOR = 12.92;
    private static final double GAMMA_OFFSET = 0.055;
    private static final double GAMMA_SCALE = 1.055;
    private static final double GAMMA = 2.4;
    private static final double RED_WEIGHT = 0.2126;
    private static final double GREEN_WEIGHT = 0.7152;
    private static final double BLUE_WEIGHT = 0.0722;
    private static final double FLARE = 0.05;

    static Stream<Arguments> screensInEachBlock() {
        return ConformanceCases.SCREENS.stream()
                .flatMap(screen -> Stream.of(ThemeMode.LIGHT, ThemeMode.DARK)
                        .map(block -> Arguments.of(Named.of(screen.name(), screen), block)));
    }

    static Stream<Arguments> screens() {
        return ConformanceCases.SCREENS.stream().map(screen -> Arguments.of(Named.of(screen.name(), screen)));
    }

    // IF a piece of text is painted in a colour its surface swallows, THEN the named node fails here.
    @ParameterizedTest(name = "{0} under {1}")
    @MethodSource("screensInEachBlock")
    void screen_text_underEachBlock_contrastsWithItsSurface(final Screen screen, final ThemeMode block)
            throws TimeoutException {
        show(screen);
        onFx(() -> themeController.setMode(block));

        final List<String> faint = ThemeTestSupport.onFx(() -> faintText(scene.getRoot()));

        assertThat(faint)
                .as("text under %s on %s below %.1f:1 (icons %.1f:1)", block, screen.name(), TEXT_RATIO, UI_RATIO)
                .isEmpty();
    }

    // IF an input's border or a switch's track and thumb merge into what is around them, THEN the control is not seen.
    @ParameterizedTest(name = "{0} under {1}")
    @MethodSource("screensInEachBlock")
    void screen_controlEdges_underEachBlock_contrastWithTheirSurface(final Screen screen, final ThemeMode block)
            throws TimeoutException {
        show(screen);
        onFx(() -> themeController.setMode(block));

        final List<String> merged = ThemeTestSupport.onFx(() -> mergedEdges(scene.getRoot()));

        assertThat(merged)
                .as("control edges under %s on %s below %.1f:1", block, screen.name(), UI_RATIO)
                .isEmpty();
    }

    // IF informative text is set below 12px (a badge below 11px), THEN the named node fails here.
    @ParameterizedTest(name = "{0}")
    @MethodSource("screens")
    void screen_text_isAtLeastTwelvePixels(final Screen screen) throws TimeoutException {
        show(screen);

        final List<String> small = ThemeTestSupport.onFx(() -> smallText(scene.getRoot()));

        assertThat(small)
                .as("text on %s below %.0fpx (badges %.0fpx)", screen.name(), MIN_TEXT_PX, MIN_BADGE_PX)
                .isEmpty();
    }

    private static List<String> faintText(final Node root) {
        final List<String> faint = new ArrayList<>();
        for (final Node node : shown(root)) {
            fillOfText(node).ifPresent(fill -> {
                final double ratio = ratio(fill, surfaceBehind(node));
                final double required = node instanceof FontIcon ? UI_RATIO : TEXT_RATIO;
                if (ratio < required) {
                    faint.add("%s ratio %.2f".formatted(describe(node), ratio));
                }
            });
        }
        return faint;
    }

    private static List<String> smallText(final Node root) {
        final List<String> small = new ArrayList<>();
        for (final Node node : shown(root)) {
            if (fillOfText(node).isEmpty() || node instanceof FontIcon) {
                continue;
            }
            final double size = fontOf(node).getSize();
            final double floor = hasClassUp(node, BADGES) ? MIN_BADGE_PX : MIN_TEXT_PX;
            if (size + SIZE_TOLERANCE < floor) {
                small.add("%s %.1fpx".formatted(describe(node), size));
            }
        }
        return small;
    }

    private static List<String> mergedEdges(final Node root) {
        final List<String> merged = new ArrayList<>();
        for (final Node node : shown(root)) {
            if (node instanceof ToggleSwitch toggle) {
                checkSwitch(toggle, merged);
            } else if (isInput(node)) {
                checkBorder((Region) node, true, merged);
            } else if (node.getStyleClass().contains("box")) {
                // A ticked box is filled with its edge colour; the edge is then read against the card only.
                checkBorder((Region) node, false, merged);
            }
        }
        return merged;
    }

    private static boolean isInput(final Node node) {
        final boolean editor = node.getParent() instanceof ComboBox<?>;
        return node instanceof ComboBox<?>
                || (node instanceof TextField && !editor)
                || node.getStyleClass().contains("brief-input");
    }

    private static void checkSwitch(final ToggleSwitch toggle, final List<String> merged) {
        final Region track = (Region) toggle.lookup(".thumb-area");
        final Color trackFill = surfaceBehind(track);
        final double thumb = ratio(surfaceBehind((Region) toggle.lookup(".thumb")), trackFill);
        final double surface = ratio(trackFill, surfaceBehind(toggle.getParent()));
        if (Math.min(thumb, surface) < UI_RATIO) {
            merged.add("%s thumb/track %.2f track/surface %.2f".formatted(describe(toggle), thumb, surface));
        }
    }

    private static void checkBorder(final Region region, final boolean againstFill, final List<String> merged) {
        final Optional<Color> edge = edgeOf(region.getBorder());
        if (edge.isEmpty() || region.isFocused()) {
            return;
        }
        final double inside = againstFill ? ratio(edge.get(), surfaceBehind(region)) : Double.MAX_VALUE;
        final double outside = ratio(edge.get(), surfaceBehind(region.getParent()));
        if (Math.min(inside, outside) < UI_RATIO) {
            merged.add("%s border/fill %.2f border/surface %.2f".formatted(describe(region), inside, outside));
        }
    }

    private static Optional<Color> edgeOf(final Border border) {
        if (border == null || border.getStrokes().isEmpty()) {
            return Optional.empty();
        }
        final Paint paint = border.getStrokes().get(0).getBottomStroke();
        return paint instanceof Color color && color.getOpacity() > 0 ? Optional.of(color) : Optional.empty();
    }

    private static Optional<Color> fillOfText(final Node node) {
        // A switch draws its words with a label of its own skin, which is read instead.
        if (node instanceof Labeled labeled
                && !(node instanceof ToggleSwitch)
                && labeled.getText() != null
                && !labeled.getText().isBlank()) {
            return labeled.getTextFill() instanceof Color color ? Optional.of(color) : Optional.empty();
        }
        if (node instanceof Text text && !(text.getParent() instanceof Labeled) && !isLabeledText(text)) {
            final boolean hasText = text.getText() != null && !text.getText().isBlank();
            return hasText && text.getFill() instanceof Color color ? Optional.of(color) : Optional.empty();
        }
        return Optional.empty();
    }

    // A Labeled's own words are drawn by a LabeledText inside it; that node is the Labeled's, read through it.
    private static boolean isLabeledText(final Text text) {
        return "LabeledText".equals(text.getClass().getSimpleName());
    }

    private static Font fontOf(final Node node) {
        return node instanceof Labeled labeled ? labeled.getFont() : ((Text) node).getFont();
    }

    /** Every node that is drawn: visible, at full opacity all the way up, and with a size. */
    private static List<Node> shown(final Node root) {
        final List<Node> nodes = new ArrayList<>();
        collect(root, nodes);
        return nodes;
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

    private static boolean hasClassUp(final Node node, final Set<String> names) {
        Node walk = node;
        while (walk != null) {
            if (walk.getStyleClass().stream().anyMatch(names::contains)) {
                return true;
            }
            walk = walk.getParent();
        }
        return false;
    }

    private static String describe(final Node node) {
        final String text = node instanceof Labeled labeled
                ? " \"" + labeled.getText() + "\""
                : node instanceof Text t ? " \"" + t.getText() + "\"" : "";
        return "%s#%s%s%s".formatted(node.getClass().getSimpleName(), node.getId(), node.getStyleClass(), text);
    }

    private static Color surfaceBehind(final Node node) {
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

    private static double ratio(final Color first, final Color second) {
        final double a = luminance(first);
        final double b = luminance(second);
        return (Math.max(a, b) + FLARE) / (Math.min(a, b) + FLARE);
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
