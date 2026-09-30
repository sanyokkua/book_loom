package ua.bookloom.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.Background;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import org.controlsfx.control.ToggleSwitch;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import ua.bookloom.ui.ConformanceCases.Screen;
import ua.bookloom.ui.theme.ThemeMode;

/**
 * The class of bug a role-by-role conformance case cannot see: text that no rule colours, so it keeps a default that
 * disappears on one theme's surface. For every screen of {@link ConformanceCases} in both value blocks, each shown
 * label's text fill must contrast with the surface it is painted on, and each switch's thumb with its track.
 *
 * <p>The surface is the first opaque fill found walking up from the label itself, blending translucent fills on the way.
 */
class ContrastTest extends ConformanceTestBase {

    private static final double TEXT_RATIO = 4.5;
    // Captions, hints, chips and the navigation's dim text take the mockup's own quieter palette pairs, which measure
    // 2.7 to 4.4:1 by design; they must still read, but a default-coloured label (about 1.2:1) is what this test hunts.
    private static final double QUIET_RATIO = 2.5;
    private static final Set<String> QUIET = Set.of(
            "muted",
            "hint",
            "stat-caption",
            "stat-number-warn",
            "kv-key",
            "field-label",
            "dialog-sub",
            "dropzone-glyph",
            "provider-endpoint",
            "shell-breadcrumb",
            "run-status-detail",
            "banner-icon",
            "status-ok",
            "column-header");
    // The mockup's white thumb on its slate track measures just under 2:1; the thumb's shadow carries the rest.
    private static final double THUMB_RATIO = 1.9;
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

    // IF a label's colour is left to a default that one theme's surface swallows, THEN the named label fails here.
    @ParameterizedTest(name = "{0} under {1}")
    @MethodSource("screensInEachBlock")
    void screen_text_underEachBlock_contrastsWithItsSurface(final Screen screen, final ThemeMode block)
            throws TimeoutException {
        show(screen);
        onFx(() -> themeController.setMode(block));

        final List<String> faint = ThemeTestSupport.onFx(() -> faintLabels(scene.getRoot()));

        assertThat(faint)
                .as(
                        "labels under %s on %s below their required contrast (%.1f:1 body, %.1f:1 quiet roles)",
                        block, screen.name(), TEXT_RATIO, QUIET_RATIO)
                .isEmpty();
    }

    // IF a switch's thumb merges into its track, THEN the state is not readable by position and colour.
    @ParameterizedTest(name = "{0} under {1}")
    @MethodSource("screensInEachBlock")
    void screen_switch_underEachBlock_thumbContrastsWithTrack(final Screen screen, final ThemeMode block)
            throws TimeoutException {
        show(screen);
        onFx(() -> themeController.setMode(block));

        final List<String> merged = ThemeTestSupport.onFx(() -> mergedSwitches(scene.getRoot()));

        assertThat(merged)
                .as("switches under %s on %s with thumb/track below %.1f:1", block, screen.name(), THUMB_RATIO)
                .isEmpty();
    }

    private static List<String> faintLabels(final Node root) {
        final List<String> faint = new ArrayList<>();
        for (final Node node : root.lookupAll(".label")) {
            if (node instanceof Label label && isShownText(label)) {
                final double ratio = ratio((Color) label.getTextFill(), surfaceBehind(label));
                if (ratio < requiredRatio(label)) {
                    faint.add("%s ratio %.2f".formatted(describe(label), ratio));
                }
            }
        }
        return faint;
    }

    private static double requiredRatio(final Node label) {
        Node walk = label;
        while (walk != null) {
            if (walk.getStyleClass().stream()
                    .anyMatch(name -> QUIET.contains(name) || name.startsWith("nav-") || name.startsWith("chip"))) {
                return QUIET_RATIO;
            }
            walk = walk.getParent();
        }
        return TEXT_RATIO;
    }

    private static List<String> mergedSwitches(final Node root) {
        final List<String> merged = new ArrayList<>();
        for (final Node node : root.lookupAll(".toggle-switch")) {
            if (node instanceof ToggleSwitch toggle && node.isVisible()) {
                final Color thumb = surfaceBehind((Region) toggle.lookup(".thumb"));
                final Color track = surfaceBehind((Region) toggle.lookup(".thumb-area"));
                final double ratio = ratio(thumb, track);
                if (ratio < THUMB_RATIO) {
                    merged.add("%s ratio %.2f".formatted(describe(toggle), ratio));
                }
            }
        }
        return merged;
    }

    private static boolean isShownText(final Label label) {
        final String text = label.getText();
        if (text == null || text.isBlank() || !(label.getTextFill() instanceof Color)) {
            return false;
        }
        Node walk = label;
        while (walk != null) {
            if (!walk.isVisible() || walk.getOpacity() < 1) {
                return false;
            }
            walk = walk.getParent();
        }
        return label.getLayoutBounds().getWidth() > 0;
    }

    private static String describe(final Node node) {
        final String text = node instanceof Label label ? " \"" + label.getText() + "\"" : "";
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
