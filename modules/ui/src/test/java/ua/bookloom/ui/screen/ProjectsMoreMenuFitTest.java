package ua.bookloom.ui.screen;

import static org.assertj.core.api.Assertions.assertThat;

import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuButton;
import javafx.stage.Window;
import org.junit.jupiter.api.Test;
import org.testfx.util.WaitForAsyncUtils;
import ua.bookloom.ui.ThemeTestSupport;

/**
 * A project row's ⋯ menu sits at the right edge of the list; at the smallest window it opens towards the window
 * instead of past its right edge, where "Show book file" and "Remove project..." were cut off.
 */
class ProjectsMoreMenuFitTest extends ProjectsScreenTestBase {

    @Test
    void moreMenu_openedAtTheSmallestWindow_staysInsideTheWindow() throws Exception {
        resizeScene(CONTENT_AT_MINIMUM_WIDTH, CONTENT_AT_MINIMUM_HEIGHT);
        showListing(ukrainianRunning());

        onFx(() -> more().show());
        WaitForAsyncUtils.waitForFxEvents();

        final double[] edges = ThemeTestSupport.onFx(() -> {
            final Window menu = Window.getWindows().stream()
                    .filter(window -> window instanceof ContextMenu && window.isShowing())
                    .findFirst()
                    .orElseThrow();
            final Window main = scene.getWindow();
            return new double[] {menu.getX() + menu.getWidth(), main.getX() + main.getWidth()};
        });
        onFx(() -> more().hide());
        assertThat(edges[0]).as("the menu's right edge").isLessThanOrEqualTo(edges[1]);
    }

    private MenuButton more() {
        return java.util.Objects.requireNonNull(firstOf(node("projects-row-" + EARTH_ID), MenuButton.class), "more");
    }
}
