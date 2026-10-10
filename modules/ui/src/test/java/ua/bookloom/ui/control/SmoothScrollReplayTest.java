package ua.bookloom.ui.control;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.IntStream;
import javafx.event.Event;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.ScrollPane;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import ua.bookloom.ui.FxTestBase;
import ua.bookloom.ui.ThemeTestSupport;

/**
 * Replays an invented trackpad gesture shaped like the owner's recorded one (bursts of 1 to 5 px with a sideways pixel
 * on about half the events, a momentum tail, gaps of 1 to 10 ms, a pause, a second gesture) into a real scroll pane
 * under the real filter, with the animation pulses driven by hand at 60 Hz, and measures what the pane did per frame.
 */
@SuppressWarnings("NullAway.Init")
class SmoothScrollReplayTest extends FxTestBase {

    private static final double VIEW_HEIGHT = 200;
    private static final double CONTENT_HEIGHT = 6000;
    private static final double INNER_VIEW = 100;
    private static final double INNER_CONTENT = 400;
    private static final long PULSE_NANOS = 16_666_667L;
    private static final double NANOS_PER_MILLI = 1e6;
    private static final int TRAILING_PULSES = 30;
    private static final double NEAR_END_PIXELS = 2;
    private static final double SMALL_EVENT = 3;
    private static final double WIDE_CONTENT = 2000;
    private static final double EXACT = 1e-6;
    /** The busiest frame may move this many times what an average moving frame moves; the input itself is within it. */
    private static final double MAX_STEP_RATIO = 2.5;

    private static final double[] DELTAS = {-3, -4, -2, -5, -3, -1, -3, -4};
    private static final int[] GAPS_MS = {2, 7, 1, 10, 4, 3, 9, 5};
    private static final int FIRST_GESTURE = 80;
    private static final int SECOND_GESTURE = 50;
    private static final int MOMENTUM_FROM = 55;
    private static final int PAUSE_MS = 400;

    private ScrollPane pane;
    private Region content;
    private HBox root;
    private ScrollPane inner;
    private ScrollPane outer;
    private Region innerContent;

    private record Input(long millis, double deltaX, double deltaY, boolean inertia) {}

    /** What the pane did: pixels moved per pulse, events that reached the pane unconsumed, events fed. */
    private record Outcome(List<Double> steps, List<Boolean> arrivedInFrame, int sidewaysReaching, double fed) {

        double moved() {
            return steps.stream().mapToDouble(Double::doubleValue).sum();
        }

        List<Double> movingSteps() {
            return steps.stream().filter(step -> step > 0).toList();
        }

        double stepRatio() {
            final List<Double> moving = movingSteps();
            return moving.stream().mapToDouble(Double::doubleValue).max().orElse(0)
                    / moving.stream().mapToDouble(Double::doubleValue).average().orElse(1);
        }
    }

    @Override
    public void start(final Stage stage) {
        content = new Region();
        content.setMinHeight(CONTENT_HEIGHT);
        pane = new ScrollPane(content);
        pane.setPadding(Insets.EMPTY);
        pane.setFitToWidth(true);
        pane.setPrefSize(150, VIEW_HEIGHT);
        innerContent = new Region();
        innerContent.setMinHeight(INNER_CONTENT);
        inner = new ScrollPane(innerContent);
        inner.setPadding(Insets.EMPTY);
        inner.setFitToWidth(true);
        inner.setPrefSize(150, INNER_VIEW);
        final Region below = new Region();
        below.setMinHeight(CONTENT_HEIGHT);
        outer = new ScrollPane(new VBox(inner, below));
        outer.setPadding(Insets.EMPTY);
        outer.setFitToWidth(true);
        outer.setPrefSize(150, VIEW_HEIGHT);
        root = new HBox(pane, outer);
        stage.setScene(new Scene(root, 300, VIEW_HEIGHT));
        stage.show();
    }

    private static List<Input> gesture(final long startMillis, final int count) {
        final List<Input> inputs = new ArrayList<>();
        long at = startMillis;
        for (int i = 0; i < count; i++) {
            at += GAPS_MS[i % GAPS_MS.length];
            inputs.add(new Input(at, i % 2 == 0 ? -1.0 : 0, DELTAS[i % DELTAS.length], i >= MOMENTUM_FROM));
        }
        return inputs;
    }

    private static List<Input> recording() {
        final List<Input> inputs = new ArrayList<>(gesture(0, FIRST_GESTURE));
        final long end = inputs.get(inputs.size() - 1).millis();
        inputs.addAll(gesture(end + PAUSE_MS, SECOND_GESTURE));
        return inputs;
    }

    private static ScrollEvent event(final Input input) {
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
                input.inertia(),
                input.deltaX(),
                input.deltaY(),
                input.deltaX(),
                input.deltaY(),
                ScrollEvent.HorizontalTextScrollUnits.NONE,
                0,
                ScrollEvent.VerticalTextScrollUnits.NONE,
                0,
                0,
                null);
    }

    private double position() {
        final double overflow =
                content.getLayoutBounds().getHeight() - pane.getViewportBounds().getHeight();
        return pane.getVvalue() * overflow;
    }

    private Outcome replay(final PixelMode mode) {
        final ManualClock clock = new ManualClock();
        final List<ScrollEvent> reaching = new CopyOnWriteArrayList<>();
        return ThemeTestSupport.onFx(() -> {
            SmoothScroll.install(root, clock, mode, new ScrollStats(System::nanoTime, false));
            content.addEventHandler(ScrollEvent.SCROLL, reaching::add);
            return run(clock, recording(), reaching);
        });
    }

    private static int sidewaysIn(final List<ScrollEvent> events) {
        return (int) events.stream().filter(e -> e.getDeltaX() != 0).count();
    }

    private Outcome run(final ManualClock clock, final List<Input> inputs, final List<ScrollEvent> reaching) {
        final List<Double> steps = new ArrayList<>();
        final List<Boolean> arrived = new ArrayList<>();
        final List<Long> arrivals = new ArrayList<>();
        final long[] nextPulse = {PULSE_NANOS};
        final double[] last = {position()};
        final Runnable pulse = () -> {
            clock.tick(nextPulse[0]);
            steps.add(Math.abs(position() - last[0]));
            last[0] = position();
            final long from = nextPulse[0] - PULSE_NANOS;
            arrived.add(arrivals.stream().anyMatch(at -> at > from));
            nextPulse[0] += PULSE_NANOS;
        };
        double fed = 0;
        for (final Input input : inputs) {
            final long at = (long) (input.millis() * NANOS_PER_MILLI);
            while (nextPulse[0] <= at) {
                pulse.run();
            }
            arrivals.add(at);
            fed += Math.abs(input.deltaY());
            Event.fireEvent(content, event(input));
        }
        for (int i = 0; i < TRAILING_PULSES; i++) {
            pulse.run();
        }
        return new Outcome(steps, arrived, sidewaysIn(reaching), fed);
    }

    @Test
    void replay_batch_movesExactlyTheFedPixels() {
        final Outcome outcome = replay(PixelMode.BATCH);

        assertThat(outcome.moved()).isCloseTo(outcome.fed(), within(EXACT));
    }

    @Test
    void replay_batch_neverStandsStillWhileInputArrives() {
        final Outcome outcome = replay(PixelMode.BATCH);

        final long stalls = java.util.stream.IntStream.range(0, outcome.steps().size())
                .filter(i ->
                        outcome.steps().get(i) == 0 && outcome.arrivedInFrame().get(i))
                .count();
        assertThat(stalls).isZero();
    }

    @Test
    void replay_batch_framesStayEven() {
        final Outcome outcome = replay(PixelMode.BATCH);

        System.out.println("REPLAY batch ratio=" + outcome.stepRatio() + " moved=" + outcome.moved() + " fed="
                + outcome.fed() + " steps=" + outcome.steps());
        assertThat(outcome.stepRatio()).isLessThan(MAX_STEP_RATIO);
    }

    @Test
    void replay_batch_sidewaysNoiseIsNotAppliedByJavaFxToo() {
        final Outcome outcome = replay(PixelMode.BATCH);

        assertThat(outcome.sidewaysReaching()).isZero();
    }

    @Test
    void replay_native_measuredForComparison() {
        final Outcome outcome = replay(PixelMode.NATIVE);

        System.out.println("REPLAY native ratio=" + outcome.stepRatio() + " moved=" + outcome.moved() + " fed="
                + outcome.fed() + " steps=" + outcome.steps());
        assertThat(outcome.steps()).isNotEmpty();
    }

    // IF the pending sum were ignored when looking for room, THEN a pane a pixel or two from its end would take every
    // event of the frame and clamp them away; once its pending pixels reach its end the rest goes to the pane around.
    @Test
    void pixelEvents_pastTheInnerPanesPendingEnd_goToTheOuterPane() {
        final ManualClock clock = new ManualClock();
        final double[] positions = ThemeTestSupport.onFx(() -> {
            SmoothScroll.install(root, clock, PixelMode.BATCH, new ScrollStats(System::nanoTime, false));
            inner.setVvalue(1 - NEAR_END_PIXELS / (INNER_CONTENT - INNER_VIEW));
            IntStream.range(0, 4)
                    .forEach(i -> Event.fireEvent(innerContent, event(new Input(0, 0, -SMALL_EVENT, false))));
            clock.tick(PULSE_NANOS);
            return new double[] {inner.getVvalue(), outer.getVvalue()};
        });

        assertThat(positions[0]).isEqualTo(1.0);
        assertThat(positions[1]).isPositive();
    }

    // The reason pixel events are not left to JavaFX: the sideways pixel macOS adds to a vertical swipe would pan a
    // wide
    // view (a table with a horizontal bar) sideways, event by event. Batched, only the vertical part is ever applied.
    @ParameterizedTest
    @EnumSource(PixelMode.class)
    void verticalSwipe_withSidewaysNoise_overAWideView_panesSideways_onlyWhenLeftToJavaFx(final PixelMode mode) {
        final ManualClock clock = new ManualClock();
        final double[] positions = ThemeTestSupport.onFx(() -> {
            pane.setFitToWidth(false);
            content.setMinWidth(WIDE_CONTENT);
            root.applyCss();
            root.layout();
            SmoothScroll.install(root, clock, mode, new ScrollStats(System::nanoTime, false));
            run(clock, gesture(0, FIRST_GESTURE), new CopyOnWriteArrayList<>());
            return new double[] {pane.getHvalue(), pane.getVvalue()};
        });

        System.out.println("REPLAY " + mode + " hvalue=" + positions[0] + " vvalue=" + positions[1]);
        assertThat(positions[1]).isPositive();
        assertThat(positions[0] == 0).isEqualTo(mode == PixelMode.BATCH);
    }
}
