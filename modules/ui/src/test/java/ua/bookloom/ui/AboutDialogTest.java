package ua.bookloom.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;
import javafx.event.Event;
import javafx.event.EventType;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Region;
import javafx.scene.paint.Paint;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import ua.bookloom.ui.theme.ThemeMode;

/**
 * The About dialog is a card shown in the shell's modal host over the dimming layer. It reports the product, the
 * licence and the build version, and it goes away by its Close button, by Escape and by a click on the dimmed area
 * outside it. Input is delivered as events to the node a person would have hit, so the test does not depend on a
 * display server or a pointer robot.
 */
class AboutDialogTest extends ShellTestBase {

    private static final double CLICK_X = 4;
    private static final double CLICK_Y = 4;

    private Node modalHost() {
        return required("shell-modal-host");
    }

    private void openAbout() {
        onFx(() -> ((Button) required("shell-about")).fire());
    }

    /** {@code true} when the card is in the scene and visible; it may be removed or hidden once dismissed. */
    private boolean isCardShowing() {
        final Node card = scene.getRoot().lookup("#about-card");
        return card != null && card.isVisible() && card.getScene() != null;
    }

    /** A whole primary-button click, so the dialog may react to the press, the release or the click itself. */
    private void click(final Node target) {
        target.fireEvent(mouse(MouseEvent.MOUSE_PRESSED));
        target.fireEvent(mouse(MouseEvent.MOUSE_RELEASED));
        target.fireEvent(mouse(MouseEvent.MOUSE_CLICKED));
    }

    private static MouseEvent mouse(final EventType<MouseEvent> type) {
        return new MouseEvent(
                type,
                CLICK_X,
                CLICK_Y,
                CLICK_X,
                CLICK_Y,
                MouseButton.PRIMARY,
                1,
                false,
                false,
                false,
                false,
                true,
                false,
                false,
                false,
                false,
                true,
                null);
    }

    private void pressClose() {
        ((Button) required("about-close")).fire();
    }

    private void pressEscape() {
        Event.fireEvent(
                required("about-card"),
                new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.ESCAPE, false, false, false, false));
    }

    private void clickScrim() {
        click(required("shell-scrim"));
    }

    /** The ways a person can dismiss the dialog, each named for the test report. */
    static Stream<Arguments> dismissals() {
        return Stream.of(
                Arguments.of(Named.<Consumer<AboutDialogTest>>of("Close button", AboutDialogTest::pressClose)),
                Arguments.of(Named.<Consumer<AboutDialogTest>>of("Escape key", AboutDialogTest::pressEscape)),
                Arguments.of(Named.<Consumer<AboutDialogTest>>of("click on the scrim", AboutDialogTest::clickScrim)));
    }

    /** Fires a Tab press at whatever owns focus, as the keyboard would, and lets the pulse settle. */
    private void pressTab() {
        onFx(() -> Event.fireEvent(
                scene.getFocusOwner(),
                new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.TAB, false, false, false, false)));
    }

    @Test
    void about_beforeItIsUsed_showsNoCardAndNoScrim() {
        // IF the scrim were up with no dialog, THEN the window would be dimmed for nothing.
        assertThat(required("shell-scrim").isVisible()).isFalse();
        assertThat(isCardShowing()).isFalse();
    }

    @Test
    void about_action_showsTheCardInTheModalHostOverAVisibleScrim() {
        // IF the About action did not raise the card and dim the window, THEN nothing would report the build.
        openAbout();

        final Node card = modalHost().lookup("#about-card");
        assertThat(card).as("the card belongs inside the modal host").isNotNull();
        assertThat(card.getStyleClass()).contains("dialog-card");
        assertThat(isCardShowing()).isTrue();
        assertThat(required("shell-scrim").isVisible()).isTrue();
    }

    @Test
    void about_card_namesTheProductTheLicenceAndTheDevBuildVersion() {
        // IF the card left out the product, licence or version, THEN a bug report could not name the build it ran.
        openAbout();

        assertThat(textsUnder(required("about-card"))).contains("BookLoom").contains("Version dev · MIT license");
    }

    @Test
    void about_card_hasACloseButtonLabelledClose() {
        // IF the card had no Close button, THEN a pointer-only person could not dismiss it without the scrim.
        openAbout();

        assertThat(required("about-card").lookup("#about-close")).isInstanceOf(Button.class);
    }

    // IF any of the three dismissals left the card or the scrim up, THEN the person would be stuck behind a dialog.
    @ParameterizedTest
    @MethodSource("dismissals")
    void about_dismissed_hidesTheCardAndTheScrim(final Consumer<AboutDialogTest> how) {
        openAbout();

        onFx(() -> how.accept(this));

        assertThat(isCardShowing()).isFalse();
        assertThat(required("shell-scrim").isVisible()).isFalse();
    }

    @Test
    void about_clickOnTheCardItself_keepsTheDialogOpen() {
        // IF a click inside the card counted as a click outside it, THEN reading the card would close it.
        openAbout();

        onFx(() -> click(required("about-card")));

        assertThat(isCardShowing()).isTrue();
        assertThat(required("shell-scrim").isVisible()).isTrue();
    }

    @Test
    void about_openedAgainAfterBeingDismissed_showsTheCardAgain() {
        // IF dismissal left the host in a state that refused a second card, THEN About would work only once.
        openAbout();
        onFx(this::pressClose);

        openAbout();

        assertThat(isCardShowing()).isTrue();
        assertThat(required("shell-scrim").isVisible()).isTrue();
    }

    @Test
    void about_closed_returnsFocusToTheControlThatOpenedIt() {
        // IF focus were left on the removed card, THEN keyboard use would need a click to get back into the shell.
        final Button about = (Button) required("shell-about");
        interact(about::requestFocus);
        assertThat(scene.getFocusOwner()).isSameAs(about);
        openAbout();
        assertThat(scene.getFocusOwner()).isNotSameAs(about);

        onFx(this::pressClose);

        assertThat(scene.getFocusOwner()).isSameAs(about);
    }

    @Test
    void about_open_keepsTabInsideTheCardAcrossSeveralPresses() {
        // IF Tab could leave the card, THEN focus would reach a navigation button hidden behind the dimmed window.
        openAbout();

        pressTab();
        pressTab();
        pressTab();

        final Node owner = scene.getFocusOwner();
        assertThat(owner).isNotNull();
        assertThat(ancestorsOf(owner)).contains(required("about-card"));
        assertThat(ancestorsOf(owner)).doesNotContain(required("shell-nav"));
    }

    private static List<Node> ancestorsOf(final Node node) {
        final List<Node> chain = new ArrayList<>();
        for (Node at = node; at != null; at = at.getParent()) {
            chain.add(at);
        }
        return chain;
    }

    // IF the card were painted with anything but the surface and border roles of the block in force, THEN it would
    // not match the reference in that block. Expected values are the published catalogue values for each block.
    @ParameterizedTest
    @CsvSource({
        "LIGHT, #ffffff, #ddd5c8",
        "DARK, #33424a, #48585f",
    })
    void about_card_paintsWithTheSurfaceAndBorderRolesOfTheBlockInForce(
            final ThemeMode mode, final String surface, final String border) {
        onFx(() -> themeController.setMode(mode));
        openAbout();

        final Region card = (Region) required("about-card");
        assertThat(card.getBackground()).as("the card must paint a background").isNotNull();
        assertThat(card.getBorder()).as("the card must draw a border").isNotNull();
        final Paint fill = card.getBackground().getFills().get(0).getFill();
        final Paint stroke = card.getBorder().getStrokes().get(0).getTopStroke();

        ThemeTestSupport.assertSameColour(fill, surface, "about card background under " + mode);
        ThemeTestSupport.assertSameColour(stroke, border, "about card border under " + mode);
    }
}
