package ua.bookloom.ui.control;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import javafx.event.Event;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ua.bookloom.ui.FxTestBase;
import ua.bookloom.ui.Theme;
import ua.bookloom.ui.ThemeTestSupport;
import ua.bookloom.ui.control.LayoutClass.Width;

/**
 * The shared two-column layout: side by side in an area of its class or wider, stacked in a narrower one, left
 * before right in the focus order either way, equal columns with one gap, no inline style, and no cut caption or
 * sideways scrolling at the two window sizes the app is held to.
 */
@SuppressWarnings("NullAway.Init")
class TwoColumnsTest extends FxTestBase {

    // The one gap between the columns and between the stacked cards, in px (the theme's 16px rule for .two-columns).
    private static final double GAP = 16;
    private static final double EDGE = 0.51;
    private static final String LONG_TEXT =
            "The old house stood at the end of a lane that nobody used any more, and its "
                    + "windows, which had once faced the sea, now looked onto a field of tall grass. ".repeat(3);

    private Stage stage;
    private ScrollPane host;
    private TwoColumns columns;
    private VBox left;
    private VBox right;

    @Override
    public void start(final Stage primary) {
        this.stage = primary;
    }

    private static VBox simpleLeft() {
        final VBox box = new VBox(button("l1"), button("l2"));
        box.setId("left");
        return box;
    }

    private static VBox simpleRight() {
        final TextField field = new TextField();
        field.setId("r1");
        final VBox box = new VBox(field, button("r2"));
        box.setId("right");
        return box;
    }

    private static Button button(final String id) {
        final Button button = new Button("Caption " + id);
        button.setId(id);
        return button;
    }

    // Representative content: captions, a long wrapped paragraph and a table whose columns want 1,080 px.
    private static VBox richLeft() {
        final Label title = new Label("Languages");
        title.setId("left-title");
        final Label text = new Label(LONG_TEXT);
        text.setId("left-text");
        text.setWrapText(true);
        final Button action = new Button("Suggest from the book");
        action.setId("left-action");
        final VBox box = new VBox(title, text, action);
        box.setId("left");
        return box;
    }

    private static VBox richRight() {
        final Label title = new Label("Names and terms");
        title.setId("right-title");
        final Label text = new Label(LONG_TEXT);
        text.setId("right-text");
        text.setWrapText(true);
        final TableView<String> table = new TableView<>();
        table.setId("right-table");
        for (final String name : List.of("Term", "Type", "Target", "Gender", "Locked", "Remove")) {
            final TableColumn<String, String> column = new TableColumn<>(name);
            column.setPrefWidth(180);
            table.getColumns().add(column);
        }
        final VBox box = new VBox(title, text, table);
        box.setId("right");
        return box;
    }

    /**
     * Shows the columns in a scroll pane that fits the width as the app's content area does, at a scene size, inside
     * an area classified by {@link LayoutClass} as the content area is.
     */
    private void show(
            final double width, final double height, final Width from, final VBox leftBox, final VBox rightBox) {
        ThemeTestSupport.onFx(() -> {
            left = leftBox;
            right = rightBox;
            columns = new TwoColumns(leftBox, rightBox, from);
            columns.setId("columns");
            final VBox area = new VBox(columns);
            LayoutClass.install(area);
            host = new ScrollPane(area);
            host.setFitToWidth(true);
            final Scene scene = new Scene(host, width, height);
            scene.getStylesheets().add(Theme.stylesheet());
            stage.setScene(scene);
            // The primary stage is shared and keeps the last test's size, so size it to this test's scene.
            stage.sizeToScene();
            stage.setX(0);
            stage.setY(0);
            stage.show();
            return null;
        });
        interact(() -> {});
    }

    private void limitWidth(final double width) {
        interact(() -> columns.setMaxWidth(width));
        interact(() -> {});
    }

    private double width(final Node node) {
        return ThemeTestSupport.onFx(() -> node.getLayoutBounds().getWidth());
    }

    // Layout positions, not boundsInParent: the focused first button's focus ring sits 1.4 px outside its box and
    // would move the visual bounds of the column that holds it, which says nothing about where the column was put.
    private double x(final Node node) {
        return ThemeTestSupport.onFx(
                () -> node.getLayoutX() + node.getLayoutBounds().getMinX());
    }

    private double y(final Node node) {
        return ThemeTestSupport.onFx(
                () -> node.getLayoutY() + node.getLayoutBounds().getMinY());
    }

    private double bottom(final Node node) {
        return ThemeTestSupport.onFx(
                () -> node.getLayoutY() + node.getLayoutBounds().getMaxY());
    }

    private double right(final Node node) {
        return ThemeTestSupport.onFx(
                () -> node.getLayoutX() + node.getLayoutBounds().getMaxX());
    }

    // IF the columns stayed stacked where their class allows two and their content fits, THEN a wide window would
    // waste half its width.
    @ParameterizedTest(name = "{0} px")
    @CsvSource({"700", "701", "1150"})
    void layout_inTheirClassWithRoom_placesTheColumnsSideBySideInEqualWidths(final double contentWidth) {
        show(1440, 900, Width.REGULAR, simpleLeft(), simpleRight());
        limitWidth(contentWidth);

        assertThat(columns.isStacked()).isFalse();
        assertThat(y(right)).isCloseTo(y(left), within(EDGE));
        assertThat(x(left)).isCloseTo(0, within(EDGE));
        assertThat(width(left)).isCloseTo(width(right), within(EDGE));
        assertThat(x(right)).isCloseTo(right(left) + GAP, within(EDGE));
        assertThat(right(right)).isCloseTo(contentWidth, within(EDGE));
    }

    // IF the columns stayed side by side in an area narrower than their class, THEN each would be too narrow to read.
    @ParameterizedTest(name = "{0} px")
    @CsvSource({"1039", "944", "656"})
    void layout_inANarrowerClass_stacksTheRightColumnUnderTheLeftAtFullWidth(final double contentWidth) {
        show(contentWidth, 900, Width.REGULAR, simpleLeft(), simpleRight());

        assertThat(columns.isStacked()).isTrue();
        assertThat(x(left)).isCloseTo(0, within(EDGE));
        assertThat(x(right)).isCloseTo(0, within(EDGE));
        assertThat(width(left)).isCloseTo(width(columns), within(EDGE));
        assertThat(width(right)).isCloseTo(width(columns), within(EDGE));
        assertThat(y(right)).isCloseTo(bottom(left) + GAP, within(EDGE));
    }

    // IF the control's own height ignored the stacked column, THEN the scroll pane could not reach the bottom card.
    @Test
    void layout_stacked_isAsTallAsBothColumnsAndTheGap() {
        show(960, 640, Width.REGULAR, simpleLeft(), simpleRight());

        assertThat(columns.isStacked()).isTrue();
        assertThat(ThemeTestSupport.onFx(() -> columns.getLayoutBounds().getHeight()))
                .isGreaterThanOrEqualTo(bottom(right) - EDGE);
    }

    // IF a window crossing a class floor left the old layout, THEN a resize would need a screen reopen.
    @Test
    void layout_windowCrossesTheBreakpointBothWays_switchesEachTime() {
        show(1200, 700, Width.REGULAR, simpleLeft(), simpleRight());
        assertThat(columns.isStacked()).as("1200 wide").isFalse();

        resizeWindow(900, 700);
        assertThat(columns.isStacked()).as("900 wide").isTrue();
        assertThat(y(right)).as("right below left when stacked").isGreaterThan(bottom(left));

        resizeWindow(1200, 700);
        assertThat(columns.isStacked()).as("1200 wide again").isFalse();
        assertThat(y(right)).as("right level with left again").isCloseTo(y(left), within(EDGE));
    }

    private void resizeWindow(final double width, final double height) {
        interact(() -> {
            final Window window = stage;
            final Scene scene = window.getScene();
            window.setWidth(width + window.getWidth() - scene.getWidth());
            window.setHeight(height + window.getHeight() - scene.getHeight());
        });
        interact(() -> {});
    }

    // IF a pair whose own width is capped missed the area's class change, THEN it would keep the old layout.
    @Test
    void areaClass_changesWhileThePairsWidthStays_relaysOutAtTheNewClass() {
        show(1500, 900, Width.WIDE, simpleLeft(), simpleRight());
        limitWidth(800);
        assertThat(columns.isStacked()).isFalse();

        resizeWindow(1300, 900);

        assertThat(width(columns)).isCloseTo(800, within(EDGE));
        assertThat(columns.isStacked()).isTrue();
        assertThat(y(right)).isGreaterThan(bottom(left));
    }

    // IF Tab went right before left in either layout, THEN the keyboard order would not match the reading order.
    @ParameterizedTest(name = "{0} px")
    @CsvSource({"1440", "944"})
    void tab_fromTheFirstControl_walksLeftThenRightInEitherLayout(final double windowWidth) {
        show(windowWidth, 900, Width.REGULAR, simpleLeft(), simpleRight());
        interact(() -> host.getScene().lookup("#l1").requestFocus());

        final List<String> walked = new ArrayList<>();
        for (int press = 0; press < 3; press++) {
            walked.add(ThemeTestSupport.onFx(this::pressTab));
        }

        assertThat(walked).containsExactly("l2", "r1", "r2");
    }

    private String pressTab() {
        final Scene scene = host.getScene();
        Event.fireEvent(
                scene.getFocusOwner(),
                new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.TAB, false, false, false, false));
        return String.valueOf(scene.getFocusOwner().getId());
    }

    // IF the children were reordered to stack, THEN focus order would differ; the children stay left, right.
    @ParameterizedTest(name = "{0} px")
    @CsvSource({"1440", "944"})
    void children_inEitherLayout_areLeftThenRight(final double windowWidth) {
        show(windowWidth, 900, Width.REGULAR, simpleLeft(), simpleRight());

        assertThat(columns.getChildrenUnmodifiable()).containsExactly(left, right);
    }

    // IF a caption were cut or the page scrolled sideways, THEN the person would lose words or have to hunt for them.
    @ParameterizedTest(name = "{0} x {1}, side by side from {2} -> stacked {3}")
    @CsvSource({
        "960, 640, COMPACT, false",
        "960, 640, REGULAR, true",
        "1440, 900, REGULAR, false",
        "1500, 900, WIDE, false",
        "1300, 900, WIDE, true"
    })
    void layout_atEachWindowSize_cutsNoCaptionAndNeedsNoSidewaysScrolling(
            final double width, final double height, final Width from, final boolean stacked) {
        show(width, height, from, richLeft(), richRight());

        assertThat(columns.isStacked()).isEqualTo(stacked);
        assertThat(cutLabels(Set.of("left-title", "left-text", "left-action", "right-title", "right-text")))
                .as("captions that draw less than they say")
                .isEmpty();
        assertThat(ThemeTestSupport.onFx(
                        () -> host.getContent().getLayoutBounds().getWidth()))
                .as("content width against the viewport")
                .isLessThanOrEqualTo(
                        ThemeTestSupport.onFx(() -> host.getViewportBounds().getWidth()) + EDGE);
        assertThat(horizontalBarShown()).as("a horizontal scroll bar").isFalse();
        assertThat(right(right)).as("right column's edge").isLessThanOrEqualTo(width(columns) + EDGE);
    }

    private boolean horizontalBarShown() {
        return ThemeTestSupport.onFx(() -> host.lookupAll(".scroll-bar").stream()
                .anyMatch(node -> node instanceof ScrollBar bar
                        && bar.getOrientation() == Orientation.HORIZONTAL
                        && bar.isVisible()));
    }

    private List<String> cutLabels(final Set<String> ids) {
        return ThemeTestSupport.onFx(() -> ids.stream()
                .sorted()
                .filter(id -> {
                    final javafx.scene.control.Labeled labeled =
                            (javafx.scene.control.Labeled) host.getScene().lookup("#" + id);
                    return labeled.isTextTruncated() || !drawn(labeled).equals(labeled.getText());
                })
                .toList());
    }

    private static String drawn(final javafx.scene.control.Labeled labeled) {
        return labeled.getChildrenUnmodifiable().stream()
                .filter(javafx.scene.text.Text.class::isInstance)
                .map(node -> ((javafx.scene.text.Text) node).getText())
                .collect(java.util.stream.Collectors.joining());
    }

    // IF a style were set on the control or its columns, THEN no theme swap could reach it.
    @Test
    void styling_control_usesAStyleClassAndNoInlineStyle() {
        show(1440, 900, Width.REGULAR, simpleLeft(), simpleRight());

        assertThat(columns.getStyleClass()).contains("two-columns");
        assertThat(columns.getStyle()).isEmpty();
        assertThat(left.getStyle()).isEmpty();
        assertThat(right.getStyle()).isEmpty();
    }

    // IF the class were not in the theme, THEN the gap would be a number hidden in Java.
    @Test
    void theme_stylesheet_definesTheTwoColumnsClass() {
        assertThat(ThemeTestSupport.rules(ThemeTestSupport.themeCss())).containsKey(".two-columns");
    }

    // IF a null column were accepted, THEN the screen would fail later at layout with no name for the cause.
    @Test
    @SuppressWarnings("NullAway")
    void constructor_nullColumn_isRejectedByName() {
        assertThatThrownBy(() -> new TwoColumns(null, simpleRight(), Width.REGULAR))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("left");
        assertThatThrownBy(() -> new TwoColumns(simpleLeft(), null, Width.REGULAR))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("right");
    }
}
