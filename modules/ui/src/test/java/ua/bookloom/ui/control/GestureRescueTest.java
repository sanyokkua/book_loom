package ua.bookloom.ui.control;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.IntStream;
import javafx.event.Event;
import javafx.event.EventType;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.skin.ListViewSkin;
import javafx.scene.control.skin.VirtualFlow;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import ua.bookloom.ui.FxTestBase;
import ua.bookloom.ui.ThemeTestSupport;

/**
 * A trackpad gesture goes on when the node it started on leaves the scene: JavaFX keeps firing the gesture's events,
 * momentum included, at that node, as {@code Scene.processGestureEvent} does, and they are replayed here the same way —
 * at the detached node, with the animation pulses driven by hand. Also: what a flush asks of a list beyond its end goes
 * to the pane around it, and a list that moved zero is passed over that way until it moves.
 */
@SuppressWarnings("NullAway.Init")
class GestureRescueTest extends FxTestBase {

    private static final double VIEW = 200;
    private static final double INNER_VIEW = 100;
    private static final double TALL = 2000;
    private static final double CELL = 20;
    private static final int ROWS = 50;
    private static final double STEP = 10;
    private static final int BEFORE_DETACH = 3;
    private static final int AFTER_DETACH = 5;
    private static final long PULSE_NANOS = 16_666_667L;
    private static final double EXACT = 1e-6;
    private static final double HALF_PIXEL = 0.5;
    private static final double FROM_END = 5;
    private static final double MIDDLE = 0.5;

    private ScrollPane outer;
    private VBox outerBox;
    private ScrollPane inner;
    private StackPane holder;
    private Label target;
    private ListView<String> list;
    private StuckFlow stuckFlow;
    private ListView<String> stuckList;
    private ScrollPane listOuter;
    private ScrollPane stuckOuter;
    private HBox root;
    private ManualClock clock;
    private ScrollStats stats;

    /** A flow whose position says it has room downward while it moves zero, as an estimated length can. */
    private static final class StuckFlow extends VirtualFlow<ListCell<String>> {
        private final List<Double> asked = new CopyOnWriteArrayList<>();

        @Override
        public double scrollPixels(final double delta) {
            asked.add(delta);
            return delta > 0 ? 0 : super.scrollPixels(delta);
        }
    }

    @Override
    public void start(final Stage stage) {
        target = new Label("row");
        holder = new StackPane(target);
        inner = pane(new VBox(holder, tall()), INNER_VIEW);
        outerBox = new VBox(inner, tall());
        outer = pane(outerBox, VIEW);
        list = rows();
        listOuter = pane(new VBox(list, tall()), VIEW);
        stuckList = rows();
        stuckList.setSkin(new ListViewSkin<>(stuckList) {
            @Override
            protected VirtualFlow<ListCell<String>> createVirtualFlow() {
                stuckFlow = new StuckFlow();
                return stuckFlow;
            }
        });
        stuckOuter = pane(new VBox(stuckList, tall()), VIEW);
        root = new HBox(outer, listOuter, stuckOuter);
        stage.setScene(new Scene(root, 450, VIEW));
        stage.show();
        clock = new ManualClock();
        stats = new ScrollStats(() -> 1L, false);
        SmoothScroll.install(root, clock, PixelMode.BATCH, stats);
    }

    private static Region tall() {
        final Region region = new Region();
        region.setMinHeight(TALL);
        return region;
    }

    private static ScrollPane pane(final Node content, final double height) {
        final ScrollPane pane = new ScrollPane(content);
        pane.setPadding(Insets.EMPTY);
        pane.setFitToWidth(true);
        pane.setPrefSize(150, height);
        pane.setMinHeight(height);
        pane.setMaxHeight(height);
        return pane;
    }

    private static ListView<String> rows() {
        final ListView<String> rows = new ListView<>();
        rows.getItems()
                .setAll(IntStream.range(0, ROWS).mapToObj(i -> "row " + i).toList());
        rows.setFixedCellSize(CELL);
        rows.setPrefSize(150, INNER_VIEW);
        rows.setMinHeight(INNER_VIEW);
        rows.setMaxHeight(INNER_VIEW);
        return rows;
    }

    private static ScrollEvent event(final EventType<ScrollEvent> type, final double deltaY, final boolean inertia) {
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
                ScrollEvent.VerticalTextScrollUnits.NONE,
                0,
                inertia ? 0 : 2,
                null);
    }

    // Down the content is a negative delta, as a trackpad sends it.
    private static void swipe(final Node at, final int count, final boolean inertia) {
        IntStream.range(0, count).forEach(i -> Event.fireEvent(at, event(ScrollEvent.SCROLL, -STEP, inertia)));
    }

    private static double pixels(final ScrollPane pane) {
        final double overflow = pane.getContent().getLayoutBounds().getHeight()
                - pane.getViewportBounds().getHeight();
        return pane.getVvalue() * overflow;
    }

    private static VirtualFlow<?> flowOf(final ListView<?> view) {
        return (VirtualFlow<?>) view.lookup(".virtual-flow");
    }

    private void startGestureAndDetach(final Runnable detach) {
        Event.fireEvent(target, event(ScrollEvent.SCROLL_STARTED, 0, false));
        swipe(target, BEFORE_DETACH, false);
        clock.tick(PULSE_NANOS);
        detach.run();
        swipe(target, AFTER_DETACH, true);
        clock.tick(2 * PULSE_NANOS);
        clock.tick(3 * PULSE_NANOS);
    }

    // IF the rest of a gesture kept going to its replaced node, THEN the momentum would die under the finger: once the
    // node is gone its events move the scroll pane it sat in.
    @Test
    void gesture_targetReplacedMidGesture_restMovesTheNearestScrollerStillInTheScene() {
        final double[] result = ThemeTestSupport.onFx(() -> {
            startGestureAndDetach(() -> holder.getChildren().setAll(new Label("rebuilt row")));
            return new double[] {pixels(inner), pixels(outer), stats.detachedInWindow()};
        });

        assertThat(result[0]).isCloseTo((BEFORE_DETACH + AFTER_DETACH) * STEP, within(EXACT));
        assertThat(result[1]).isZero();
        assertThat(result[2]).isEqualTo(AFTER_DETACH);
    }

    // IF the rescue went to the gesture's first scroller whatever became of it, THEN a whole replaced panel would take
    // the momentum with it: a scroller that left the scene too is passed over for the one around it.
    @Test
    void gesture_targetsScrollerReplacedToo_restMovesTheOuterPane() {
        final double[] result = ThemeTestSupport.onFx(() -> {
            startGestureAndDetach(() -> outerBox.getChildren().set(0, new Region()));
            return new double[] {pixels(outer), stats.detachedInWindow()};
        });

        assertThat(result[0]).isCloseTo(AFTER_DETACH * STEP, within(EXACT));
        assertThat(result[1]).isEqualTo(AFTER_DETACH);
    }

    // IF a target still in the scene were handed on as well, THEN every gesture would be counted and fired twice.
    @Test
    void gesture_targetStaysInTheScene_isNotRescued() {
        final double[] result = ThemeTestSupport.onFx(() -> {
            startGestureAndDetach(() -> {});
            return new double[] {pixels(inner), stats.detachedInWindow()};
        });

        assertThat(result[0]).isCloseTo((BEFORE_DETACH + AFTER_DETACH) * STEP, within(EXACT));
        assertThat(result[1]).isZero();
    }

    // IF what a list could not take of its flush were dropped, THEN the page would stall for the frame the list hits
    // its end in: the rest of the flush moves the pane around the list.
    @Test
    void flush_pastTheListsEnd_restMovesTheOuterPane() {
        final double[] result = ThemeTestSupport.onFx(() -> {
            root.applyCss();
            root.layout();
            final VirtualFlow<?> flow = flowOf(list);
            flow.setPosition(1);
            root.layout();
            flow.scrollPixels(-FROM_END);
            swipe(list.lookup(".list-cell"), 2, false);
            clock.tick(PULSE_NANOS);
            return new double[] {flow.getPosition(), pixels(listOuter)};
        });

        assertThat(result[0]).isEqualTo(1.0);
        assertThat(result[1]).isCloseTo(2 * STEP - FROM_END, within(HALF_PIXEL));
    }

    // IF a list whose position promises room but which moved zero kept taking the gesture, THEN each frame would be
    // asked of it and handed on a frame late; it is passed over that way until it moves, and asked again after a move
    // the other way.
    @Test
    void flowThatMovedZero_isOutOfRoomThatWay_untilAMoveTheOtherWay() {
        final List<Double> asked = ThemeTestSupport.onFx(() -> {
            root.applyCss();
            root.layout();
            flowOf(stuckList).setPosition(MIDDLE);
            root.layout();
            final Node cell = stuckList.lookup(".list-cell");
            stuckFlow.asked.clear();
            final long[] at = {PULSE_NANOS};
            final Runnable pulse = () -> clock.tick(at[0] += PULSE_NANOS);
            swipe(cell, 1, false);
            pulse.run();
            swipe(cell, 1, false);
            pulse.run();
            Event.fireEvent(cell, event(ScrollEvent.SCROLL, STEP, false));
            pulse.run();
            swipe(cell, 1, false);
            pulse.run();
            return List.copyOf(stuckFlow.asked);
        });

        assertThat(asked).containsExactly(STEP, -STEP, STEP);
        assertThat(ThemeTestSupport.onFx(() -> pixels(stuckOuter))).isCloseTo(3 * STEP, within(EXACT));
    }
}
