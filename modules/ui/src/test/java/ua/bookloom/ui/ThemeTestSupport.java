package ua.bookloom.ui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javafx.application.Platform;
import javafx.collections.ObservableList;
import javafx.scene.Scene;
import javafx.scene.effect.DropShadow;
import javafx.scene.effect.Effect;
import javafx.scene.layout.Background;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;

/**
 * Shared probes for the theme tests: a themed scene, a switch between the two value blocks, a way to ask what a
 * role resolves to, and a splitter for the one flat stylesheet.
 *
 * <p>Public because the screen tests in other packages of this module resolve roles the same way; keeping one probe
 * means "what does this role resolve to" has a single definition. The role probe uses a test-only inline style on a
 * throwaway region: that is the only way to ask the CSS engine for a looked-up colour, and it is outside the
 * {@code no-inline-style-in-ui} rule, which scans production classes only.
 *
 * <p>Every method that touches the scene graph hops to the FX Application Thread itself, so callers may invoke them
 * from the JUnit thread.
 */
// Checkstyle's HideUtilityClassConstructor parses source text before Lombok's annotation processor runs, so it
// cannot see the private constructor @NoArgsConstructor generates; suppressed as FixtureCatalog does (ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ThemeTestSupport {

    private static final long FX_TIMEOUT_SECONDS = 10;
    private static final double SCENE_SIZE = 320;

    /** Tolerance for a colour component: an alpha written as {@code .13} is stored as a float. */
    private static final double COMPONENT_TOLERANCE = 1e-6;

    private static final Pattern COMMA = Pattern.compile(",");
    private static final Pattern COMMENT = Pattern.compile("/\\*.*?\\*/", Pattern.DOTALL);
    private static final Pattern RULE = Pattern.compile("([^{}]+)\\{([^{}]*)}");

    /**
     * A scene whose root is an empty {@link StackPane} with {@code theme.css} attached at Scene level, light block
     * in force.
     *
     * @return a new scene; the caller shows it in a stage (for example from {@code ApplicationTest#start})
     */
    public static Scene themedScene() {
        final Scene scene = new Scene(new StackPane(), SCENE_SIZE, SCENE_SIZE);
        scene.getStylesheets().add(Theme.stylesheet());
        return scene;
    }

    /**
     * Puts the dark value block in force, or takes it out, by toggling {@link Theme#DARK_STYLE_CLASS} on the scene
     * root.
     *
     * @param scene a scene built by {@link #themedScene()}
     * @param dark {@code true} for the dark block, {@code false} for the light block
     */
    public static void applyTheme(final Scene scene, final boolean dark) {
        onFx(() -> {
            final ObservableList<String> classes = scene.getRoot().getStyleClass();
            classes.remove(Theme.DARK_STYLE_CLASS);
            if (dark) {
                classes.add(Theme.DARK_STYLE_CLASS);
            }
            scene.getRoot().applyCss();
            return null;
        });
    }

    /**
     * Resolves the looked-up colour {@code -color-<role>} against the value block currently in force on the scene
     * root, by painting a probe region with it and reading the fill back.
     *
     * @param scene a scene built by {@link #themedScene()} and shown
     * @param role the published role name without the {@code -color-} prefix, for example {@code surface-2}
     * @return the resolved fill, or {@code null} when the role is not defined (the engine drops the declaration
     *     silently, so an undefined role leaves the probe with no background at all)
     */
    public static @Nullable Paint resolveRole(final Scene scene, final String role) {
        return onFx(() -> {
            final StackPane root = (StackPane) scene.getRoot();
            final Region probe = new Region();
            probe.setStyle("-fx-background-color: -color-" + role + ";");
            root.getChildren().add(probe);
            root.applyCss();
            final Background background = probe.getBackground();
            root.getChildren().remove(probe);
            return background == null ? null : background.getFills().get(0).getFill();
        });
    }

    /**
     * Applies an elevation style class to a throwaway region and returns the effect the stylesheet gave it.
     *
     * @param scene a scene built by {@link #themedScene()} and shown
     * @param styleClass {@code elevation-sm}, {@code elevation} or {@code elevation-lg}
     * @return the effect, or {@code null} when the stylesheet gives that class none
     */
    public static @Nullable Effect resolveElevation(final Scene scene, final String styleClass) {
        return onFx(() -> {
            final StackPane root = (StackPane) scene.getRoot();
            final Region probe = new Region();
            probe.getStyleClass().add(styleClass);
            root.getChildren().add(probe);
            root.applyCss();
            final Effect effect = probe.getEffect();
            root.getChildren().remove(probe);
            return effect;
        });
    }

    /**
     * Reads an expected colour written the way the published catalogue writes it.
     *
     * @param published {@code #rrggbb} or {@code rgba(r,g,b,a)} with the alpha as a fraction
     * @return the colour it denotes
     */
    public static Color color(final String published) {
        final String value = published.trim();
        if (!value.startsWith("rgba(")) {
            return Color.web(value);
        }
        final List<String> parts = COMMA.splitAsStream(value.substring("rgba(".length(), value.length() - 1))
                .map(String::trim)
                .toList();
        return Color.rgb(
                Integer.parseInt(parts.get(0)),
                Integer.parseInt(parts.get(1)),
                Integer.parseInt(parts.get(2)),
                Double.parseDouble(parts.get(3)));
    }

    /**
     * Splits a flat stylesheet into selector to declaration-block, comments removed.
     *
     * <p>The theme file has no nesting, so a single regular expression is enough. A selector that appears twice has
     * its blocks concatenated, so a caller sees everything declared for it.
     *
     * @param css the stylesheet text
     * @return selector (trimmed) to the text between its braces, in file order
     */
    public static Map<String, String> rules(final String css) {
        final String stripped = COMMENT.matcher(css).replaceAll("");
        final Map<String, String> rules = new LinkedHashMap<>();
        final Matcher matcher = RULE.matcher(stripped);
        while (matcher.find()) {
            rules.merge(matcher.group(1).trim(), matcher.group(2), String::concat);
        }
        return rules;
    }

    /**
     * Reads the one theme stylesheet as text.
     *
     * @return the full contents of {@code theme.css}
     */
    public static String themeCss() {
        try (InputStream in = URI.create(Theme.stylesheet()).toURL().openStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("theme.css could not be read", e);
        }
    }

    /**
     * Asserts that a resolved colour is the one the catalogue publishes, allowing only float rounding.
     *
     * @param actual what the CSS engine produced; must be a {@link Color}
     * @param published the expected value, written out by hand from the published table
     * @param what what is being resolved, for the failure message
     */
    public static void assertSameColour(final @Nullable Object actual, final String published, final String what) {
        assertThat(actual).as("%s must resolve to a colour", what).isInstanceOf(Color.class);
        final Color got = (Color) actual;
        final Color want = color(published);
        assertThat(got.getRed())
                .as("%s red, expected %s", what, published)
                .isCloseTo(want.getRed(), within(COMPONENT_TOLERANCE));
        assertThat(got.getGreen())
                .as("%s green, expected %s", what, published)
                .isCloseTo(want.getGreen(), within(COMPONENT_TOLERANCE));
        assertThat(got.getBlue())
                .as("%s blue, expected %s", what, published)
                .isCloseTo(want.getBlue(), within(COMPONENT_TOLERANCE));
        assertThat(got.getOpacity())
                .as("%s opacity, expected %s", what, published)
                .isCloseTo(want.getOpacity(), within(COMPONENT_TOLERANCE));
    }

    /**
     * Runs {@code action} on the FX Application Thread and waits for its result.
     *
     * @param action work that touches the scene graph
     * @param <T> the result type
     * @return whatever {@code action} returned
     */
    public static <T> T onFx(final Supplier<T> action) {
        if (Platform.isFxApplicationThread()) {
            return action.get();
        }
        final FutureTask<T> task = new FutureTask<>(action::get);
        Platform.runLater(task);
        try {
            return task.get(FX_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while waiting for the FX thread", e);
        } catch (ExecutionException | TimeoutException e) {
            throw new IllegalStateException("the FX-thread probe failed", e);
        }
    }

    /**
     * Narrows an effect to the drop shadow the elevation classes are meant to carry.
     *
     * @param effect what {@link #resolveElevation} returned
     * @return the same object as a {@link DropShadow}
     * @throws AssertionError if the effect is absent or of another kind, which is the failure the test reports
     */
    public static DropShadow asDropShadow(final @Nullable Effect effect) {
        if (effect instanceof DropShadow shadow) {
            return shadow;
        }
        throw new AssertionError("expected a DropShadow but the element carries: " + effect);
    }
}
