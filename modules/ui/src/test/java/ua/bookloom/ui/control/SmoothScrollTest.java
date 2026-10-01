package ua.bookloom.ui.control;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.IntStream;
import javafx.event.Event;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.skin.VirtualFlow;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.testfx.framework.junit5.ApplicationTest;
import ua.bookloom.ui.ThemeTestSupport;

/**
 * A wheel notch glides the pane or list under the pointer to where JavaFX would have jumped, in several eased frames;
 * a trackpad's own events still jump at once, and a mouse press stops a glide.
 */
@SuppressWarnings("NullAway.Init")
class SmoothScrollTest extends ApplicationTest {

    private static final double VIEW_HEIGHT = 200;
    private static final double CONTENT_HEIGHT = 1000;
    private static final double NOTCH = -40;
    private static final double CELL = 20;
    private static final long SETTLE_MS = 500;
    private static final double EXACT = 1e-6;

    private ScrollPane pane;
    private Region content;
    private ListView<String> list;

    @Override
    public void start(final Stage stage) {
        content = new Region();
        content.setPrefSize(100, CONTENT_HEIGHT);
        content.setMinHeight(CONTENT_HEIGHT);
        pane = new ScrollPane(content);
        pane.setPadding(Insets.EMPTY);
        pane.setFitToWidth(true);
        pane.setPrefSize(150, VIEW_HEIGHT);
        list = new ListView<>();
        list.getItems().setAll(IntStream.range(0, 200).mapToObj(i -> "row " + i).toList());
        list.setFixedCellSize(CELL);
        list.setPrefSize(150, VIEW_HEIGHT);
        final HBox root = SmoothScroll.install(new HBox(pane, list));
        stage.setScene(new Scene(root, 300, VIEW_HEIGHT));
        stage.show();
    }

    private static ScrollEvent wheel(final double deltaY, final boolean inertia) {
        return new ScrollEvent(
                ScrollEvent.SCROLL,
                10,
                10,
                10,
                10,
                false,
                false,
                false,
                false,
                false,
                inertia,
                0,
                deltaY,
                0,
                deltaY,
                ScrollEvent.HorizontalTextScrollUnits.NONE,
                0,
                ScrollEvent.VerticalTextScrollUnits.LINES,
                deltaY / 40,
                0,
                null);
    }

    private List<Double> recordPaneValues() {
        final List<Double> values = new CopyOnWriteArrayList<>();
        interact(() -> pane.vvalueProperty().addListener((observed, was, now) -> values.add(now.doubleValue())));
        return values;
    }

    // IF a notch jumped instead of gliding, THEN a long list would flicker from place to place on every turn of the
    // wheel; it must arrive in several rising steps at exactly where the jump would have put it.
    @Test
    void wheelNotch_overAScrollPane_glidesInRisingStepsToTheJumpDistance() {
        assertThat(ThemeTestSupport.onFx(() -> pane.getViewportBounds().getHeight()))
                .isEqualTo(VIEW_HEIGHT);
        final List<Double> values = recordPaneValues();

        interact(() -> Event.fireEvent(content, wheel(NOTCH, false)));
        sleep(SETTLE_MS);

        assertThat(values).hasSizeGreaterThan(2).isSorted();
        assertThat(values.getFirst()).isLessThan(0.05);
        assertThat(ThemeTestSupport.onFx(pane::getVvalue)).isCloseTo(0.05, within(EXACT));
    }

    // IF a second notch cut the first short, THEN a fast spin would travel less than the notches add up to.
    @Test
    void twoWheelNotches_inQuickSuccession_travelBothDistances() {
        interact(() -> {
            Event.fireEvent(content, wheel(NOTCH, false));
            Event.fireEvent(content, wheel(NOTCH, false));
        });
        sleep(SETTLE_MS);

        assertThat(ThemeTestSupport.onFx(pane::getVvalue)).isCloseTo(0.1, within(EXACT));
    }

    // IF trackpad momentum were glided too, THEN it would be smoothed twice and lag behind the fingers.
    @Test
    void inertiaEvent_overAScrollPane_isLeftToJavaFxAndJumpsAtOnce() {
        interact(() -> Event.fireEvent(content, wheel(NOTCH, true)));

        assertThat(ThemeTestSupport.onFx(pane::getVvalue)).isCloseTo(0.05, within(EXACT));
    }

    // IF a press did not stop the glide, THEN dragging the scroll bar would fight the animation.
    @Test
    void mousePress_duringAGlide_stopsItWhereItIs() {
        interact(() -> {
            Event.fireEvent(content, wheel(NOTCH, false));
            Event.fireEvent(content, press());
        });
        sleep(SETTLE_MS);

        assertThat(ThemeTestSupport.onFx(pane::getVvalue)).isZero();
    }

    // IF a list were not glided, THEN the long lists — the glossary, the review list, the log — would still jump.
    @Test
    void wheelNotch_overAList_glidesItsRowsByTheJumpDistance() {
        final VirtualFlow<?> flow = (VirtualFlow<?>) list.lookup(".virtual-flow");
        final Node cell = list.lookup(".list-cell");
        final List<Double> positions = new CopyOnWriteArrayList<>();
        interact(() -> flow.positionProperty().addListener((observed, was, now) -> positions.add(now.doubleValue())));

        interact(() -> Event.fireEvent(cell, wheel(NOTCH, false)));
        sleep(SETTLE_MS);

        assertThat(positions).hasSizeGreaterThan(2).isSorted();
        assertThat(ThemeTestSupport.onFx(() -> flow.getFirstVisibleCell().getIndex()))
                .isEqualTo(2);
    }

    private static MouseEvent press() {
        return new MouseEvent(
                MouseEvent.MOUSE_PRESSED,
                10,
                10,
                10,
                10,
                MouseButton.PRIMARY,
                1,
                false,
                false,
                false,
                false,
                true,
                false,
                false,
                false,
                false,
                false,
                null);
    }
}
