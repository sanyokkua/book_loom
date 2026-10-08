package ua.bookloom.ui.control;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.concurrent.TimeUnit;
import javafx.scene.Scene;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.testfx.util.WaitForAsyncUtils;
import ua.bookloom.ui.FxTestBase;
import ua.bookloom.ui.ThemeTestSupport;

/** The transitions ease a node into the state it is set to, and reduced motion sets that state at once. */
@SuppressWarnings("NullAway.Init")
class MotionTest extends FxTestBase {

    private static final double TOLERANCE = 1e-6;
    // Longer than the slowest transition, with room for a loaded test machine.
    private static final long SETTLED_MS = 1500;

    private StackPane root;

    @Override
    public void start(final Stage stage) {
        root = new StackPane();
        stage.setScene(new Scene(root, 200, 200));
        stage.show();
    }

    // IF the explicit switch did not win over the system's preference, THEN a person could not turn motion back on,
    // and the test tasks could not turn it off; an unknown word falls through to the next source.
    @ParameterizedTest(name = "env={0} property={1} platform={2} -> {3}")
    @CsvSource(
            nullValues = "-",
            value = {
                "-, -, false, false",
                "-, -, true, true",
                "1, -, false, true",
                "TRUE, -, false, true",
                "0, -, true, false",
                "-, yes, false, true",
                "-, off, true, false",
                "0, true, false, false",
                "maybe, true, false, true",
                "maybe, -, true, true",
            })
    void decide_explicitSwitchThenPlatform_givesTheExpectedAnswer(
            final @Nullable String environment,
            final @Nullable String property,
            final boolean platform,
            final boolean expected) {
        assertThat(Motion.decide(environment, property, platform)).isEqualTo(expected);
    }

    // IF a fade did not start from transparent, THEN nothing would ease in; IF it did not end opaque, THEN the node
    // would stay faded.
    @Test
    void play_withMotion_startsTransparentAndEndsOpaque() {
        final Region node = place();

        final double first = ThemeTestSupport.onFx(() -> {
            Motion.play(node, Motion.STANDARD, 0, false);
            return node.getOpacity();
        });

        assertThat(first).isCloseTo(0, within(TOLERANCE));
        assertThat(settledOpacity(node)).isCloseTo(1, within(TOLERANCE));
    }

    // IF reduced motion still faded, THEN a person who asked for stillness would still see things move.
    @Test
    void play_reduced_isOpaqueAtOnce() {
        final Region node = place();

        final double opacity = ThemeTestSupport.onFx(() -> {
            node.setOpacity(0);
            Motion.play(node, Motion.STANDARD, 0, true);
            return node.getOpacity();
        });

        assertThat(opacity).isCloseTo(1, within(TOLERANCE));
    }

    // IF a card did not grow from slightly smaller, THEN a dialog would pop in with no sense of arriving.
    @Test
    void popIn_withMotion_growsToFullSize() {
        final Region card = place();

        final double startScale = ThemeTestSupport.onFx(() -> {
            Motion.popIn(card, false);
            return card.getScaleX();
        });
        WaitForAsyncUtils.sleep(SETTLED_MS, TimeUnit.MILLISECONDS);

        assertThat(startScale).isLessThan(1);
        assertThat(ThemeTestSupport.onFx(card::getScaleX)).isCloseTo(1, within(TOLERANCE));
        assertThat(ThemeTestSupport.onFx(card::getOpacity)).isCloseTo(1, within(TOLERANCE));
    }

    // IF a bar jumped to each new fraction, THEN a slow run's bar would twitch; it glides and arrives exactly.
    @Test
    void moveProgress_withMotion_glidesToTheTarget() {
        final ProgressBar bar = ThemeTestSupport.onFx(() -> {
            final ProgressBar made = new ProgressBar(0.2);
            root.getChildren().setAll(made);
            return made;
        });

        final double atOnce = ThemeTestSupport.onFx(() -> {
            Motion.moveProgress(bar, 0.6, false);
            return bar.getProgress();
        });
        WaitForAsyncUtils.sleep(SETTLED_MS, TimeUnit.MILLISECONDS);

        assertThat(atOnce).isLessThan(0.6);
        assertThat(ThemeTestSupport.onFx(bar::getProgress)).isCloseTo(0.6, within(TOLERANCE));
    }

    // IF an indeterminate value or a reset glided, THEN the bar would sweep backwards or animate a spinner's value.
    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({"0.5, -1", "0.5, 0.1", "-1, 0.4"})
    void moveProgress_resetOrIndeterminate_isShownAtOnce(final double from, final double to) {
        final ProgressBar bar = ThemeTestSupport.onFx(() -> {
            final ProgressBar made = new ProgressBar(from);
            root.getChildren().setAll(made);
            return made;
        });

        final double shown = ThemeTestSupport.onFx(() -> {
            Motion.moveProgress(bar, to, false);
            return bar.getProgress();
        });

        assertThat(shown).isCloseTo(to, within(TOLERANCE));
    }

    private Region place() {
        return ThemeTestSupport.onFx(() -> {
            final Region node = new Region();
            root.getChildren().setAll(node);
            return node;
        });
    }

    private static double settledOpacity(final Region node) {
        WaitForAsyncUtils.sleep(SETTLED_MS, TimeUnit.MILLISECONDS);
        return ThemeTestSupport.onFx(node::getOpacity);
    }
}
