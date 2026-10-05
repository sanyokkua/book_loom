package ua.bookloom.ui.screen;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TitledPane;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.testfx.util.WaitForAsyncUtils;
import ua.bookloom.api.project.ContextSnapshot;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.SegmentPath;
import ua.bookloom.api.project.SnapshotTerm;
import ua.bookloom.api.project.TermType;
import ua.bookloom.ui.ThemeTestSupport;
import ua.bookloom.ui.TooltipProbe;
import ua.bookloom.ui.state.LiveRow;
import ua.bookloom.ui.state.LiveRows;
import ua.bookloom.ui.state.RunState;

/**
 * What the dashboard tells a person who presses or hovers its figures: Review flagged always shows something where it
 * was pressed, and the remaining tile says what it counts besides the book text.
 */
class TranslatingPolishScreenTest extends TranslatingScreenTestBase {

    private static final ContextSnapshot CONTEXT = new ContextSnapshot(
            List.of("Дощ лив стіною."),
            List.of(new SnapshotTerm("Lovelace", "Лавлейс", TermType.CHARACTER, Gender.MALE, true)),
            List.of(),
            null,
            "");

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

    private void runningWithContext() {
        mirror().publishRunStarted("Frankenstein.epub", null);
        mirror().live()
                .publishLiveRows(new LiveRows(
                        new LiveRow(
                                "s-1",
                                "ch7 · p41",
                                "Text.",
                                "Текст.",
                                0.93,
                                SegmentPath.DRAFT,
                                false,
                                false,
                                null,
                                CONTEXT),
                        new LiveRow("s-2", "ch7 · p42", "Text.", null, null, null, true, false, null, CONTEXT)));
        WaitForAsyncUtils.waitForFxEvents();
        showTranslating();
    }

    private boolean isExpanded(final String id) {
        return ThemeTestSupport.onFx(() -> ((TitledPane) required(id)).isExpanded());
    }

    // IF the context the person opened closed itself whenever they looked at another step, THEN they would have to
    // reopen it on every return; each row's section stays as they left it, open or closed again.
    @Test
    void contextSection_openedThenAnotherStepVisited_isStillOpenOnReturn() {
        runningWithContext();
        onFx(() -> ((TitledPane) required("live-current-context")).setExpanded(true));

        showTranslating();

        assertThat(isExpanded("live-current-context")).isTrue();
        assertThat(isExpanded("live-last-context")).isFalse();
        onFx(() -> ((TitledPane) required("live-current-context")).setExpanded(false));
        showTranslating();
        assertThat(isExpanded("live-current-context")).isFalse();
    }

    // IF the open section closed when the rows moved on to the next segment, THEN it could not be read during a run.
    @Test
    void contextSection_opened_staysOpenWhenTheRowsChange() {
        runningWithContext();
        onFx(() -> ((TitledPane) required("live-current-context")).setExpanded(true));

        mirror().live()
                .publishLiveRows(new LiveRows(
                        new LiveRow(
                                "s-2",
                                "ch7 · p42",
                                "Text.",
                                "Текст.",
                                0.9,
                                SegmentPath.DRAFT,
                                false,
                                false,
                                null,
                                CONTEXT),
                        new LiveRow("s-3", "ch7 · p43", "More.", null, null, null, true, false, null, CONTEXT)));
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(isExpanded("live-current-context")).isTrue();
    }
}
