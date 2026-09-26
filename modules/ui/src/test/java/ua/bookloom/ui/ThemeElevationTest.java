package ua.bookloom.ui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import javafx.scene.Scene;
import javafx.scene.effect.DropShadow;
import javafx.stage.Stage;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.testfx.framework.junit5.ApplicationTest;

/**
 * The three elevation roles are a real drop-shadow effect on a named style class, one value per block (D10).
 *
 * <p>A shadow written as a looked-up colour is silently dropped by the CSS engine and the element renders with
 * nothing, which is why these are asserted on the effect a real node ends up with rather than on stylesheet text.
 * Expected values are the published shadows: the offset's y and the blur, with the colour as published.
 */
class ThemeElevationTest extends ApplicationTest {

    private static final double NO_HORIZONTAL_OFFSET = 0.0;
    private static final double EXACT = 1e-9;

    // Assigned in start(), which ApplicationTest runs before every test.
    @SuppressWarnings("NullAway.Init")
    private Scene scene;

    @Override
    public void start(final Stage stage) {
        scene = ThemeTestSupport.themedScene();
        stage.setScene(scene);
        stage.show();
    }

    // IF an element carries an elevation class, THEN under each block it has a DropShadow with that block's
    // published colour, blur radius and vertical offset (and no horizontal offset, since none is published).
    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
            elevation-sm | false | rgba(58,74,82,.10)  | 2  | 1
            elevation    | false | rgba(58,74,82,.12)  | 10 | 3
            elevation-lg | false | rgba(35,45,50,.22)  | 34 | 12
            elevation-sm | true  | rgba(0,0,0,.3)      | 2  | 1
            elevation    | true  | rgba(0,0,0,.35)     | 14 | 4
            elevation-lg | true  | rgba(0,0,0,.5)      | 40 | 16
            """)
    void elevationClass_underEachBlock_carriesThePublishedDropShadow(
            final String styleClass,
            final boolean dark,
            final String colour,
            final double radius,
            final double offsetY) {
        ThemeTestSupport.applyTheme(scene, dark);

        final DropShadow shadow = ThemeTestSupport.asDropShadow(ThemeTestSupport.resolveElevation(scene, styleClass));

        ThemeTestSupport.assertSameColour(shadow.getColor(), colour, styleClass + " dark=" + dark);
        assertThat(shadow.getRadius()).as("%s blur radius", styleClass).isCloseTo(radius, within(EXACT));
        assertThat(shadow.getOffsetY()).as("%s vertical offset", styleClass).isCloseTo(offsetY, within(EXACT));
        assertThat(shadow.getOffsetX()).as("%s horizontal offset", styleClass).isEqualTo(NO_HORIZONTAL_OFFSET);
    }

    // IF a region has no elevation class, THEN it has no effect: the class, not the region, carries the shadow.
    @ParameterizedTest
    @CsvSource({"false", "true"})
    void plainRegion_withoutAnElevationClass_carriesNoEffect(final boolean dark) {
        ThemeTestSupport.applyTheme(scene, dark);

        assertThat(ThemeTestSupport.resolveElevation(scene, "not-an-elevation")).isNull();
    }

    // IF the three shadow roles are looked up as colours, THEN none resolves: they are effects, never colours.
    @ParameterizedTest
    @CsvSource({"shadow-sm", "shadow", "shadow-lg"})
    void shadowRole_lookedUpAsAColour_isNotDefined(final String role) {
        assertThat(ThemeTestSupport.resolveRole(scene, role)).isNull();
    }
}
