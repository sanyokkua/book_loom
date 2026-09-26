package ua.bookloom.ui.screen;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.concurrent.TimeoutException;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.Region;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.api.Result;
import ua.bookloom.ui.BookFixtures;
import ua.bookloom.ui.ThemeTestSupport;

/**
 * The import screen with a book that reports every card row, in the shell's 700 px scene, where the dropzone and card
 * are taller than the content area: the screen scrolls inside the shell instead of growing past it, so the title bar
 * and navigation stay where the shell put them.
 */
class ImportScreenScrollTest extends ImportScreenTestBase {

    @TempDir
    private Path dir;

    private void openFullCard() throws TimeoutException {
        final Path source = dir.resolve("Frankenstein.epub");
        port.on(source, Result.ok(BookFixtures.frankenstein()));
        openImport();
        openBook(source);
    }

    // IF the screen had no scroll container, THEN a card taller than the content area would push the whole shell up
    // and crop the title bar; the shell must stay exactly the size of the scene.
    @Test
    void screen_fullCard_keepsShellWithinTheScene() throws TimeoutException {
        openFullCard();

        final Region shellRoot = (Region) scene.getRoot();
        final Bounds titleBar = ThemeTestSupport.onFx(() -> required("shell-title-bar")
                .localToScene(required("shell-title-bar").getBoundsInLocal()));

        assertThat(titleBar.getMinY()).isZero();
        assertThat(ThemeTestSupport.onFx(shellRoot::getHeight)).isLessThanOrEqualTo(scene.getHeight());
    }

    // IF the screen were not the scroll container itself, THEN a person in a short window could never reach the
    // Continue control below the fold.
    @Test
    void screen_fullCard_scrollsVerticallyWithNoHorizontalBar() throws TimeoutException {
        openFullCard();

        final Node screen = required("import-screen");
        assertThat(screen).isInstanceOf(ScrollPane.class);
        final ScrollPane pane = (ScrollPane) screen;
        final double contentHeight =
                ThemeTestSupport.onFx(() -> pane.getContent().getLayoutBounds().getHeight());
        final double viewportHeight =
                ThemeTestSupport.onFx(() -> pane.getViewportBounds().getHeight());

        assertThat(contentHeight).isGreaterThan(viewportHeight);
        assertThat(pane.getHbarPolicy()).isEqualTo(ScrollPane.ScrollBarPolicy.NEVER);
        assertThat(pane.isFitToWidth()).isTrue();
    }
}
