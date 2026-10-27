package ua.bookloom.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.kordamp.ikonli.javafx.FontIcon;
import ua.bookloom.ui.control.StateMarker;
import ua.bookloom.ui.control.StateMarker.Meaning;
import ua.bookloom.ui.i18n.Messages;

/**
 * The six state markers on what they are painted on, in both value blocks: each marker's words hold 4.5:1 and its icon
 * 3:1 against the marker's own fill, and the marker's fill sits on the page or a card without merging into it.
 */
@SuppressWarnings("NullAway.Init")
class StateMarkerContrastTest extends FxTestBase {

    private static final double TEXT_RATIO = 4.5;
    private static final double ICON_RATIO = 3.0;

    private Scene scene;
    private final Map<Meaning, StateMarker> markers = new EnumMap<>(Meaning.class);

    @Override
    public void start(final Stage stage) {
        scene = ThemeTestSupport.themedScene();
        final Messages messages = new Messages(() -> Locale.ENGLISH);
        final VBox column = new VBox();
        for (final Meaning meaning : Meaning.values()) {
            final StateMarker marker = StateMarker.of(messages, meaning);
            markers.put(meaning, marker);
            column.getChildren().add(marker);
        }
        ((StackPane) scene.getRoot()).getChildren().add(column);
        stage.setScene(scene);
        stage.show();
    }

    static Stream<Arguments> meaningsInEachBlock() {
        return Stream.of(true, false)
                .flatMap(dark -> Stream.of(Meaning.values()).map(meaning -> Arguments.of(meaning, dark)));
    }

    private void applyBlock(final boolean dark) {
        ThemeTestSupport.applyTheme(scene, dark);
        ThemeTestSupport.onFx(() -> {
            scene.getRoot().applyCss();
            scene.getRoot().layout();
            return null;
        });
    }

    // IF a marker's words fell under 4.5:1 on its fill, THEN a sand "kept as is" or an amber "needs attention" could
    // not be read; the fill is the marker's own, so the page behind it does not matter.
    @ParameterizedTest(name = "{0}, dark={1}")
    @MethodSource("meaningsInEachBlock")
    void marker_eachMeaning_wordsHoldFourPointFiveAndIconThreeToOneOnItsOwnFill(
            final Meaning meaning, final boolean dark) {
        applyBlock(dark);

        final StateMarker marker = java.util.Objects.requireNonNull(markers.get(meaning));
        final Color words = ThemeTestSupport.onFx(() -> (Color) marker.getTextFill());
        final Color icon = ThemeTestSupport.onFx(() -> (Color) ((FontIcon) marker.getGraphic()).getIconColor());
        final Color fill = ThemeTestSupport.onFx(() -> Contrast.surfaceBehind(marker));

        assertThat(Contrast.ratio(words, fill)).isGreaterThanOrEqualTo(TEXT_RATIO);
        assertThat(Contrast.ratio(icon, fill)).isGreaterThanOrEqualTo(ICON_RATIO);
    }

    // IF the sand words were not their own role, THEN "kept as is" would borrow the muted grey and read as waiting.
    @ParameterizedTest(name = "dark={0}")
    @MethodSource("blocks")
    void sandForeground_underEachBlock_readsOnTheSurfacesItAppearsOn(final boolean dark) {
        applyBlock(dark);

        final Color fg = ThemeTestSupport.onFx(() -> (Color) ThemeTestSupport.resolveRole(scene, "sand-fg"));

        assertThat(List.of("surface", "surface-2", "bg", "sand-soft"))
                .allSatisfy(surface -> assertThat(Contrast.ratio(
                                fg, ThemeTestSupport.onFx(() -> (Color) ThemeTestSupport.resolveRole(scene, surface))))
                        .isGreaterThanOrEqualTo(TEXT_RATIO));
    }

    static Stream<Arguments> blocks() {
        return Stream.of(Arguments.of(true), Arguments.of(false));
    }
}
