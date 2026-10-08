package ua.bookloom.ui.screen;

import static org.assertj.core.api.Assertions.assertThat;

import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TitledPane;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.testfx.util.WaitForAsyncUtils;
import ua.bookloom.ui.LiveCallFixtures;
import ua.bookloom.ui.ThemeTestSupport;
import ua.bookloom.ui.TooltipProbe;
import ua.bookloom.ui.state.RunState;

/**
 * What the dashboard tells a person who presses or hovers its figures: Review flagged always shows something where it
 * was pressed, and the remaining tile says what it counts besides the book text.
 */
class TranslatingPolishScreenTest extends TranslatingScreenTestBase {

    private static final String CURRENT_PROMPT = "translating-live-card-current-prompt";
    private static final String PREVIOUS_PROMPT = "translating-live-card-previous-prompt";

    private Bounds sceneBounds(final String id) {
        return ThemeTestSupport.onFx(() -> {
            final Node node = required(id);
            return node.localToScene(node.getLayoutBounds());
        });
    }

    // IF Review flagged (0) opened its panel below the live card and the log, THEN pressing it would seem to do
    // nothing; the panel opens under the run controls, inside the viewport, and says nothing is flagged.
    @ParameterizedTest
    @EnumSource(
            value = RunState.class,
            names = {"RUNNING", "PAUSED"})
    void reviewFlagged_nothingFlagged_opensTheEmptyPanelInViewUnderTheControls(final RunState state) {
        showTranslating();
        publish(state);
        publishProgress(10, 0, 20);
        final ScrollPane pane = (ScrollPane) required("shell-content-scroll");
        onFx(() -> pane.setVvalue(0));

        onFx(() -> button("translating-review-flagged").fire());

        assertThat(isShown("review-panel")).isTrue();
        assertThat(labelText("review-empty-text")).isEqualTo("Nothing flagged — every segment passed the checks");
        final Bounds viewport = ThemeTestSupport.onFx(() -> pane.localToScene(pane.getLayoutBounds()));
        assertThat(sceneBounds("review-panel").getMinY())
                .isGreaterThanOrEqualTo(sceneBounds("translating-head").getMaxY())
                .isLessThan(viewport.getMaxY());
        assertThat(sceneBounds("review-panel").getMaxY())
                .isLessThanOrEqualTo(sceneBounds("translating-progress-card").getMinY());
    }

    // IF the remaining tile were silent about what it counts, THEN its first figure, above the book-text total on
    // Structure, would read as a mistake.
    @Test
    void remainingTile_hovered_explainsTheOtherTextsItCounts() {
        showTranslating();
        publish(RunState.RUNNING);

        assertThat(TooltipProbe.tipText(required("translating-tile-remaining")))
                .startsWith("Segments not translated yet.")
                .contains("titles, alt texts, contents entries and metadata");
    }

    private void runningWithPrompt() {
        mirror().publishRunStarted("Frankenstein.epub", null);
        publishCalls(2);
        showTranslating();
    }

    private void publishCalls(final long newest) {
        mirror().live()
                .publishCalls(LiveCallFixtures.calls(
                        LiveCallFixtures.waiting(newest, 2, "Text.", LiveCallFixtures.smallPrompt()),
                        LiveCallFixtures.answered(
                                LiveCallFixtures.waiting(newest - 1, 2, "Text.", LiveCallFixtures.smallPrompt()),
                                "{}")));
        WaitForAsyncUtils.waitForFxEvents();
    }

    private boolean isExpanded(final String id) {
        return ThemeTestSupport.onFx(() -> ((TitledPane) required(id)).isExpanded());
    }

    // IF the prompt the person opened closed itself whenever they looked at another step, THEN they would have to
    // reopen it on every return; each block's section stays as they left it, open or closed again.
    @Test
    void promptSection_openedThenAnotherStepVisited_isStillOpenOnReturn() {
        runningWithPrompt();
        onFx(() -> ((TitledPane) required(CURRENT_PROMPT)).setExpanded(true));

        showTranslating();

        assertThat(isExpanded(CURRENT_PROMPT)).isTrue();
        assertThat(isExpanded(PREVIOUS_PROMPT)).isFalse();
        onFx(() -> ((TitledPane) required(CURRENT_PROMPT)).setExpanded(false));
        showTranslating();
        assertThat(isExpanded(CURRENT_PROMPT)).isFalse();
    }

    // IF the open section closed when the next call took the block, THEN it could not be read during a run.
    @Test
    void promptSection_opened_staysOpenWhenTheCallsChange() {
        runningWithPrompt();
        onFx(() -> ((TitledPane) required(CURRENT_PROMPT)).setExpanded(true));

        publishCalls(3);

        assertThat(isExpanded(CURRENT_PROMPT)).isTrue();
    }
}
