package ua.bookloom.ui.screen;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.ArrayList;
import java.util.List;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TitledPane;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.Region;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.testfx.util.WaitForAsyncUtils;
import ua.bookloom.api.pipeline.PromptSection;
import ua.bookloom.ui.LiveCallFixtures;
import ua.bookloom.ui.LiveRowFixtures;
import ua.bookloom.ui.ThemeTestSupport;
import ua.bookloom.ui.state.LiveRow;
import ua.bookloom.ui.state.LiveRow.Stage;

/**
 * The Detailed detail's "Prompt context" section measured in the real shell: an opened section is as tall as what
 * it shows, at its natural height up to 320 pixels and scrolling inside beyond that, and
 * closing it gives the height back. Earlier tests asserted only that the texts existed, which a section crushed to one
 * line also passes; these read the laid-out sizes, at the content area a 960 by 640 window leaves and at 1400 by 900.
 */
class ContextSectionLayoutTest extends TranslatingScreenTestBase {

    private static final double BODY_MAX = 320;
    private static final double SLACK = 1.0;
    private static final String TEXT = "She had lost her mother, and the poor girl wept as she followed the coffin. ";
    private static final String LONG = "Коли я приземлився на верхівку ліхтаря, місто вже спало, і тільки дощ, що лив"
            + " стіною, нагадував мені, чому я взагалі погодився на цю роботу для молодого чарівника. ";

    private void sizeTo(final double width, final double height) {
        interact(() -> {
            scene.getWindow().setX(0);
            scene.getWindow().setY(0);
        });
        resizeScene(width, height);
    }

    private static final String CALL = "translating-live-call";

    // The Detailed detail of a row that was sent in call 2, whose prompt section is the one measured.
    private void running(final boolean large) {
        final List<PromptSection> prompt = large ? LiveCallFixtures.largePrompt(LONG) : LiveCallFixtures.smallPrompt();
        final LiveRow row = new LiveRow("s-2-1", "ch7 · p201", TEXT, null, Stage.IN_CALL, null, List.of(), 2L);
        showLive(LiveRowFixtures.calls(LiveCallFixtures.waiting(2, 2, TEXT, prompt), List.of(row)));
        onFx(() -> ((ToggleButton) required("translating-live-mode-detailed")).fire());
        WaitForAsyncUtils.waitForFxEvents();
    }

    private double heightOf(final String id) {
        final Node node = required(id);
        return ThemeTestSupport.onFx(() -> node.getLayoutBounds().getHeight());
    }

    private void expand(final String row, final boolean expanded) {
        onFx(() -> ((TitledPane) required(row + "-prompt")).setExpanded(expanded));
        onFx(() -> {});
    }

    // How much of the opened body lies below its frame: the body's content past its viewport, zero when everything
    // shows.
    private double hiddenBelow(final String row) {
        final Node body = required(row + "-prompt-body");
        return ThemeTestSupport.onFx(() -> {
            final ScrollPane inner = (ScrollPane) body;
            return Math.max(
                    0,
                    inner.getContent().getLayoutBounds().getHeight()
                            - inner.getViewportBounds().getHeight());
        });
    }

    // Every region from the body up to the shell's scrolled content, each laid out shorter than it asks to be.
    private List<String> crushed(final String row) {
        final Node body = required(row + "-prompt-body");
        return ThemeTestSupport.onFx(() -> {
            final List<String> found = new ArrayList<>();
            // The walk ends at the shell's content host: above it are the scroll pane's own viewport nodes, which are
            // the window's height by design.
            Parent at = (Parent) body;
            while (at != null && !"shell-content-scroll".equals(at.getId()) && !isAboveContent(at)) {
                if (at instanceof Region region && region.getHeight() + SLACK < region.prefHeight(region.getWidth())) {
                    found.add(region.getClass().getSimpleName() + "#" + region.getId() + " " + region.getHeight()
                            + " < " + region.prefHeight(region.getWidth()));
                }
                at = at.getParent();
            }
            return found;
        });
    }

    private static boolean isAboveContent(final Parent node) {
        return node.lookup("#shell-content") != null && !"shell-content".equals(node.getId());
    }

    // IF an opened section kept a fixed or crushed height, THEN it would show one clipped line of what the model was
    // given; opened, its body is as tall as its content up to 320 px, and nothing above it is squeezed below its own
    // height.
    @ParameterizedTest
    @CsvSource({"944, 600, false", "944, 600, true", "1400, 900, false", "1400, 900, true"})
    void context_expanded_showsItsWholeContentOrScrollsAt320(
            final double width, final double height, final boolean large) {
        running(large);
        sizeTo(width, height);

        expand(CALL, true);

        final double body = heightOf(CALL + "-prompt-body");
        assertThat(Math.min(hiddenBelow(CALL), BODY_MAX - body))
                .as("either the whole text shows or the body is at its 320 px")
                .isLessThanOrEqualTo(SLACK);
        assertThat(body).isLessThanOrEqualTo(BODY_MAX + SLACK);
        assertThat(crushed(CALL)).isEmpty();
    }

    // IF a long context grew without limit, THEN one row would push the detail off the page; past 320 px the body
    // scrolls inside and holds the rest.
    @ParameterizedTest
    @CsvSource({"944, 600", "1400, 900"})
    void context_expandedWithMoreThanFits_scrollsInsideAt320(final double width, final double height) {
        running(true);
        sizeTo(width, height);

        expand(CALL, true);

        assertThat(hiddenBelow(CALL)).isGreaterThan(0);
        assertThat(heightOf(CALL + "-prompt-body")).isCloseTo(BODY_MAX, within(SLACK));
    }

    // IF closing a section left its space behind, THEN the detail would keep a gap the size of the context.
    @ParameterizedTest
    @CsvSource({"944, 600", "1400, 900"})
    void context_collapsedAgain_restoresTheHeights(final double width, final double height) {
        running(true);
        sizeTo(width, height);
        final double callBefore = heightOf(CALL);
        expand(CALL, true);

        expand(CALL, false);

        assertThat(heightOf(CALL)).isCloseTo(callBefore, within(SLACK));
    }
}
