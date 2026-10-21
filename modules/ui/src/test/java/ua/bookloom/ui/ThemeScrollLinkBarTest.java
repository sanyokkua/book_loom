package ua.bookloom.ui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.ScrollBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import org.controlsfx.control.SegmentedButton;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * What the theme paints for the three things a person must be able to find: the scrollbar's thumb on its track in the
 * content and in the navigation column, an underlined link in the information role, and the bar under the chosen
 * segment. Each is read from a real node in a themed scene, in both value blocks.
 */
@SuppressWarnings("NullAway.Init")
class ThemeScrollLinkBarTest extends FxTestBase {

    private static final double UI_RATIO = 3.0;
    private static final double TEXT_RATIO = 4.5;
    private static final double BAR_PIXELS = 2;
    private static final double BAR_WIDTH_PIXELS = 12;

    private Scene scene;
    private ScrollPane content;
    private ScrollPane navigation;
    private SegmentedButton three;
    private SegmentedButton alone;

    @Override
    public void start(final Stage stage) {
        scene = ThemeTestSupport.themedScene();
        content = tall("content-scroll");
        navigation = tall("nav-scroll");
        three = segmented("Fast", "Balanced", "Max");
        alone = segmented("Only");
        final VBox column = new VBox(content, navigation, three, alone);
        ((StackPane) scene.getRoot()).getChildren().add(column);
        stage.setScene(scene);
        stage.setWidth(400);
        stage.setHeight(700);
        stage.show();
    }

    private static ScrollPane tall(final String styleClass) {
        final Region body = new Region();
        body.setPrefSize(100, 900);
        final ScrollPane pane = new ScrollPane(body);
        pane.getStyleClass().add(styleClass);
        pane.setPrefSize(150, 150);
        pane.setMinSize(150, 150);
        pane.setMaxSize(150, 150);
        return pane;
    }

    private static SegmentedButton segmented(final String... labels) {
        final SegmentedButton bar = new SegmentedButton();
        for (final String label : labels) {
            bar.getButtons().add(new ToggleButton(label));
        }
        return bar;
    }

    private void layoutIn(final boolean dark) {
        ThemeTestSupport.applyTheme(scene, dark);
        ThemeTestSupport.onFx(() -> {
            scene.getRoot().applyCss();
            scene.getRoot().layout();
            return null;
        });
    }

    private static ScrollBar verticalBar(final ScrollPane pane) {
        return (ScrollBar) pane.lookupAll(".scroll-bar").stream()
                .filter(node ->
                        node instanceof ScrollBar bar && bar.getOrientation() == javafx.geometry.Orientation.VERTICAL)
                .findFirst()
                .orElseThrow();
    }

    private static Color fillOf(final Node node) {
        return (Color) ((Region) node).getBackground().getFills().get(0).getFill();
    }

    private Color role(final String name) {
        return (Color) Objects.requireNonNull(ThemeTestSupport.resolveRole(scene, name), name);
    }

    static Stream<Arguments> panesInEachBlock() {
        return Stream.of(true, false)
                .flatMap(dark -> Stream.of(
                        Arguments.of(Named.of("content", "content"), dark, "muted-2", "surface-2"),
                        Arguments.of(Named.of("navigation", "navigation"), dark, "nav-fg-muted", "nav-bg-2")));
    }

    // IF the thumb merged into its track, THEN a person could not see that more lies below the edge, nor grab it;
    // the bar is 12 wide, drawn without a pointer, and its thumb holds 3:1 against its track in all four pairs.
    @ParameterizedTest(name = "{0} bar, dark={1}")
    @MethodSource("panesInEachBlock")
    void scrollBar_overflowingPane_isTwelveWideWithAThumbThatReadsAgainstItsTrack(
            final String which, final boolean dark, final String thumbRole, final String trackRole) {
        layoutIn(dark);

        final ScrollPane pane = "content".equals(which) ? content : navigation;
        final ScrollBar bar = ThemeTestSupport.onFx(() -> verticalBar(pane));
        final Color thumb = ThemeTestSupport.onFx(() -> fillOf(bar.lookup(".thumb")));
        final Color track = ThemeTestSupport.onFx(() -> fillOf(bar.lookup(".track")));

        assertThat(ThemeTestSupport.onFx(bar::isVisible)).isTrue();
        assertThat(ThemeTestSupport.onFx(bar::getWidth)).isCloseTo(BAR_WIDTH_PIXELS, within(0.5));
        assertThat(thumb).isEqualTo(ThemeTestSupport.onFx(() -> role(thumbRole)));
        assertThat(track).isEqualTo(ThemeTestSupport.onFx(() -> role(trackRole)));
        assertThat(Contrast.ratio(thumb, track)).isGreaterThanOrEqualTo(UI_RATIO);
    }

    static Stream<Arguments> surfacesInEachBlock() {
        return Stream.of(true, false)
                .flatMap(dark -> Stream.of("bg", "surface", "surface-2", "surface-alt", "selected")
                        .map(surface -> Arguments.of(surface, dark)));
    }

    // IF the link colour fell below 4.5:1 on any surface it appears on, THEN the weakest one (a selected row) would
    // be unreadable; the link is also underlined, so colour is not the only sign.
    @ParameterizedTest(name = "on {0}, dark={1}")
    @MethodSource("surfacesInEachBlock")
    void hyperlink_onEverySurface_isUnderlinedInTheInformationRoleAtFourPointFiveToOne(
            final String surface, final boolean dark) {
        final Hyperlink link = new Hyperlink("Show them");
        final StackPane behind = new StackPane(new StackPane(link));
        ThemeTestSupport.onFx(() -> {
            behind.getChildren().get(0).setStyle("-fx-background-color: -color-" + surface + ";");
            ((StackPane) scene.getRoot()).getChildren().add(behind);
            return null;
        });
        layoutIn(dark);

        final Color fill = ThemeTestSupport.onFx(() -> (Color) link.getTextFill());
        final double ratio = ThemeTestSupport.onFx(() -> Contrast.ratio(fill, Contrast.surfaceBehind(link)));

        assertThat(ThemeTestSupport.onFx(link::isUnderline)).isTrue();
        assertThat(fill).isEqualTo(ThemeTestSupport.onFx(() -> role("info-fg")));
        assertThat(ratio).isGreaterThanOrEqualTo(TEXT_RATIO);
    }

    private List<BackgroundFill> fills(final ToggleButton button) {
        return ThemeTestSupport.onFx(() -> button.getBackground().getFills());
    }

    // IF the chosen segment differed from its neighbours by its fill alone (1.24:1), THEN Fast · Balanced · Max could
    // not be told apart; every pill variant carries a 2px strong-text bar along its foot.
    @ParameterizedTest(name = "pill {0}, dark={1}")
    @MethodSource("pillsInEachBlock")
    void segmentedButton_selectedPill_carriesATwoPixelStrongTextBarAndItsNeighboursNone(
            final int index, final boolean dark) {
        layoutIn(dark);
        ThemeTestSupport.onFx(() -> {
            three.getButtons().get(index).setSelected(true);
            scene.getRoot().applyCss();
            return null;
        });

        final ToggleButton chosen = three.getButtons().get(index);
        final List<BackgroundFill> chosenFills = fills(chosen);
        final Color barColour = (Color) chosenFills.get(0).getFill();
        final Insets fillInsets = chosenFills.get(1).getInsets();

        assertThat(chosenFills).hasSize(2);
        assertThat(barColour).isEqualTo(ThemeTestSupport.onFx(() -> role("text-strong")));
        assertThat(chosenFills.get(0).getInsets()).isEqualTo(Insets.EMPTY);
        assertThat(fillInsets.getBottom()).isEqualTo(BAR_PIXELS);
        assertThat(fillInsets.getTop() + fillInsets.getLeft() + fillInsets.getRight())
                .isZero();
        assertThat(three.getButtons().stream()
                        .filter(button -> !button.equals(chosen))
                        .toList())
                .allSatisfy(button -> assertThat(fills(button)).hasSize(1));
        assertThat(Contrast.ratio(barColour, ThemeTestSupport.onFx(() -> Contrast.surfaceBehind(three))))
                .isGreaterThanOrEqualTo(UI_RATIO);
    }

    static Stream<Arguments> pillsInEachBlock() {
        return Stream.of(true, false).flatMap(dark -> Stream.of(0, 1, 2).map(index -> Arguments.of(index, dark)));
    }

    // IF a lone segment (no left or right pill class) missed the bar, THEN a one-option selector would show none.
    @ParameterizedTest(name = "dark={0}")
    @ValueSource(booleans = {false, true})
    void segmentedButton_loneSelectedSegment_carriesTheBarToo(final boolean dark) {
        layoutIn(dark);
        ThemeTestSupport.onFx(() -> {
            alone.getButtons().get(0).setSelected(true);
            scene.getRoot().applyCss();
            return null;
        });

        final List<BackgroundFill> chosenFills = fills(alone.getButtons().get(0));

        assertThat(chosenFills).hasSize(2);
        assertThat(chosenFills.get(1).getInsets().getBottom()).isEqualTo(BAR_PIXELS);
    }
}
