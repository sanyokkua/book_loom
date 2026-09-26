package ua.bookloom.ui;

import static org.assertj.core.api.Assertions.assertThat;

import javafx.scene.Scene;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;
import org.controlsfx.control.ToggleSwitch;
import org.junit.jupiter.api.Test;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;
import org.testfx.framework.junit5.ApplicationTest;

/**
 * Proof that the two UI libraries this change depends on resolve and lay out under the JavaFX 26 headless platform.
 *
 * <p>A sibling of {@link HeadlessToolkitCanaryTest} rather than an extension of it, so that canary stays about the
 * toolkit alone. Tests run on the classpath, so this cannot prove the JPMS {@code requires} names — that is
 * {@code :ui:compileJava}'s job, on the module path.
 */
class LibraryResolutionCanaryTest extends ApplicationTest {

    private static final String TOGGLE_ID = "#canary-toggle";
    private static final String ICON_ID = "#canary-icon";
    private static final Feather REQUESTED_ICON = Feather.BOOK;

    @Override
    public void start(final Stage stage) {
        final ToggleSwitch toggle = new ToggleSwitch();
        toggle.setId(TOGGLE_ID.substring(1));
        final FontIcon icon = FontIcon.of(REQUESTED_ICON);
        icon.setId(ICON_ID.substring(1));
        stage.setScene(new Scene(new HBox(toggle, icon), 320, 200));
        stage.show();
    }

    // Proves the ControlsFX artifact resolves and a control from it is constructed and laid out headlessly.
    @Test
    void start_controlsFxToggleSwitch_isConstructedAndLaidOut() {
        final ToggleSwitch toggle = lookup(TOGGLE_ID).queryAs(ToggleSwitch.class);

        assertThat(toggle).isNotNull();
        assertThat(toggle.getWidth()).isGreaterThan(0.0);
    }

    // Proves the Feather pack module and its font resolved, not merely FontIcon's constructor: the icon code is the
    // one requested.
    @Test
    void start_ikonliFeatherIcon_isConstructedAndLaidOutWithTheRequestedGlyph() {
        final FontIcon icon = lookup(ICON_ID).queryAs(FontIcon.class);

        assertThat(icon).isNotNull();
        assertThat(icon.getLayoutBounds().getWidth()).isGreaterThan(0.0);
        assertThat(icon.getIconCode()).isEqualTo(REQUESTED_ICON);
    }
}
