package ua.bookloom.ui;

import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.Labeled;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** The hover bubble paints with the title bar's published pair under each value block, so it reads on both themes. */
class TooltipStyleTest extends FxTestBase {

    // Assigned in start(), which FxTestBase runs before every test.
    @SuppressWarnings("NullAway.Init")
    private Scene scene;

    @Override
    public void start(final Stage stage) {
        scene = ThemeTestSupport.themedScene();
        stage.setScene(scene);
        stage.show();
    }

    // IF the bubble kept Modena's default, THEN it would be unreadable on one theme; it must use the published pair.
    @ParameterizedTest
    @CsvSource({"false,#324148,#dfe4e6", "true,#1c2429,#dfe4e6"})
    void tooltipClass_underEachBlock_paintsTheTitleBarPair(
            final boolean dark, final String background, final String text) {
        final Label bubble = ThemeTestSupport.onFx(() -> {
            final Label label = new Label("A hover explanation");
            label.getStyleClass().add("tooltip");
            ((StackPane) scene.getRoot()).getChildren().add(label);
            return label;
        });
        ThemeTestSupport.applyTheme(scene, dark);

        final Object fill = ThemeTestSupport.onFx(
                () -> bubble.getBackground().getFills().get(0).getFill());
        ThemeTestSupport.assertSameColour(fill, background, "tooltip background dark=" + dark);
        ThemeTestSupport.assertSameColour(
                ThemeTestSupport.onFx(bubble::getTextFill), text, "tooltip text dark=" + dark);
    }

    // IF the popup window did not inherit the owner scene's tokens, THEN the real bubble would lose the pair the
    // styled label above shows, so the shown tooltip itself is read.
    @ParameterizedTest
    @CsvSource({"false,#324148,#dfe4e6", "true,#1c2429,#dfe4e6"})
    void shownTooltip_underEachBlock_paintsTheTitleBarPair(
            final boolean dark, final String background, final String text) {
        ThemeTestSupport.applyTheme(scene, dark);
        final Tooltip tooltip = new Tooltip("A hover explanation");
        ThemeTestSupport.onFx(() -> {
            tooltip.show(scene.getWindow());
            return null;
        });

        // The popup's window root holds a bridge that holds the label the tooltip's skin paints.
        final Labeled bubble = ThemeTestSupport.onFx(() -> {
            tooltip.getScene().getRoot().applyCss();
            return (Labeled) tooltip.getScene().getRoot().lookup(".tooltip .tooltip");
        });
        final Object fill = ThemeTestSupport.onFx(
                () -> bubble.getBackground().getFills().get(0).getFill());
        ThemeTestSupport.assertSameColour(fill, background, "shown tooltip background dark=" + dark);
        ThemeTestSupport.assertSameColour(
                ThemeTestSupport.onFx(bubble::getTextFill), text, "shown tooltip text dark=" + dark);
        ThemeTestSupport.onFx(() -> {
            tooltip.hide();
            return null;
        });
    }
}
