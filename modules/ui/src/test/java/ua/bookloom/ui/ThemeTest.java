package ua.bookloom.ui;

import static org.assertj.core.api.Assertions.assertThat;

import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.testfx.framework.junit5.ApplicationTest;

/**
 * The theming mechanism every later screen depends on.
 *
 * <p>The assertion is on the <strong>resolved colour</strong>, not on the presence of a stylesheet URL, and that
 * distinction is the whole value of the test: a stylesheet that fails to parse still attaches, and
 * {@code getStylesheets()} still lists it. Asking a real node what colour it ended up with is the only way to tell
 * "the theme is wired" from "a file with the right name is on the scene".
 *
 * <p>Runs under JavaFX 26's built-in headless platform (ADR-0019), selected by {@code bookloom.test-conventions};
 * no display server and no Monocle artifact are involved.
 */
class ThemeTest extends ApplicationTest {

    /** Charcoal — the brand anchor that {@code -color-text} resolves to in the light block. */
    private static final Color EXPECTED_TEXT = Color.web("#3a4a52");

    @Override
    public void start(final Stage stage) {
        final Scene scene = new Scene(AppShellView.create(), 640, 400);
        scene.getStylesheets().add(Theme.stylesheet());
        stage.setScene(scene);
        stage.show();
    }

    @Test
    void stylesheet_attachedAtSceneLevel_cascadesTheTokenToADescendantNode() {
        final Label placeholder = lookup("#" + AppShellView.PLACEHOLDER_ID).queryAs(Label.class);

        assertThat(placeholder.getTextFill())
                .as("-color-text must resolve through the .root token block to the charcoal brand anchor; a "
                        + "stylesheet that failed to parse would leave the platform default here")
                .isEqualTo(EXPECTED_TEXT);
    }

    @Test
    void stylesheet_lookedUpColor_isDeclaredOnRootRatherThanOnTheComponent() {
        final Label placeholder = lookup("#" + AppShellView.PLACEHOLDER_ID).queryAs(Label.class);

        // The token resolves from `.root`, so the same lookup must succeed from the scene root — which is what
        // makes one value block able to re-theme every descendant.
        assertThat(placeholder.getScene().getRoot().getStyleClass())
                .as("the shell carries the class the token block styles")
                .contains(AppShellView.SHELL_STYLE_CLASS);
        assertThat(placeholder.getScene().getStylesheets())
                .as("attached at Scene level, not on a node: node-level attachment would not cascade from .root")
                .hasSize(1);
    }
}
