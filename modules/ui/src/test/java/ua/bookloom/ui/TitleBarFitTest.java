package ua.bookloom.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Label;
import javafx.scene.control.Labeled;
import javafx.scene.paint.Color;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.testfx.util.WaitForAsyncUtils;
import ua.bookloom.api.pipeline.QualityDial;
import ua.bookloom.api.pipeline.ReviewMode;
import ua.bookloom.ui.state.ActivityKind;
import ua.bookloom.ui.state.ActivityTracker;
import ua.bookloom.ui.state.RunMode;
import ua.bookloom.ui.state.RunState;
import ua.bookloom.ui.state.StateMirror;
import ua.bookloom.ui.state.Throughput;
import ua.bookloom.ui.theme.ThemeMode;

/**
 * The title bar at the smallest window with everything in it at once — a long book name, a model scan with Stop, the
 * run's state, mode, times, rate and connection, the theme toggle and About: nothing is cut or overlaps; the book name
 * gives way first, then the least important run parts are hidden whole, rate first, and a wide window shows them all.
 */
class TitleBarFitTest extends ShellTestBase {

    private static final double PART_TOLERANCE = 0.5;
    private static final double TEXT_RATIO = 4.5;
    private static final double WIDE = 1900;
    private static final List<String> DROP_ORDER =
            List.of("shell-run-rate", "shell-run-mode", "shell-run-left", "shell-run-elapsed");

    private Bounds sceneBounds(final Node node) {
        return node.localToScene(node.getLayoutBounds());
    }

    private void fillTheTitleBar(final String language) {
        useLocale(Locale.forLanguageTag(language));
        final StateMirror mirror = injector.getInstance(StateMirror.class);
        onFx(() -> {
            mirror.publishRunStarted(
                    "The Strange Case of Doctor Jekyll and Mister Hyde, the complete annotated edition.epub",
                    new RunMode(QualityDial.BALANCED, ReviewMode.ASSISTED));
            mirror.publishProgress(ProgressFixtures.progress(7, 11, 78, 0, 22));
            mirror.live()
                    .publishThroughput(
                            new Throughput(31.0, false, Duration.ofMinutes(80), Duration.ofMinutes(62), 28.0));
            mirror.publishRunState(RunState.RUNNING);
            injector.getInstance(ActivityTracker.class)
                    .begin(ActivityKind.GLOSSARY_SCAN, () -> {})
                    .requests(12);
        });
        WaitForAsyncUtils.waitForFxEvents();
    }

    // IF a part were cut, squeezed into another or pushed past the edge, THEN the person could not read or reach it.
    @ParameterizedTest
    @ValueSource(strings = {"en", "uk"})
    void titleBar_atTheSmallestWindowWithEverythingShown_cutsAndOverlapsNothing(final String language) {
        fillTheTitleBar(language);
        resizeScene(CONTENT_AT_MINIMUM_WIDTH, CONTENT_AT_MINIMUM_HEIGHT);

        final Bounds bar = sceneBounds(required("shell-title-bar"));
        final List<Node> parts = onFxList(() -> shownParts(required("shell-title-bar")));

        assertThat(sceneBounds(required("shell-about")).getMaxX()).isLessThanOrEqualTo(bar.getMaxX());
        assertThat(required("shell-activity-text").isVisible()).isTrue();
        assertThat(required("shell-run-state").isVisible()).isTrue();
        assertThat(required("shell-run-connection").isVisible()).isTrue();
        assertThat(required("shell-run-control").isVisible()).isTrue();
        assertThat(cutParts(parts)).as("parts shown shorter than their words").isEmpty();
        assertThat(overlaps(parts)).as("parts drawn over each other").isEmpty();
    }

    // IF parts were hidden in no order, THEN the elapsed time could vanish while the rate stayed.
    @Test
    void titleBar_tooNarrowForEveryPart_hidesTheRateBeforeTheModeAndTheTimes() {
        fillTheTitleBar("uk");
        resizeScene(CONTENT_AT_MINIMUM_WIDTH, CONTENT_AT_MINIMUM_HEIGHT);

        final List<Boolean> shown =
                DROP_ORDER.stream().map(id -> required(id).isVisible()).toList();

        assertThat(shown)
                .as("a part shown only if every part after it in the order is")
                .isSortedAccordingTo(Boolean::compare);
        assertThat(shown.getFirst())
                .as("the narrowest window cannot hold the rate as well")
                .isFalse();
    }

    // IF a part hidden for a narrow window never came back, THEN widening the window would not restore it.
    @Test
    void titleBar_wideWindowAfterNarrow_showsEveryPartAgain() {
        fillTheTitleBar("uk");
        resizeScene(CONTENT_AT_MINIMUM_WIDTH, CONTENT_AT_MINIMUM_HEIGHT);
        resizeScene(WIDE, CONTENT_AT_MINIMUM_HEIGHT);

        assertThat(DROP_ORDER.stream().map(id -> required(id).isVisible()).toList())
                .containsOnly(true);
        final Node file = required("shell-run-file");
        assertThat(file.getLayoutBounds().getWidth())
                .as("a wide window shows the whole book name")
                .isGreaterThanOrEqualTo(file.prefWidth(-1) - PART_TOLERANCE);
    }

    // IF the chip's words kept the body text colour, THEN they would be dark on the dark title bar of the light theme.
    @ParameterizedTest
    @ValueSource(strings = {"LIGHT", "DARK"})
    void activityChip_inEachTheme_readsOnTheTitleBar(final String block) {
        fillTheTitleBar("en");
        onFx(() -> themeController.setMode(ThemeMode.valueOf(block)));
        final Label text = (Label) required("shell-activity-text");

        final double ratio =
                ThemeTestSupport.onFx(() -> Contrast.ratio((Color) text.getTextFill(), Contrast.surfaceBehind(text)));

        assertThat(ratio).isGreaterThanOrEqualTo(TEXT_RATIO);
    }

    private List<Node> onFxList(final Supplier<List<Node>> read) {
        return ThemeTestSupport.onFx(read);
    }

    /** The visible leaf parts of the bar: its labels and buttons, wherever they are nested. */
    private static List<Node> shownParts(final Node root) {
        final List<Node> parts = new ArrayList<>();
        collect(root, parts);
        return parts;
    }

    private static void collect(final Node node, final List<Node> into) {
        if (!node.isVisible()) {
            return;
        }
        if (node instanceof Labeled) {
            into.add(node);
            return;
        }
        if (node instanceof Parent parent) {
            parent.getChildrenUnmodifiable().forEach(child -> collect(child, into));
        }
    }

    // The book name is the one part meant to give way (it ends in an ellipsis), so only the others are held whole.
    private static List<String> cutParts(final List<Node> parts) {
        return parts.stream()
                .filter(part -> !"shell-run-file".equals(part.getId()))
                .filter(part -> part.getLayoutBounds().getWidth() + PART_TOLERANCE < part.prefWidth(-1))
                .map(part -> part.getId() + " " + ((Labeled) part).getText())
                .toList();
    }

    private List<String> overlaps(final List<Node> parts) {
        final List<String> found = new ArrayList<>();
        for (int i = 1; i < parts.size(); i++) {
            final Bounds before = sceneBounds(parts.get(i - 1));
            final Bounds after = sceneBounds(parts.get(i));
            if (after.getMinX() + PART_TOLERANCE < before.getMaxX()) {
                found.add(parts.get(i - 1).getId() + " / " + parts.get(i).getId());
            }
        }
        return found;
    }
}
