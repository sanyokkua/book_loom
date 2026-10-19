package ua.bookloom.ui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Stream;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.event.Event;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Control;
import javafx.scene.control.Labeled;
import javafx.scene.control.ListView;
import javafx.scene.control.Slider;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.Background;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import javafx.stage.Stage;
import org.controlsfx.control.SegmentedButton;
import org.controlsfx.control.ToggleSwitch;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Every kind of control the screens use tells its state in both themes: keyboard focus draws a ring in the focus role
 * (not the primary role, so it shows on a primary button too) that contrasts 3:1 with the surface around the control;
 * the quiet buttons darken when pressed; an unavailable input is dimmed like an unavailable button.
 */
@SuppressWarnings("NullAway.Init")
class ThemeFocusStatesTest extends FxTestBase {

    private static final double UI_RATIO = 3.0;
    private static final double TEXT_RATIO = 4.5;
    private static final double DISABLED_OPACITY = 0.45;
    private static final double OPACITY_TOLERANCE = 0.001;
    private static final double CONTAINER_PADDING = 16;

    private Scene scene;

    /** A control as a screen uses it: how to build it, the class of what it sits in, and its ring's role. */
    record Sample(Supplier<Control> make, @Nullable String container, String ringRole) {}

    @Override
    public void start(final Stage stage) {
        scene = ThemeTestSupport.themedScene();
        stage.setScene(scene);
        stage.show();
    }

    static Stream<Arguments> samplesInEachBlock() {
        return samples().flatMap(sample -> Stream.of(false, true).map(dark -> Arguments.of(sample, dark)));
    }

    private static Stream<Named<Sample>> samples() {
        return Stream.of(
                Named.of("primary button", new Sample(() -> button("btn-primary"), null, "focus")),
                Named.of("secondary button", new Sample(() -> button("btn-secondary"), null, "focus")),
                Named.of("ghost button", new Sample(() -> button("btn-ghost"), null, "focus")),
                Named.of("danger button", new Sample(() -> button("btn-danger-outline"), null, "focus")),
                Named.of("text field", new Sample(() -> new TextField("Victor"), null, "focus")),
                Named.of("combo box", new Sample(ThemeFocusStatesTest::combo, null, "focus")),
                Named.of("check box", new Sample(() -> new CheckBox("Bilingual"), null, "focus")),
                Named.of("switch", new Sample(() -> new ToggleSwitch("Locked"), null, "focus")),
                Named.of("segmented picker", new Sample(ThemeFocusStatesTest::segmented, null, "focus")),
                Named.of("slider", new Sample(() -> new Slider(0, 1, 0.5), null, "focus")),
                Named.of("tabs", new Sample(ThemeFocusStatesTest::tabs, null, "focus")),
                Named.of("list", new Sample(ThemeFocusStatesTest::list, null, "focus")),
                Named.of("table", new Sample(ThemeFocusStatesTest::table, null, "focus")),
                Named.of("tree", new Sample(ThemeFocusStatesTest::tree, null, "focus")),
                Named.of("filter chip", new Sample(ThemeFocusStatesTest::chip, null, "focus")),
                Named.of("navigation entry", new Sample(() -> button("nav-item"), "shell-nav", "nav-focus")),
                Named.of(
                        "title bar button",
                        new Sample(() -> button("shell-title-button"), "shell-title-bar", "nav-focus")));
    }

    // IF the focus ring were the primary colour, or merged into the surface, THEN a keyboard user loses their place.
    @ParameterizedTest(name = "{0} dark={1}")
    @MethodSource("samplesInEachBlock")
    void keyboardFocus_eachControl_drawsARingThatContrastsWithTheSurface(final Sample sample, final boolean dark) {
        final Control control = ThemeTestSupport.onFx(() -> place(sample));
        ThemeTestSupport.applyTheme(scene, dark);
        tabInto();

        final Color ring = (Color) role(sample.ringRole());
        final Node owner = ThemeTestSupport.onFx(scene::getFocusOwner);
        final Optional<Region> drawn = ThemeTestSupport.onFx(() -> ringOf(control, ring));

        assertThat(isInside(owner, control)).as("Tab reaches the control").isTrue();
        assertThat(owner.isFocusVisible()).as("focus came from the keyboard").isTrue();
        assertThat(drawn)
                .as("a border in the %s role is drawn", sample.ringRole())
                .isPresent();
        assertThat(ring).as("the ring is not the primary colour").isNotEqualTo(role("primary"));
        assertThat(Contrast.ratio(ring, ThemeTestSupport.onFx(() -> Contrast.surfaceBehind(control.getParent()))))
                .as("ring against the surface around the control")
                .isGreaterThanOrEqualTo(UI_RATIO);
    }

    // IF the arrow keys did not move along a segmented picker, THEN Tab (which stops once in the group) would leave the
    // other choices out of a keyboard user's reach.
    @Test
    void arrowKey_inSegmentedPicker_movesFocusToTheNextChoice() {
        final SegmentedButton picker = (SegmentedButton)
                ThemeTestSupport.onFx(() -> place(new Sample(ThemeFocusStatesTest::segmented, null, "focus")));
        tabInto();

        final Node owner = ThemeTestSupport.onFx(() -> {
            Event.fireEvent(
                    scene.getFocusOwner(),
                    new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.RIGHT, false, false, false, false));
            return scene.getFocusOwner();
        });

        assertThat(owner).isSameAs(picker.getButtons().get(1));
    }

    static Stream<Arguments> quietButtonsInEachBlock() {
        return Stream.of("btn-ghost", "btn-danger-outline")
                .flatMap(style -> Stream.of(false, true).map(dark -> Arguments.of(style, dark)));
    }

    // IF a quiet button looked the same pressed as hovered, THEN a click would give no feedback.
    @ParameterizedTest(name = "{0} dark={1}")
    @MethodSource("quietButtonsInEachBlock")
    void press_quietButton_changesItsFillAndKeepsItsWordsReadable(final String style, final boolean dark) {
        final Button button = (Button) ThemeTestSupport.onFx(() -> place(new Sample(() -> button(style), null, "")));
        ThemeTestSupport.applyTheme(scene, dark);

        moveTo(button);
        final Color hovered = fill(button);
        press(MouseButton.PRIMARY);
        final Color pressed = fill(button);
        final Color words = ThemeTestSupport.onFx(() -> (Color) button.getTextFill());
        release(MouseButton.PRIMARY);

        assertThat(pressed).as("pressed differs from hovered").isNotEqualTo(hovered);
        assertThat(Contrast.ratio(words, pressed))
                .as("words on the pressed fill")
                .isGreaterThanOrEqualTo(TEXT_RATIO);
    }

    static Stream<Named<Supplier<Control>>> inputs() {
        return Stream.of(
                Named.of("text field", TextField::new),
                Named.of("combo box", ThemeFocusStatesTest::combo),
                Named.of("check box", () -> new CheckBox("Bilingual")),
                Named.of("switch", () -> new ToggleSwitch("Locked")),
                Named.of("slider", Slider::new),
                Named.of("segmented picker", ThemeFocusStatesTest::segmented));
    }

    // IF an unavailable input looked available, THEN a person would try to use it and nothing would happen.
    @ParameterizedTest(name = "{0}")
    @MethodSource("inputs")
    void disable_input_isDimmedLikeAnUnavailableButton(final Supplier<Control> make) {
        final Control control = ThemeTestSupport.onFx(() -> place(new Sample(make, null, "")));

        final double opacity = ThemeTestSupport.onFx(() -> {
            control.setDisable(true);
            scene.getRoot().applyCss();
            return effectiveOpacity(control);
        });

        assertThat(opacity).isCloseTo(DISABLED_OPACITY, within(OPACITY_TOLERANCE));
    }

    // A segmented picker is dimmed through its toggles: what shows is the product of the opacities on the way down.
    private static double effectiveOpacity(final Control control) {
        final Node shown =
                control instanceof SegmentedButton picker ? picker.getButtons().get(0) : control;
        double opacity = 1;
        for (Node walk = shown; walk != null && !walk.equals(control.getParent()); walk = walk.getParent()) {
            opacity *= walk.getOpacity();
        }
        return opacity;
    }

    private Control place(final Sample sample) {
        final Button anchor = new Button("before");
        final Control control = sample.make().get();
        // The control sits in a holder painted like the place it is used in (a card's page, the navigation column).
        final VBox holder = new VBox(control);
        holder.setPadding(new Insets(CONTAINER_PADDING));
        if (sample.container() != null) {
            holder.getStyleClass().add(sample.container());
        }
        ((StackPane) scene.getRoot()).getChildren().setAll(new VBox(CONTAINER_PADDING, anchor, holder));
        scene.getRoot().applyCss();
        scene.getRoot().layout();
        anchor.requestFocus();
        return control;
    }

    private void tabInto() {
        ThemeTestSupport.onFx(() -> {
            Event.fireEvent(
                    scene.getFocusOwner(),
                    new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.TAB, false, false, false, false));
            scene.getRoot().applyCss();
            scene.getRoot().layout();
            return null;
        });
    }

    private Paint role(final String name) {
        final Paint paint = ThemeTestSupport.resolveRole(scene, name);
        assertThat(paint).as("role %s resolves", name).isNotNull();
        return paint;
    }

    private static Optional<Region> ringOf(final Control control, final Color ring) {
        return Contrast.shown(control).stream()
                .filter(Region.class::isInstance)
                .map(Region.class::cast)
                .filter(region -> Contrast.strokeColours(region.getBorder()).contains(ring))
                .findFirst();
    }

    private static boolean isInside(final @Nullable Node node, final Node container) {
        for (Node walk = node; walk != null; walk = walk.getParent()) {
            if (walk.equals(container)) {
                return true;
            }
        }
        return false;
    }

    private static Color fill(final Region region) {
        return ThemeTestSupport.onFx(() -> {
            region.applyCss();
            final Background background = region.getBackground();
            final List<Paint> fills = background == null
                    ? List.of()
                    : background.getFills().stream().map(f -> f.getFill()).toList();
            return fills.isEmpty() ? Color.TRANSPARENT : (Color) fills.get(fills.size() - 1);
        });
    }

    private static Button button(final String style) {
        final Button button = new Button("Retry with note");
        button.getStyleClass().add(style);
        return button;
    }

    private static Labeled chip() {
        final ToggleButton chip = new ToggleButton("names");
        chip.getStyleClass().add("review-chip");
        return chip;
    }

    private static ComboBox<String> combo() {
        final ComboBox<String> combo = new ComboBox<>(FXCollections.observableArrayList("Gothic novel", "Memoir"));
        combo.getSelectionModel().select(0);
        return combo;
    }

    private static SegmentedButton segmented() {
        final ToggleButton keep = new ToggleButton("Keep");
        final ToggleButton translate = new ToggleButton("Translate");
        keep.setSelected(true);
        return new SegmentedButton(keep, translate);
    }

    private static TabPane tabs() {
        return new TabPane(new Tab("Providers"), new Tab("Appearance"));
    }

    private static ListView<String> list() {
        return new ListView<>(FXCollections.observableArrayList("ch1 · p1", "ch1 · p2"));
    }

    private static TableView<String> table() {
        final TableView<String> table = new TableView<>(FXCollections.observableArrayList("Victor", "Elizabeth"));
        final TableColumn<String, String> column = new TableColumn<>("Source term");
        column.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue()));
        table.getColumns().add(column);
        return table;
    }

    private static TreeView<String> tree() {
        final TreeItem<String> root = new TreeItem<>("Frankenstein");
        root.getChildren().add(new TreeItem<>("Letter 1"));
        root.setExpanded(true);
        return new TreeView<>(root);
    }
}
