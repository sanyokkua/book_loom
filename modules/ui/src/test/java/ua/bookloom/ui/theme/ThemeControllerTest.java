package ua.bookloom.ui.theme;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Paint;
import javafx.stage.Stage;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.testfx.framework.junit5.ApplicationTest;
import ua.bookloom.ui.Theme;
import ua.bookloom.ui.ThemeTestSupport;

/**
 * The theme controller applies the light or dark value block on the scene root from the chosen mode, asking the
 * operating system only through {@link ColorSchemeProvider}. Expected colours are the published catalogue values,
 * written out by hand.
 */
class ThemeControllerTest extends ApplicationTest {

    private static final String LIGHT_BG = "#f4f1ea";
    private static final String DARK_BG = "#283237";

    /** The 41 colour roles as published; the set that must survive a switch of block. */
    private static final Set<String> COLOUR_ROLES = Set.of(
            "bg",
            "surface",
            "surface-2",
            "surface-alt",
            "border",
            "border-cool",
            "divider",
            "text",
            "text-strong",
            "muted",
            "muted-2",
            "primary",
            "primary-hover",
            "primary-press",
            "primary-fg",
            "primary-soft",
            "sand-soft",
            "sand-strong",
            "selected",
            "nav-bg",
            "nav-bg-2",
            "nav-fg",
            "nav-fg-muted",
            "nav-active-bg",
            "nav-active-fg",
            "nav-accent",
            "title-bg",
            "title-fg",
            "focus",
            "ok",
            "ok-bg",
            "ok-bd",
            "warn",
            "warn-bg",
            "warn-bd",
            "err",
            "err-bg",
            "err-bd",
            "info",
            "info-bg",
            "info-bd");

    // Assigned in start(), which ApplicationTest runs before every test.
    @SuppressWarnings("NullAway.Init")
    private Scene scene;

    @Override
    public void start(final Stage stage) {
        scene = new Scene(new StackPane(), 320, 320);
        stage.setScene(scene);
        stage.show();
    }

    // IF the operating system reports dark when the scene is attached, THEN the app background is the dark #283237.
    @Test
    void attach_providerReportsDark_backgroundResolvesToDarkBlock() {
        final ThemeController controller = new ThemeController(new FakeColorSchemeProvider(ThemeBlock.DARK));

        ThemeTestSupport.onFx(() -> attachTo(controller));

        ThemeTestSupport.assertSameColour(ThemeTestSupport.resolveRole(scene, "bg"), DARK_BG, "bg");
    }

    // IF the operating system reports no preference, THEN the light block applies and bg is #f4f1ea.
    @Test
    void attach_providerReportsNoPreference_backgroundResolvesToLightBlock() {
        final ThemeController controller = new ThemeController(new FakeColorSchemeProvider(null));

        ThemeTestSupport.onFx(() -> attachTo(controller));

        ThemeTestSupport.assertSameColour(ThemeTestSupport.resolveRole(scene, "bg"), LIGHT_BG, "bg");
    }

    // IF nothing was chosen, THEN the mode is to follow the operating system.
    @Test
    void mode_afterAttachWithNoChoice_isSystem() {
        final ThemeController controller = new ThemeController(new FakeColorSchemeProvider(ThemeBlock.DARK));

        ThemeTestSupport.onFx(() -> attachTo(controller));

        assertThat(ThemeTestSupport.onFx(controller::mode)).isEqualTo(ThemeMode.SYSTEM);
    }

    // IF the theme goes to dark and back to light, THEN the same 41 role names resolve in each block but bg differs.
    @Test
    void setMode_darkThenLight_resolvesSameRoleNamesWithDifferentValues() {
        final ThemeController controller = new ThemeController(new FakeColorSchemeProvider(null));
        ThemeTestSupport.onFx(() -> attachTo(controller));
        final Set<String> lightRoles = resolvedRoleNames();
        final Paint lightBg = ThemeTestSupport.resolveRole(scene, "bg");

        ThemeTestSupport.onFx(() -> switchTo(controller, ThemeMode.DARK));
        final Set<String> darkRoles = resolvedRoleNames();
        final Paint darkBg = ThemeTestSupport.resolveRole(scene, "bg");
        ThemeTestSupport.onFx(() -> switchTo(controller, ThemeMode.LIGHT));
        final Set<String> backToLightRoles = resolvedRoleNames();

        assertThat(lightRoles).hasSize(41).isEqualTo(COLOUR_ROLES);
        assertThat(darkRoles).isEqualTo(lightRoles);
        assertThat(backToLightRoles).isEqualTo(lightRoles);
        assertThat(darkBg).isNotEqualTo(lightBg);
    }

    // IF the mode is set to follow the system while the operating system reports light, THEN the light block applies.
    @Test
    void setMode_systemWhileProviderReportsLight_appliesLightBlock() {
        final ThemeController controller = new ThemeController(new FakeColorSchemeProvider(ThemeBlock.LIGHT));
        ThemeTestSupport.onFx(() -> attachTo(controller));
        ThemeTestSupport.onFx(() -> switchTo(controller, ThemeMode.DARK));

        ThemeTestSupport.onFx(() -> switchTo(controller, ThemeMode.SYSTEM));

        ThemeTestSupport.assertSameColour(ThemeTestSupport.resolveRole(scene, "bg"), LIGHT_BG, "bg");
        assertThat(ThemeTestSupport.onFx(controller::activeBlock)).isEqualTo(ThemeBlock.LIGHT);
    }

    // IF a person picks dark while following the system, THEN the mode is DARK (no longer following the system).
    @Test
    void setMode_darkWhileSystem_modeBecomesDark() {
        final ThemeController controller = new ThemeController(new FakeColorSchemeProvider(ThemeBlock.LIGHT));
        ThemeTestSupport.onFx(() -> attachTo(controller));

        ThemeTestSupport.onFx(() -> switchTo(controller, ThemeMode.DARK));

        assertThat(ThemeTestSupport.onFx(controller::mode)).isEqualTo(ThemeMode.DARK);
        assertThat(ThemeTestSupport.onFx(controller::activeBlock)).isEqualTo(ThemeBlock.DARK);
    }

    // IF dark was chosen explicitly and the operating system later reports light, THEN the dark block stays in force.
    @Test
    void setMode_darkThenProviderChangesToLight_darkBlockStays() {
        final FakeColorSchemeProvider provider = new FakeColorSchemeProvider(ThemeBlock.DARK);
        final ThemeController controller = new ThemeController(provider);
        ThemeTestSupport.onFx(() -> attachTo(controller));
        ThemeTestSupport.onFx(() -> switchTo(controller, ThemeMode.DARK));

        provider.report(ThemeBlock.LIGHT);

        ThemeTestSupport.assertSameColour(ThemeTestSupport.resolveRole(scene, "bg"), DARK_BG, "bg");
        assertThat(ThemeTestSupport.onFx(controller::mode)).isEqualTo(ThemeMode.DARK);
    }

    // IF the title-bar control is used while the light block is in force, THEN dark applies and the mode is DARK.
    @Test
    void toggle_fromLight_givesDarkBlockAndExplicitDarkMode() {
        final ThemeController controller = new ThemeController(new FakeColorSchemeProvider(ThemeBlock.LIGHT));
        ThemeTestSupport.onFx(() -> attachTo(controller));

        ThemeTestSupport.onFx(() -> toggle(controller));

        ThemeTestSupport.assertSameColour(ThemeTestSupport.resolveRole(scene, "bg"), DARK_BG, "bg");
        assertThat(ThemeTestSupport.onFx(controller::mode)).isEqualTo(ThemeMode.DARK);
        assertThat(ThemeTestSupport.onFx(controller::activeBlock)).isEqualTo(ThemeBlock.DARK);
    }

    // IF the observable mode did not follow setMode, THEN a control showing the mode would go stale the moment another
    // control changed it.
    @Test
    void modeProperty_followsSetMode() {
        final ThemeController controller = new ThemeController(new FakeColorSchemeProvider(null));
        ThemeTestSupport.onFx(() -> attachTo(controller));
        assertThat(ThemeTestSupport.onFx(() -> controller.modeProperty().get())).isEqualTo(ThemeMode.SYSTEM);

        ThemeTestSupport.onFx(() -> switchTo(controller, ThemeMode.DARK));
        assertThat(ThemeTestSupport.onFx(() -> controller.modeProperty().get())).isEqualTo(ThemeMode.DARK);

        ThemeTestSupport.onFx(() -> switchTo(controller, ThemeMode.SYSTEM));
        assertThat(ThemeTestSupport.onFx(() -> controller.modeProperty().get())).isEqualTo(ThemeMode.SYSTEM);
    }

    // IF the title-bar toggle changed the mode without the observable mode following, THEN the appearance selector
    // would not move with it.
    @Test
    void modeProperty_followsToggle() {
        final ThemeController controller = new ThemeController(new FakeColorSchemeProvider(ThemeBlock.LIGHT));
        ThemeTestSupport.onFx(() -> attachTo(controller));

        ThemeTestSupport.onFx(() -> toggle(controller));
        assertThat(ThemeTestSupport.onFx(() -> controller.modeProperty().get())).isEqualTo(ThemeMode.DARK);

        ThemeTestSupport.onFx(() -> toggle(controller));
        assertThat(ThemeTestSupport.onFx(() -> controller.modeProperty().get())).isEqualTo(ThemeMode.LIGHT);
    }

    // IF a listener on the observable mode were not told of a change, THEN a view bound to it would never repaint.
    @Test
    void modeProperty_listenerAdded_isToldOfEachChangeInOrder() {
        final ThemeController controller = new ThemeController(new FakeColorSchemeProvider(null));
        ThemeTestSupport.onFx(() -> attachTo(controller));
        final List<ThemeMode> seen = new ArrayList<>();
        ThemeTestSupport.onFx(() -> {
            controller.modeProperty().addListener((observed, was, now) -> seen.add(now));
            return null;
        });

        ThemeTestSupport.onFx(() -> switchTo(controller, ThemeMode.DARK));
        ThemeTestSupport.onFx(() -> switchTo(controller, ThemeMode.LIGHT));

        assertThat(seen).containsExactly(ThemeMode.DARK, ThemeMode.LIGHT);
    }

    // IF a scene is attached twice, THEN the theme stylesheet is listed exactly once.
    @Test
    void attach_calledTwice_addsStylesheetOnce() {
        final ThemeController controller = new ThemeController(new FakeColorSchemeProvider(null));

        ThemeTestSupport.onFx(() -> attachTo(controller));
        ThemeTestSupport.onFx(() -> attachTo(controller));

        assertThat(scene.getStylesheets()).containsOnlyOnce(Theme.stylesheet());
    }

    // IF the theme is switched, THEN no inline style is written to the root: only the style class changes.
    @Test
    void setMode_switching_leavesRootInlineStyleEmpty() {
        final ThemeController controller = new ThemeController(new FakeColorSchemeProvider(null));
        ThemeTestSupport.onFx(() -> attachTo(controller));

        ThemeTestSupport.onFx(() -> switchTo(controller, ThemeMode.DARK));

        assertThat(scene.getRoot().getStyle()).isEmpty();
        assertThat(scene.getRoot().getStyleClass()).contains(Theme.DARK_STYLE_CLASS);
    }

    private Set<String> resolvedRoleNames() {
        return COLOUR_ROLES.stream()
                .filter(role -> ThemeTestSupport.resolveRole(scene, role) != null)
                .collect(Collectors.toSet());
    }

    private @Nullable Object attachTo(final ThemeController controller) {
        controller.attach(scene);
        return null;
    }

    private static @Nullable Object switchTo(final ThemeController controller, final ThemeMode mode) {
        controller.setMode(mode);
        return null;
    }

    private static @Nullable Object toggle(final ThemeController controller) {
        controller.toggle();
        return null;
    }
}
