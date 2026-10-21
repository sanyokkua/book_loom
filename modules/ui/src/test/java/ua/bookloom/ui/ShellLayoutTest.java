package ua.bookloom.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.layout.Region;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ua.bookloom.ui.state.ShellBanners;

/**
 * The shell's layout rules that depend on its own regions: the pinned banner slot, the width classes on the content
 * host, the content caps, the navigation width and the screens that fill the window's height.
 */
class ShellLayoutTest extends ShellTestBase {

    private Bounds sceneBounds(final String id) {
        final Node node = required(id);
        return node.localToScene(node.getLayoutBounds());
    }

    // Through Import, as the screen tests arrive anywhere: a step stays closed until a book has been looked at.
    private void openAScreen(final ViewNames view) {
        onFx(() -> shell.activate(ViewNames.IMPORT));
        onFx(() -> shell.activate(view));
    }

    // IF the slot took room while empty, THEN the screen area would start lower than the toolbar's edge.
    @Test
    void bannerSlot_noBanner_hasNoHeightAndContentStartsUnderTheToolbar() {
        openAScreen(ViewNames.EXPORT);

        final Bounds slot = sceneBounds("shell-banners");
        final Bounds toolbar = sceneBounds("shell-breadcrumb");

        assertThat(slot.getHeight()).isZero();
        assertThat(sceneBounds("shell-content-scroll").getMinY()).isGreaterThanOrEqualTo(toolbar.getMaxY());
    }

    // IF a published banner did not show in the slot above the scroll area, THEN it would scroll away with the page.
    @Test
    void bannerSlot_bannerPublished_sitsBetweenToolbarAndScrollingContent() {
        final Region banner = new Region();
        banner.setId("probe-banner");
        banner.setMinHeight(30);
        banner.setPrefHeight(30);
        onFx(() -> injector.getInstance(ShellBanners.class).publish("probe", banner));

        final Bounds slot = sceneBounds("shell-banners");
        final Bounds scroll = sceneBounds("shell-content-scroll");

        assertThat(required("probe-banner").getParent()).isSameAs(required("shell-banners"));
        assertThat(slot.getHeight()).isGreaterThanOrEqualTo(30);
        assertThat(scroll.getMinY()).isGreaterThanOrEqualTo(slot.getMaxY());
        assertThat(slot.getMinY())
                .isGreaterThanOrEqualTo(sceneBounds("shell-breadcrumb").getMaxY());
    }

    // IF the withdrawn banner left its padding, THEN the slot would stay as tall as when it was shown.
    @Test
    void bannerSlot_bannerWithdrawn_collapsesAgain() {
        final Region banner = new Region();
        banner.setPrefHeight(30);
        onFx(() -> injector.getInstance(ShellBanners.class).publish("probe", banner));
        onFx(() -> injector.getInstance(ShellBanners.class).withdraw("probe"));

        assertThat(sceneBounds("shell-banners").getHeight()).isZero();
    }

    // IF the run's banner stayed inside the Translating page, THEN it would scroll away and show on that screen only.
    @Test
    void translatingScreen_banner_livesInTheShellSlotNotInTheScreen() {
        openAScreen(ViewNames.TRANSLATING);

        final Node banner = required("translating-banner");

        assertThat(banner.getParent()).isSameAs(required("shell-banners"));
        assertThat(required("translating-screen").lookup("#translating-banner")).isNull();
    }

    // IF the slot kept the banner after the screen was left, THEN a "ready to translate" strip would sit on Export.
    @Test
    void translatingScreen_leftForAnotherScreen_takesItsBannerOffTheSlot() {
        openAScreen(ViewNames.TRANSLATING);
        openAScreen(ViewNames.EXPORT);

        assertThat(scene.getRoot().lookup("#translating-banner")).isNull();
    }

    // IF the host did not carry the class, THEN CSS keyed to the width class would never apply.
    @ParameterizedTest
    @CsvSource({"1100, compact", "1300, regular", "1700, wide"})
    void contentHost_atASceneWidth_carriesOneWidthClassOfItsOwnWidth(final double sceneWidth, final String expected) {
        // The navigation column takes 236 (200 below 1200) of the scene, and the host fills the rest.
        resizeScene(sceneWidth, 700);

        final List<String> classes = onFxStates(required("shell-content"));

        assertThat(classes).containsExactly(expected);
    }

    // IF a form screen filled a 1920-pixel window, THEN lines of settings would run unreadably long.
    @Test
    void settingsScreen_atFullHdWidth_isCappedAt1320AndCentred() {
        resizeScene(1920, 700);
        openAScreen(ViewNames.SETTINGS);

        final Node screen = required("settings-screen");
        final Bounds bounds = screen.localToScene(screen.getLayoutBounds());
        final Bounds host = sceneBounds("shell-content-scroll");

        assertThat(bounds.getWidth()).isEqualTo(1320);
        assertThat(bounds.getMinX() - host.getMinX()).isEqualTo(host.getMaxX() - bounds.getMaxX());
    }

    // IF Translating were capped too, THEN its tiles and live card would leave the sides of a wide window empty.
    @Test
    void translatingScreen_atFullHdWidth_usesTheFullContentWidth() {
        resizeScene(1920, 700);
        openAScreen(ViewNames.TRANSLATING);

        assertThat(sceneBounds("translating-screen").getWidth()).isGreaterThan(1500);
    }

    // IF the prose were not capped, THEN a subtitle would run the full width of a wide window.
    @Test
    void screenSubtitle_atFullHdWidth_isCappedAt680() {
        resizeScene(1920, 700);
        openAScreen(ViewNames.SETTINGS);

        assertThat(sceneBounds("settings-subtitle").getWidth()).isLessThanOrEqualTo(680);
    }

    // IF the column kept 236 in a narrow window, THEN it would take a quarter of the screen that is left.
    @ParameterizedTest
    @CsvSource({"1100, 200", "1199, 200", "1200, 236", "1280, 236"})
    void navigation_atASceneWidth_isTwoHundredBelowTwelveHundredElseTwoThirtySix(
            final double sceneWidth, final double expected) {
        resizeScene(sceneWidth, 700);

        assertThat(sceneBounds("shell-nav").getWidth()).isEqualTo(expected);
    }

    // IF the shell held every screen at its preferred height, THEN a screen that fills the window could never shrink
    // to a minimum of its own; a screen that does not ask keeps the hold.
    @ParameterizedTest
    @CsvSource({
        "SETTINGS, true",
        "EXPORT, false",
    })
    void showContent_screenAskingToFill_isNotHeldAtItsPreferredHeight(final ViewNames view, final boolean fills) {
        openAScreen(view);

        final Region screen = (Region) navigator.content().get();

        assertThat(screen.getStyleClass().contains("fills-viewport")).isEqualTo(fills);
        assertThat(screen.getMinHeight() == Region.USE_PREF_SIZE).isEqualTo(!fills);
    }

    // IF a fill screen were not marked, THEN the shell would hold it at its preferred height whatever it declares.
    @ParameterizedTest
    @CsvSource({"TRANSLATING", "NAMES_STYLE", "SETTINGS", "IMPORT"})
    void fourScreens_whenShown_askToFillTheWindow(final ViewNames view) {
        openAScreen(view);

        assertThat(navigator.content().get().getStyleClass()).contains("fills-viewport");
    }

    // IF the fill opt-out were ignored for a screen, THEN a tall window would leave its lists short.
    @Test
    void namesStyleScreen_inATallWindow_fillsTheViewport() {
        resizeScene(1000, 900);
        openAScreen(ViewNames.NAMES_STYLE);

        final Bounds scroll = sceneBounds("shell-content-scroll");
        final Bounds screen = sceneBounds("names-style-screen");

        assertThat(screen.getHeight()).isGreaterThanOrEqualTo(scroll.getHeight() - 70);
    }

    private List<String> onFxStates(final Node node) {
        return ThemeTestSupport.onFx(() -> node.getPseudoClassStates().stream()
                .map(Object::toString)
                .filter(name -> List.of("compact", "regular", "wide").contains(name))
                .toList());
    }
}
