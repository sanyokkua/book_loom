package ua.bookloom.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;
import javafx.geometry.Point2D;
import javafx.geometry.VerticalDirection;
import javafx.scene.Node;
import javafx.scene.control.ScrollPane;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.testfx.util.WaitForAsyncUtils;
import ua.bookloom.ui.ConformanceCases.Screen;
import ua.bookloom.ui.ScrollReach.Hidden;

/**
 * Every screen state, Settings tab and dialog the conformance catalogue lists, in English and Ukrainian, at the
 * narrowest and shortest windows: whatever lies below the bottom of what is shown is reached with the mouse wheel. A
 * control below the edge must sit in a scroll pane that draws its vertical bar, the robot's wheel turned over that pane
 * must move it, and once moved to the end no control is left below the edge — the last one is shown whole. The
 * headless robot's wheel arrives as pixel deltas, so it goes the trackpad's way through the shell's {@code SmoothScroll}
 * filter (summed per pulse); the notch glide is proved in {@code SmoothScrollTest}.
 */
class ScreenScrollTest extends ConformanceTestBase {

    private static final List<double[]> SIZES =
            List.of(new double[] {CONTENT_AT_MINIMUM_WIDTH, CONTENT_AT_MINIMUM_HEIGHT});
    // The other narrow sizes the window can have: `slow`, so only the gate runs them.
    private static final List<double[]> OTHER_SIZES = List.of(
            new double[] {CONTENT_AT_MINIMUM_WIDTH, 640},
            new double[] {960, CONTENT_AT_MINIMUM_HEIGHT},
            new double[] {960, 640});
    private static final int NOTCHES = 10;
    private static final int MOST_ROUNDS = 40;
    private static final int QUIET_READS = 3;
    private static final int MOST_READS = 120;
    private static final long READ_GAP_MILLIS = 16;

    static Stream<Arguments> screensAtTheSmallestWindow() {
        return FitMatrix.screenCases(SIZES);
    }

    static Stream<Arguments> screensAtTheOtherNarrowWindows() {
        return FitMatrix.screenCases(OTHER_SIZES);
    }

    // IF a card ran below the window with no scroll pane to move, THEN a person could never read or press its end.
    @ParameterizedTest(name = "{0} in {1} at {2}")
    @MethodSource("screensAtTheSmallestWindow")
    void screen_atTheSmallestWindow_reachesEveryControlWithTheWheel(
            final Screen screen, final String language, final double[] size) throws TimeoutException {
        assertReachable(screen, language, size);
    }

    // IF the wheel reached everything only at the smallest window, THEN a slightly taller or wider one could cut a
    // card.
    @Tag("slow")
    @ParameterizedTest(name = "{0} in {1} at {2}")
    @MethodSource("screensAtTheOtherNarrowWindows")
    void screen_atTheOtherNarrowWindows_reachesEveryControlWithTheWheel(
            final Screen screen, final String language, final double[] size) throws TimeoutException {
        assertReachable(screen, language, size);
    }

    private void assertReachable(final Screen screen, final String language, final double[] size)
            throws TimeoutException {
        show(screen, Locale.forLanguageTag(language));
        resizeScene(size[0], size[1]);
        // The headless platform delivers the robot's input at screen = scene coordinates wherever the window says it
        // is, so the window is put at the screen's origin for the two to agree.
        interact(() -> {
            scene.getWindow().setX(0);
            scene.getWindow().setY(0);
        });

        final List<String> stuck = wheelToTheEnd();
        final List<String> unreached =
                ThemeTestSupport.onFx(() -> ScrollReach.hiddenBelow(ScrollReach.scope(scene)).stream()
                        .map(Hidden::describe)
                        .toList());

        assertThat(stuck).as("scroll panes the wheel did not move").isEmpty();
        assertThat(unreached)
                .as("controls left below the edge with no scroll bar to reach them")
                .isEmpty();
    }

    // Turns the wheel over the pane that hides the first control below the edge until nothing is hidden, no pane can
    // move, or a pane does not move.
    private List<String> wheelToTheEnd() {
        final List<String> stuck = new ArrayList<>();
        for (int round = 0; round < MOST_ROUNDS && stuck.isEmpty(); round++) {
            final @Nullable ScrollPane pane = ThemeTestSupport.onFx(this::paneToMove);
            if (pane == null) {
                return stuck;
            }
            final double before = ThemeTestSupport.onFx(pane::getVvalue);
            final Point2D pointer = ThemeTestSupport.onFx(() -> ScrollReach.pointerOver(pane));
            moveTo(pointer);
            scroll(NOTCHES, VerticalDirection.DOWN);
            awaitStill(pane);
            if (ThemeTestSupport.onFx(pane::getVvalue) <= before) {
                stuck.add(ThemeTestSupport.onFx(() -> ScrollReach.diagnose(pane)) + " stayed at " + before);
            }
        }
        return stuck;
    }

    private @Nullable ScrollPane paneToMove() {
        final Node scope = ScrollReach.scope(scene);
        return ScrollReach.hiddenBelow(scope).stream()
                .map(Hidden::pane)
                .filter(pane -> pane != null)
                .findFirst()
                .orElse(null);
    }

    // The filter moves the pane over the next pulses; the pane is still once its value stops changing.
    private static void awaitStill(final ScrollPane pane) {
        double last = Double.NaN;
        int quiet = 0;
        for (int read = 0; read < MOST_READS && quiet < QUIET_READS; read++) {
            WaitForAsyncUtils.sleep(READ_GAP_MILLIS, TimeUnit.MILLISECONDS);
            final double now = ThemeTestSupport.onFx(pane::getVvalue);
            quiet = now == last ? quiet + 1 : 0;
            last = now;
        }
    }
}
