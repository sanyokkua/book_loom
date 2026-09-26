package ua.bookloom.ui.screen;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Control;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextInputControl;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.Region;
import org.controlsfx.control.SegmentedButton;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import ua.bookloom.ui.ShellTestBase;
import ua.bookloom.ui.ThemeTestSupport;
import ua.bookloom.ui.ViewNames;
import ua.bookloom.ui.theme.ThemeMode;

/**
 * The appearance area of the settings screen as the application builds it: the light / dark / system selector, the
 * fixed accent beside it, and what must not be there. The shell's operating system always reports the light scheme, so
 * "system" resolves to light. Expected colours are the published catalogue values, written out by hand.
 */
class AppearanceViewTest extends ShellTestBase {

    private static final String COGNAC = "#a58075";

    private void showAppearance() {
        onFx(() -> shell.activate(ViewNames.SETTINGS));
        final TabPane tabs = (TabPane) required("settings-tabs");
        final Tab appearance = tabs.getTabs().stream()
                .filter(tab -> "settings-tab-appearance".equals(tab.getId()))
                .findFirst()
                .orElseThrow();
        onFx(() -> tabs.getSelectionModel().select(appearance));
    }

    private ToggleButton toggle(final String id) {
        return (ToggleButton) required(id);
    }

    private static Stream<Node> descendantsOf(final Node root) {
        return root instanceof Parent parent
                ? Stream.concat(
                        Stream.of(root),
                        parent.getChildrenUnmodifiable().stream().flatMap(AppearanceViewTest::descendantsOf))
                : Stream.of(root);
    }

    // IF the selector were missing an option, or offered them in another order or under other names, THEN a person
    // could not reach a theme the specification promises, or would meet a different vocabulary than the reference.
    @Test
    void themeSelector_appearanceShown_offersLightDarkAndMatchSystemInOrder() {
        showAppearance();

        assertThat(required("settings-appearance-host").lookup(".segmented-button"))
                .isInstanceOf(SegmentedButton.class);
        assertThat(List.of(toggle("appearance-light"), toggle("appearance-dark"), toggle("appearance-system")))
                .extracting(ToggleButton::getText)
                .containsExactly("Light", "Dark", "Match system");
        assertThat(((SegmentedButton) required("settings-appearance-host").lookup(".segmented-button")).getButtons())
                .extracting(Node::getId)
                .containsExactly("appearance-light", "appearance-dark", "appearance-system");
    }

    // IF the selector did not show the theme already in force, THEN it would claim a choice the window is not
    // wearing.
    @Test
    void themeSelector_lightModeInForce_onlyLightIsSelected() {
        showAppearance();

        assertThat(toggle("appearance-light").isSelected()).isTrue();
        assertThat(toggle("appearance-dark").isSelected()).isFalse();
        assertThat(toggle("appearance-system").isSelected()).isFalse();
    }

    // IF choosing an option did not set the mode, or the scene did not repaint with the matching block, THEN the
    // selector would be decoration; "system" must follow the operating system, which here reports light.
    @ParameterizedTest
    @CsvSource({
        "appearance-dark, LIGHT, DARK, #283237",
        "appearance-light, DARK, LIGHT, #f4f1ea",
        "appearance-system, DARK, SYSTEM, #f4f1ea",
    })
    void themeSelector_optionChosen_setsModeRepaintsAndSelectsIt(
            final String optionId,
            final ThemeMode startMode,
            final ThemeMode expectedMode,
            final String backgroundHex) {
        onFx(() -> themeController.setMode(startMode));
        showAppearance();

        onFx(() -> toggle(optionId).fire());

        assertThat(ThemeTestSupport.onFx(themeController::mode)).isEqualTo(expectedMode);
        assertThat(toggle(optionId).isSelected()).isTrue();
        ThemeTestSupport.assertSameColour(ThemeTestSupport.resolveRole(scene, "bg"), backgroundHex, "bg");
    }

    // IF two options could read as selected at once, THEN the selector would not say which theme is in force.
    @Test
    void themeSelector_darkChosen_deselectsTheOthers() {
        showAppearance();

        onFx(() -> toggle("appearance-dark").fire());

        assertThat(toggle("appearance-light").isSelected()).isFalse();
        assertThat(toggle("appearance-system").isSelected()).isFalse();
    }

    // IF the title-bar toggle changed the theme without the selector following, THEN the two places the specification
    // gives for choosing a theme would disagree with each other.
    @Test
    void themeSelector_titleBarToggleUsed_selectionFollows() {
        showAppearance();
        assertThat(toggle("appearance-light").isSelected()).isTrue();

        onFx(themeController::toggle);
        assertThat(toggle("appearance-dark").isSelected()).isTrue();
        assertThat(toggle("appearance-light").isSelected()).isFalse();

        onFx(themeController::toggle);
        assertThat(toggle("appearance-light").isSelected()).isTrue();
        assertThat(toggle("appearance-dark").isSelected()).isFalse();
    }

    // IF clicking the selected option again left the selector with nothing selected, THEN it would stop saying which
    // theme is in force while the mode had not changed at all.
    @Test
    void themeSelector_selectedOptionClickedAgain_staysSelectedAndModeUnchanged() {
        showAppearance();

        onFx(() -> toggle("appearance-light").fire());

        assertThat(Stream.of("appearance-light", "appearance-dark", "appearance-system")
                        .filter(id -> toggle(id).isSelected()))
                .containsExactly("appearance-light");
        assertThat(ThemeTestSupport.onFx(themeController::mode)).isEqualTo(ThemeMode.LIGHT);
    }

    // IF a mode set from elsewhere, such as follow-the-system, did not move the selection, THEN the selector would
    // show a stale choice.
    @Test
    void themeSelector_modeSetToSystemElsewhere_systemIsSelected() {
        showAppearance();

        onFx(() -> themeController.setMode(ThemeMode.SYSTEM));

        assertThat(toggle("appearance-system").isSelected()).isTrue();
        assertThat(toggle("appearance-light").isSelected()).isFalse();
    }

    // IF the accent row were missing a part, or worded differently, THEN it would not read as the deliberate
    // fixed-in-this-version value the reference shows.
    @Test
    void accentRow_appearanceShown_showsSwatchNameAndFixedTag() {
        showAppearance();
        final Node row = required("appearance-accent-row");

        assertThat(required("appearance-accent-swatch"))
                .isInstanceOf(Region.class)
                .isNotInstanceOf(Control.class);
        assertThat(((Label) required("appearance-accent-value")).getText()).isEqualTo("Cognac #a58075");
        assertThat(((Label) required("appearance-accent-fixed")).getText()).isEqualTo("fixed");
        assertThat(descendantsOf(row))
                .contains(
                        required("appearance-accent-swatch"),
                        required("appearance-accent-value"),
                        required("appearance-accent-fixed"));
    }

    // IF the swatch painted another colour in either block, THEN the accent would not be the Cognac the value beside
    // it names.
    @ParameterizedTest
    @EnumSource(
            value = ThemeMode.class,
            names = {"LIGHT", "DARK"})
    void accentSwatch_underEachBlock_paintsCognac(final ThemeMode block) {
        showAppearance();
        onFx(() -> themeController.setMode(block));

        final Region swatch = (Region) required("appearance-accent-swatch");

        assertThat(swatch.getBackground())
                .as("the swatch must paint a background")
                .isNotNull();
        ThemeTestSupport.assertSameColour(
                swatch.getBackground().getFills().get(0).getFill(), COGNAC, "accent swatch under " + block);
    }

    // IF any control that edits the accent sat in the accent row, THEN it would offer a choice this version does not
    // have, when the value is read-only rather than disabled.
    @Test
    void accentRow_appearanceShown_holdsNoInputControl() {
        showAppearance();

        assertThat(descendantsOf(required("appearance-accent-row")))
                .noneMatch(node -> node instanceof TextInputControl
                        || node instanceof ComboBox
                        || node instanceof ChoiceBox
                        || node instanceof ColorPicker
                        || node instanceof ToggleButton
                        || node instanceof Slider);
    }

    // IF a language control appeared here, THEN it would offer a choice `localization` says only the operating
    // system makes; a combo or choice box anywhere in the area would be the usual shape of one.
    @Test
    void appearanceArea_shown_hasNoLanguageControl() {
        showAppearance();
        final Node host = required("settings-appearance-host");

        assertThat(descendantsOf(host)).noneMatch(node -> node instanceof ComboBox || node instanceof ChoiceBox);
        assertThat(textsUnder(host))
                .noneMatch(text -> text.toLowerCase(Locale.ROOT).contains("language"));
    }
}
