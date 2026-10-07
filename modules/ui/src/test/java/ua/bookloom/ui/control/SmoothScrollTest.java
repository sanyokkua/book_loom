package ua.bookloom.ui.control;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.DoubleSupplier;
import java.util.stream.IntStream;
import javafx.beans.value.ObservableDoubleValue;
import javafx.event.Event;
import javafx.event.EventType;
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
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import ua.bookloom.ui.FxTestBase;
import ua.bookloom.ui.ThemeTestSupport;

/**
 * A wheel notch glides the pane or list under the pointer exactly as far as JavaFX would have jumped it, in several
 * eased frames; a trackpad's own events still jump at once, and a mouse press stops a glide. The wheel events are the
 * ones a real wheel sends: {@code LINES} units, three lines and forty pixels a notch, no fingers.
 */
@SuppressWarnings("NullAway.Init")
class SmoothScrollTest extends FxTestBase {

    private static final double VIEW_HEIGHT = 200;
    private static final double CONTENT_HEIGHT = 1000;
    private static final double NOTCH_PIXELS = -40;
    private static final double NOTCH_LINES = -3;
    private static final double CELL = 20;
    private static final int ROWS = 200;
    private static final int NOTCHES = 10;
    private static final long SETTLE_MS = 1200;
    private static final long SPIN_MS = 40;
    private static final double EXACT = 1e-6;
    private static final long AWAIT_NANOS = 15_000_000_000L;

    private ScrollPane pane;
    private Region content;
    private ListView<String> list;
    private ListView<String> nativeList;
    private ScrollPane outer;
    private ListView<String> inner;
    private Stage stage;

    @Override
    public void start(final Stage stage) {
        this.stage = stage;
        content = new Region();
        content.setPrefSize(100, CONTENT_HEIGHT);
        content.setMinHeight(CONTENT_HEIGHT);
        pane = new ScrollPane(content);
        pane.setPadding(Insets.EMPTY);
        pane.setFitToWidth(true);
        pane.setPrefSize(150, VIEW_HEIGHT);
        list = rows();
        nativeList = rows();
        inner = rows();
        inner.setPrefHeight(VIEW_HEIGHT / 2);
        final Region below = new Region();
        below.setMinHeight(CONTENT_HEIGHT);
        outer = new ScrollPane(new VBox(inner, below));
        outer.setPadding(Insets.EMPTY);
        outer.setFitToWidth(true);
        outer.setPrefSize(150, VIEW_HEIGHT);
        final HBox glided = SmoothScroll.install(new HBox(pane, list, outer));
        stage.setScene(new Scene(new HBox(glided, nativeList), 600, VIEW_HEIGHT));
        stage.show();
    }

    @AfterEach
    void clearSwitch() {
        System.clearProperty(SmoothScroll.SWITCH_PROPERTY);
    }

    private static ListView<String> rows() {
        final ListView<String> rows = new ListView<>();
        rows.getItems()
                .setAll(IntStream.range(0, ROWS).mapToObj(i -> "row " + i).toList());
        rows.setFixedCellSize(CELL);
        rows.setPrefSize(150, VIEW_HEIGHT);
        return rows;
    }

    private static ScrollEvent scroll(
            final EventType<ScrollEvent> type,
            final double deltaY,
            final ScrollEvent.VerticalTextScrollUnits units,
            final double textDeltaY,
            final int touchCount,
            final boolean inertia) {
        return new ScrollEvent(
                type,
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
                units,
                textDeltaY,
                touchCount,
                null);
    }

    private static ScrollEvent wheel(final double sign) {
        return scroll(
                ScrollEvent.SCROLL,
                sign * NOTCH_PIXELS,
                ScrollEvent.VerticalTextScrollUnits.LINES,
                sign * NOTCH_LINES,
                0,
                false);
    }

    private static ScrollEvent trackpad(final EventType<ScrollEvent> type, final boolean inertia) {
        return scroll(type, -10, ScrollEvent.VerticalTextScrollUnits.NONE, 0, inertia ? 0 : 2, inertia);
    }

    private static VirtualFlow<?> flowOf(final ListView<?> view) {
        return (VirtualFlow<?>) view.lookup(".virtual-flow");
    }

    private static Node cellOf(final ListView<?> view) {
        return view.lookup(".list-cell");
    }

    private List<Double> record(final ObservableDoubleValue value) {
        final List<Double> values = new CopyOnWriteArrayList<>();
        interact(() -> value.addListener((observed, was, now) -> values.add(now.doubleValue())));
        return values;
    }

    // Waits until the glide has landed instead of guessing how long a loaded machine needs; fails by the assertion
    // after.
    private void awaitValue(final DoubleSupplier value, final double target) {
        final long deadline = System.nanoTime() + AWAIT_NANOS;
        while (System.nanoTime() < deadline && Math.abs(ThemeTestSupport.onFx(value::getAsDouble) - target) > EXACT) {
            sleep(SPIN_MS);
        }
    }

    // The events that get past the glide's filter to the node itself, i.e. the ones that were not consumed.
    private List<ScrollEvent> reaching(final Node node) {
        final List<ScrollEvent> reached = new CopyOnWriteArrayList<>();
        interact(() -> node.addEventHandler(ScrollEvent.SCROLL, reached::add));
        return reached;
    }

    private static ScrollEvent pixels(final double deltaY, final boolean inertia) {
        return scroll(ScrollEvent.SCROLL, deltaY, ScrollEvent.VerticalTextScrollUnits.NONE, -0.0, 0, inertia);
    }

    // A hand spins about one notch every 40 ms; firing all ten in one go would also prove the run-ahead cap.
    private void fireNotches(final Node target, final double sign) {
        IntStream.range(0, NOTCHES).forEach(i -> {
            interactAtTestFxPace(() -> Event.fireEvent(target, wheel(sign)));
            sleep(SPIN_MS);
        });
    }

    // IF a list glided by the event's pixels instead of by its own lines, THEN wheel scrolling would crawl compared
    // with the same list without the glide; ten notches must end exactly where JavaFX's own ten jumps end, rising all
    // the way.
    @Test
    void tenNotches_overAList_riseAndEndExactlyWhereJavaFxJumpsEnd() {
        final VirtualFlow<?> flow = flowOf(list);
        final List<Double> positions = record(flow.positionProperty());

        fireNotches(flow, 1);
        fireNotches(flowOf(nativeList), 1);
        final double jumped = ThemeTestSupport.onFx(flowOf(nativeList)::getPosition);
        awaitValue(flow::getPosition, jumped);

        assertThat(positions).hasSizeGreaterThan(1).isSorted();
        assertThat(ThemeTestSupport.onFx(flow::getPosition)).isCloseTo(jumped, within(EXACT));
        // 30 rows of 20 px; a floating-point landing a hair under the row boundary made the first visible index 29.
        assertThat(ThemeTestSupport.onFx(flow::getPosition))
                .isCloseTo(600.0 / (ROWS * CELL - ThemeTestSupport.onFx(flow::getHeight)), within(EXACT));
    }

    // IF a notch jumped instead of gliding, THEN a long page would flicker from place to place on every turn of the
    // wheel; ten notches arrive in rising steps at exactly the pixels JavaFX would have jumped (400 of 800).
    @Test
    void tenNotches_overAScrollPane_riseToExactlyTheJumpDistance() {
        final List<Double> values = record(pane.vvalueProperty());

        fireNotches(content, 1);
        sleep(SETTLE_MS);

        assertThat(values).hasSizeGreaterThan(NOTCHES).isSorted();
        assertThat(ThemeTestSupport.onFx(pane::getVvalue)).isCloseTo(0.5, within(EXACT));
    }

    // IF the recorded macOS shape (pixel deltas, no units, some of them momentum) were left to JavaFX, THEN every event
    // would move the view on its own and scrolling would lurch; each is consumed and the pane ends exactly where the
    // deltas add up (-2 -3 -5 -10 -10 -5 -3 -2 = 40 px of 800 = 0.05).
    @Test
    void recordedMacPixelEvents_overAScrollPane_areConsumedAndSumExactly() {
        final List<Double> deltas = List.of(-2.0, -3.0, -5.0, -10.0, -10.0, -5.0, -3.0, -2.0);
        final List<ScrollEvent> events =
                deltas.stream().map(d -> pixels(d, d > -5.0)).toList();

        final List<ScrollEvent> reachedTarget = reaching(content);
        interact(() -> events.forEach(event -> Event.fireEvent(content, event)));
        awaitValue(pane::getVvalue, 0.05);

        assertThat(reachedTarget).isEmpty();
        assertThat(ThemeTestSupport.onFx(pane::getVvalue)).isCloseTo(0.05, within(EXACT));
    }

    // IF a list took pixel events as lines (textDeltaY times the line size), THEN with textDeltaY 0 it would never
    // move;
    // 20 px of deltas move a list by exactly 20 px (one 20 px row of the 4000 px content less the viewport).
    @Test
    void pixelEvents_overAList_moveItByTheirPixelsNotByLines() {
        final VirtualFlow<?> flow = flowOf(list);

        interact(() -> IntStream.range(0, 4).forEach(i -> Event.fireEvent(flow, pixels(-5, false))));
        final double expected = 20.0 / (ROWS * CELL - ThemeTestSupport.onFx(flow::getHeight));
        awaitValue(flow::getPosition, expected);

        assertThat(ThemeTestSupport.onFx(flow::getPosition)).isCloseTo(expected, within(EXACT));
    }

    // IF a horizontal part were swallowed with the vertical one, THEN sideways panning would die on a diagonal swipe.
    @Test
    void pixelEvent_withAHorizontalPart_isLeftToJavaFx() {
        final ScrollEvent diagonal = new ScrollEvent(
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
                false,
                5,
                -5,
                5,
                -5,
                ScrollEvent.HorizontalTextScrollUnits.NONE,
                0,
                ScrollEvent.VerticalTextScrollUnits.NONE,
                0,
                0,
                null);

        final List<ScrollEvent> reachedTarget = reaching(content);
        interact(() -> Event.fireEvent(content, diagonal));

        assertThat(reachedTarget).hasSize(1);
    }

    // IF a gesture whose end never arrived left the wheel thinking a gesture was still under way (the old shared
    // state), THEN every later notch would jump; a notch after a lone gesture start still glides.
    @Test
    void wheelNotch_afterAGestureStartWithNoFinish_stillGlides() {
        interact(() -> Event.fireEvent(content, trackpad(ScrollEvent.SCROLL_STARTED, false)));
        final List<Double> values = record(pane.vvalueProperty());

        interact(() -> Event.fireEvent(content, wheel(1)));
        sleep(SETTLE_MS);

        assertThat(values).hasSizeGreaterThan(2).isSorted();
        assertThat(ThemeTestSupport.onFx(pane::getVvalue)).isCloseTo(0.05, within(EXACT));
    }

    // IF a list with room kept its notch from the page around it, or a list at its end swallowed it, THEN the page
    // could never be scrolled from over a list: the inner list moves first, the outer pane once the list is at its end.
    @Test
    void wheelNotch_overANestedList_movesTheListFirstAndThePaneAtTheListsEnd() {
        interact(() -> Event.fireEvent(cellOf(inner), wheel(1)));
        sleep(SETTLE_MS);
        final double listMoved = ThemeTestSupport.onFx(flowOf(inner)::getPosition);
        final double paneBefore = ThemeTestSupport.onFx(outer::getVvalue);
        interact(() -> flowOf(inner).setPosition(1));

        interact(() -> Event.fireEvent(cellOf(inner), wheel(1)));
        sleep(SETTLE_MS);

        assertThat(listMoved).isPositive();
        assertThat(paneBefore).isZero();
        assertThat(ThemeTestSupport.onFx(outer::getVvalue)).isPositive();
    }

    // IF the glide ran past the end and came back, THEN the view would bounce; notches towards a near end stop exactly
    // at it, never beyond.
    @Test
    void tenNotches_nearTheEnd_stopExactlyAtTheEndWithoutOvershoot() {
        interact(() -> pane.setVvalue(0.9));
        final List<Double> values = record(pane.vvalueProperty());

        fireNotches(content, 1);
        sleep(SETTLE_MS);

        assertThat(values).isSorted().allSatisfy(value -> assertThat(value).isLessThanOrEqualTo(1.0));
        assertThat(ThemeTestSupport.onFx(pane::getVvalue)).isEqualTo(1.0);
    }

    // IF a press did not stop the glide, THEN dragging the scroll bar would fight the animation.
    @Test
    void mousePress_duringAGlide_stopsItWhereItIs() {
        interact(() -> {
            Event.fireEvent(content, wheel(1));
            Event.fireEvent(content, press());
        });
        sleep(SETTLE_MS);

        assertThat(ThemeTestSupport.onFx(pane::getVvalue)).isZero();
    }

    // IF there were no way to turn the glide off, THEN a person on a system where it misbehaves would be stuck with it;
    // with the switch off a notch jumps at once, as JavaFX does.
    @Test
    void install_switchedOff_leavesTheWheelToJavaFx() {
        System.setProperty(SmoothScroll.SWITCH_PROPERTY, "false");
        final Region plain = new Region();
        plain.setMinHeight(CONTENT_HEIGHT);
        final ScrollPane unglided = new ScrollPane(plain);
        unglided.setPadding(Insets.EMPTY);
        unglided.setFitToWidth(true);
        unglided.setPrefSize(150, VIEW_HEIGHT);
        interact(() -> stage.getScene().setRoot(SmoothScroll.install(new HBox(unglided))));

        interact(() -> Event.fireEvent(plain, wheel(1)));

        assertThat(ThemeTestSupport.onFx(unglided::getVvalue)).isCloseTo(0.05, within(EXACT));
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
