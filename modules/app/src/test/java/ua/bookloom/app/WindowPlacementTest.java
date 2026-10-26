package ua.bookloom.app;

import static org.assertj.core.api.Assertions.assertThat;

import javafx.stage.Screen;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.testfx.api.FxToolkit;
import org.testfx.util.WaitForAsyncUtils;

/**
 * "Restart now" brings the window back where it was: the closing copy writes its place and size, and the next copy
 * puts its window there, unless that place is on no screen any more.
 */
class WindowPlacementTest {

    @Test
    void parse_writtenPlacement_readsBackTheSameNumbers() {
        assertThat(WindowPlacement.parse("40 60 1300 820 false"))
                .contains(new WindowPlacement.Placement(40, 60, 1300, 820, false));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "40 60 1300", "a b c d false", "40 60 -5 820 false"})
    void parse_unreadable_isEmpty(final String text) {
        assertThat(WindowPlacement.parse(text)).isEmpty();
    }

    @Test
    void apply_placeOnTheScreen_movesAndSizesTheWindow() throws Exception {
        FxToolkit.registerPrimaryStage();
        final javafx.geometry.Rectangle2D screen = onFx(() -> Screen.getPrimary().getVisualBounds());
        final WindowPlacement.Placement placement =
                new WindowPlacement.Placement(screen.getMinX() + 30, screen.getMinY() + 20, 1100, 760, false);

        final Stage stage = onFx(() -> {
            final Stage shown = new Stage();
            WindowPlacement.apply(shown, placement);
            return shown;
        });

        assertThat(onFx(() -> new double[] {stage.getX(), stage.getY(), stage.getWidth(), stage.getHeight()}))
                .containsExactly(screen.getMinX() + 30, screen.getMinY() + 20, 1100, 760);
    }

    @Test
    void apply_placeOnNoScreen_leavesTheWindowAsItWas() throws Exception {
        FxToolkit.registerPrimaryStage();
        final Stage stage = onFx(() -> {
            final Stage shown = new Stage();
            shown.setWidth(1024);
            WindowPlacement.apply(shown, new WindowPlacement.Placement(-90_000, -90_000, 1300, 820, false));
            return shown;
        });

        assertThat(onFx(stage::getWidth)).isEqualTo(1024);
    }

    private static <T> T onFx(final java.util.concurrent.Callable<T> action) throws Exception {
        return WaitForAsyncUtils.asyncFx(action).get();
    }
}
