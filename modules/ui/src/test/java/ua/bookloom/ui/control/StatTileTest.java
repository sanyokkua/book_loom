package ua.bookloom.ui.control;

import static org.assertj.core.api.Assertions.assertThat;

import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import ua.bookloom.ui.FitProbe;
import ua.bookloom.ui.FxTestBase;
import ua.bookloom.ui.ThemeTestSupport;

/** A count tile's figure is never cut: the row gives the other tiles' long captions the lines, not the figure. */
@SuppressWarnings("NullAway.Init")
class StatTileTest extends FxTestBase {

    private Scene scene;

    @Override
    public void start(final Stage stage) {
        scene = ThemeTestSupport.themedScene();
        final HBox row = new HBox(
                16,
                new StatTile("written", figure("142 з 142"), "сегментів записано"),
                new StatTile("auto", figure("115"), "перекладено без змін"),
                new StatTile("reviewed", figure("1"), "переглянуто вами"),
                new StatTile("valid", figure("✓"), "перевірено: відкривається, нічого не втрачено, усе на місці"));
        ((StackPane) scene.getRoot()).getChildren().add(row);
        stage.setScene(scene);
        stage.show();
    }

    private static Label figure(final String text) {
        return new Label(text);
    }

    // IF the row squeezed the first tile to its 120 px floor, THEN "142 з 142" would read "142 з 1...".
    @ParameterizedTest
    @ValueSource(doubles = {560, 640, 720})
    void tiles_narrowRowWithALongCaption_figureIsWhole(final double width) {
        interact(() -> {
            scene.getWindow().setWidth(width);
            scene.getRoot().applyCss();
            scene.getRoot().layout();
        });

        assertThat(ThemeTestSupport.onFx(() -> FitProbe.cutParts(scene.getRoot())))
                .isEmpty();
    }
}
