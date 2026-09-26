package ua.bookloom.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;
import javafx.scene.Scene;
import javafx.scene.control.Labeled;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.testfx.framework.junit5.ApplicationTest;

/**
 * The theming mechanism every later screen depends on.
 *
 * <p>The scene root is the real {@link AppShellView}, so the cascade is proved through the chrome every screen sits
 * in: a looked-up colour declared on {@code .root} must reach labels several levels down the tree.
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

    /** The published light value of the {@code muted} role (09_THEMING.md#token-catalog). */
    private static final String EXPECTED_MUTED = "#6f7c82";

    /** The published light value of the {@code title-fg} role. */
    private static final String EXPECTED_TITLE_FG = "#dfe4e6";

    // Assigned in start(), which ApplicationTest runs before every test.
    @SuppressWarnings("NullAway.Init")
    private Scene scene;

    @Override
    public void start(final Stage stage) {
        final AppShellView shell = UiTestInjector.create(Locale.ENGLISH).getInstance(AppShellView.class);
        scene = new Scene(shell.root(), 640, 400);
        scene.getStylesheets().add(Theme.stylesheet());
        stage.setScene(scene);
        stage.show();
    }

    @Test
    void stylesheet_attachedAtSceneLevel_cascadesTheMutedRoleToTheBreadcrumbLabel() {
        // The breadcrumb sits in the toolbar inside the frame inside the root; its text fill can only be the muted
        // role if the .root block reached it. A stylesheet that failed to parse leaves the platform default.
        final Labeled breadcrumb = lookup("#shell-breadcrumb").queryLabeled();

        ThemeTestSupport.assertSameColour(
                breadcrumb.getTextFill(), EXPECTED_MUTED, "breadcrumb text fill (-color-muted) through .root");
    }

    @Test
    void stylesheet_attachedAtSceneLevel_cascadesTheTitleForegroundToTheProductName() {
        final Labeled product = lookup(".shell-title").queryLabeled();

        ThemeTestSupport.assertSameColour(
                product.getTextFill(), EXPECTED_TITLE_FG, "product name text fill (-color-title-fg) through .root");
    }

    @Test
    void stylesheet_attachedToTheScene_isListedExactlyOnce() {
        assertThat(scene.getStylesheets())
                .as("attached at Scene level, not on a node: node-level attachment would not cascade from .root")
                .hasSize(1);
    }
}
