package ua.bookloom.ui;

import static org.assertj.core.api.Assertions.assertThat;

import javafx.geometry.Bounds;
import javafx.geometry.Rectangle2D;
import javafx.scene.Node;
import javafx.scene.control.Control;
import javafx.scene.control.Tooltip;
import javafx.stage.Screen;
import javafx.stage.Window;
import org.junit.jupiter.api.Test;
import org.testfx.util.WaitForAsyncUtils;

/**
 * A tooltip of a control at the window's right edge, with the window at the screen's right edge, opens where the
 * pointer is and is pulled back onto the screen: it is a window of its own, so the main window does not cut it, and the
 * screen does not either (QA: tooltips near the right window edge looked clipped).
 */
class TooltipScreenEdgeTest extends ShellTestBase {

    // The popup's width is fractional and JavaFX places it on whole pixels.
    private static final double SNAP = 1;

    // IF a tooltip opened at the pointer ran past the screen, THEN its last words could not be read on any display.
    @Test
    void tooltip_controlAtTheScreensRightEdge_staysOnTheScreen() {
        resizeScene(CONTENT_AT_MINIMUM_WIDTH, CONTENT_AT_MINIMUM_HEIGHT);
        final Rectangle2D screen = Screen.getPrimary().getVisualBounds();
        onFx(() -> {
            final Window window = scene.getWindow();
            window.setX(screen.getMaxX() - window.getWidth());
        });
        final Node about = required("shell-about");
        final Tooltip tip = ThemeTestSupport.onFx(() -> ((Control) about).getTooltip());

        onFx(() -> {
            final Bounds at = about.localToScreen(about.getLayoutBounds());
            tip.show(about, at.getMaxX() - 1, at.getMaxY());
        });
        WaitForAsyncUtils.waitForFxEvents();

        final double right = ThemeTestSupport.onFx(() -> tip.getX() + tip.getWidth());
        onFx(tip::hide);
        assertThat(tip.getText()).isNotBlank();
        assertThat(right).as("the tooltip's right edge").isLessThanOrEqualTo(screen.getMaxX() + SNAP);
    }
}
