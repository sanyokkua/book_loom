package ua.bookloom.ui.screen;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.ArrayList;
import java.util.List;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.Region;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.testfx.util.WaitForAsyncUtils;
import ua.bookloom.api.pipeline.PromptSection;
import ua.bookloom.ui.LiveCallFixtures;
import ua.bookloom.ui.ThemeTestSupport;

/**
 * The live blocks' "Prompt context" sections measured in the real shell: an opened section grows its block, the
 * live card and the page by what it shows, at its natural height up to 320 pixels and scrolling inside beyond that, and
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

    private void running(final boolean large) {
        mirror().publishRunStarted("Frankenstein.epub", null);
        final List<PromptSection> prompt = large ? LiveCallFixtures.largePrompt(LONG) : LiveCallFixtures.smallPrompt();
        mirror().live()
                .publishCalls(LiveCallFixtures.calls(
                        LiveCallFixtures.waiting(2, 2, TEXT, prompt),
                        LiveCallFixtures.answered(LiveCallFixtures.waiting(1, 2, TEXT, prompt), "{}")));
        WaitForAsyncUtils.waitForFxEvents();
        showTranslating();
    }

    private double heightOf(final String id) {
        final Node node = required(id);
        return ThemeTestSupport.onFx(() -> node.getLayoutBounds().getHeight());
    }

    private void expand(final String row, final boolean expanded) {
        onFx(() -> ((TitledPane) required(row + "-prompt")).setExpanded(expanded));
        onFx(() -> {});
    }

    // The natural height of what the body scrolls: its sections laid out at the width the body gives them.
    private double contentHeight(final String row) {
        final ScrollPane body = (ScrollPane) required(row + "-prompt-body");
        return ThemeTestSupport.onFx(() -> {
            final Region sections = (Region) body.getContent();
            return sections.prefHeight(sections.getWidth());
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
    // given; opened, its body is as tall as its content up to 320 px, its row and the live card grow by that much, and
    // nothing above it is squeezed below its own height.
    @ParameterizedTest
    @CsvSource({
        "944, 600, translating-live-card-current, false",
        "944, 600, translating-live-card-previous, true",
        "1400, 900, translating-live-card-current, true",
        "1400, 900, translating-live-card-previous, false"
    })
    void context_expanded_growsItsRowAndTheCardByItsContent(
            final double width, final double height, final String row, final boolean large) {
        running(large);
        sizeTo(width, height);
        final double cardBefore = heightOf("translating-live-card");
        final double rowBefore = heightOf(row);

        expand(row, true);

        final double body = heightOf(row + "-prompt-body");
        assertThat(body).isGreaterThanOrEqualTo(Math.min(contentHeight(row), BODY_MAX) - SLACK);
        assertThat(body).isLessThanOrEqualTo(BODY_MAX + SLACK);
        assertThat(heightOf(row) - rowBefore).isGreaterThanOrEqualTo(body);
        assertThat(heightOf("translating-live-card") - cardBefore).isCloseTo(heightOf(row) - rowBefore, within(SLACK));
        assertThat(crushed(row)).isEmpty();
    }

    // IF a long context grew without limit, THEN one row would push the activity log off the page; past 320 px the body
    // scrolls inside and holds the rest.
    @ParameterizedTest
    @CsvSource({"944, 600", "1400, 900"})
    void context_expandedWithMoreThanFits_scrollsInsideAt320(final double width, final double height) {
        running(true);
        sizeTo(width, height);

        expand("translating-live-card-current", true);

        assertThat(contentHeight("translating-live-card-current")).isGreaterThan(BODY_MAX);
        assertThat(heightOf("translating-live-card-current-prompt-body")).isCloseTo(BODY_MAX, within(SLACK));
    }

    // IF closing a section left its space behind, THEN the card would keep a gap the size of the context.
    @ParameterizedTest
    @CsvSource({"944, 600, translating-live-card-current", "1400, 900, translating-live-card-previous"})
    void context_collapsedAgain_restoresTheHeights(final double width, final double height, final String row) {
        running(true);
        sizeTo(width, height);
        final double cardBefore = heightOf("translating-live-card");
        final double rowBefore = heightOf(row);
        expand(row, true);

        expand(row, false);

        assertThat(heightOf(row)).isCloseTo(rowBefore, within(SLACK));
        assertThat(heightOf("translating-live-card")).isCloseTo(cardBefore, within(SLACK));
    }
}
