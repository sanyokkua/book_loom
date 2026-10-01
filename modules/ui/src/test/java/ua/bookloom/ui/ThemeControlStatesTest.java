package ua.bookloom.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import javafx.scene.Scene;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import javafx.stage.Stage;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** A control's state reads the same in both themes: an unchosen box shows no tick, an unavailable tab looks it. */
@SuppressWarnings("NullAway.Init")
class ThemeControlStatesTest extends FxTestBase {

    private Scene scene;
    private CheckBox unchosen;
    private CheckBox chosen;
    private TabPane tabs;

    @Override
    public void start(final Stage stage) {
        scene = ThemeTestSupport.themedScene();
        unchosen = new CheckBox("Bilingual");
        chosen = new CheckBox("Report");
        chosen.setSelected(true);
        final Tab selected = new Tab("Providers");
        final Tab available = new Tab("Appearance");
        final Tab unavailable = new Tab("Models");
        unavailable.setDisable(true);
        tabs = new TabPane(selected, unavailable, available);
        ((StackPane) scene.getRoot()).getChildren().add(new VBox(unchosen, chosen, tabs));
        stage.setScene(scene);
        stage.show();
    }

    private static List<Paint> visibleFills(final Region region) {
        final Background background = region.getBackground();
        return background == null
                ? List.of()
                : background.getFills().stream()
                        .map(BackgroundFill::getFill)
                        .filter(fill -> !(fill instanceof Color colour) || colour.getOpacity() > 0)
                        .toList();
    }

    private static @Nullable Paint tabText(final TabPane pane, final int index) {
        final Label label = (Label) pane.lookupAll(".tab").stream()
                .skip(index)
                .findFirst()
                .orElseThrow()
                .lookup(".tab-label");
        return label.getTextFill();
    }

    // IF the tick were painted on an unchosen box, THEN in the dark theme the light tick on the dark box reads as
    // checked.
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void checkBox_unchosen_showsNoTickInEitherTheme(final boolean dark) {
        ThemeTestSupport.applyTheme(scene, dark);

        assertThat(ThemeTestSupport.onFx(() -> visibleFills((Region) unchosen.lookup(".mark"))))
                .isEmpty();
    }

    // IF the tick were gone from a chosen box too, THEN no box would ever look checked.
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void checkBox_chosen_showsItsTick(final boolean dark) {
        ThemeTestSupport.applyTheme(scene, dark);

        assertThat(ThemeTestSupport.onFx(() -> visibleFills((Region) chosen.lookup(".mark"))))
                .isNotEmpty();
    }

    // IF an available tab were painted like an unavailable one, THEN Appearance would look as dead as Models.
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void tab_availableAndUnavailable_areToldApart(final boolean dark) {
        ThemeTestSupport.applyTheme(scene, dark);

        final Paint unavailable = ThemeTestSupport.onFx(() -> tabText(tabs, 1));
        final Paint available = ThemeTestSupport.onFx(() -> tabText(tabs, 2));

        assertThat(available).isEqualTo(ThemeTestSupport.resolveRole(scene, "text"));
        assertThat(unavailable).isEqualTo(ThemeTestSupport.resolveRole(scene, "muted"));
    }
}
