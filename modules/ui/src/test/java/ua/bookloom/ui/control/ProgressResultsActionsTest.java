package ua.bookloom.ui.control;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import javafx.event.Event;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import ua.bookloom.ui.ThemeTestSupport;

/** What each control of the Results view does: Revert on a row, Undo all, Show changed rows and Done. */
class ProgressResultsActionsTest extends ProgressResultsTestBase {

    private Button firstVisibleRevert() {
        return required("results-list").lookupAll(".button").stream()
                .filter(Node::isVisible)
                .map(Button.class::cast)
                .filter(button -> "Revert".equals(button.getText()))
                .findFirst()
                .orElseThrow();
    }

    // IF Revert on a row did not reach the results, THEN the button would be decoration.
    @Test
    void revert_rowButtonPressed_revertsThatRowAndShowsItReverted() {
        show(results(false, added(0, "Mid"), added(1, "Hale")));
        final Button first = ThemeTestSupport.onFx(this::firstVisibleRevert);

        onFx(first::fire);

        assertThat(reverted).containsExactly(0L);
        assertThat(texts()).contains("Reverted");
    }

    // IF a reverted row kept its "added" chip and its new value, THEN the row would still read as a change made.
    @Test
    void revert_rowButtonPressed_rowReadsRevertedWithTheValueItWasPutBackTo() {
        show(results(false, added(0, "Mid")));
        final Button first = ThemeTestSupport.onFx(this::firstVisibleRevert);

        onFx(first::fire);

        assertThat(texts()).contains("reverted").doesNotContain("added", "Терм · character");
        assertThat(required("results-list").lookupAll(".results-row-reverted")).isNotEmpty();
    }

    @Test
    void undoAll_pressed_putsEveryRowBackAndDisablesItself() {
        show(results(false, added(0, "Mid"), added(1, "Hale")));

        final Button undo = press("results-undo-all");

        assertThat(reverted).containsExactly(0L, 1L);
        assertThat(ThemeTestSupport.onFx(undo::isDisabled)).isTrue();
    }

    // IF the filter button only closed the view, THEN "Show changed rows" would show nothing.
    @Test
    void showChangedRows_pressed_asksTheScreenToFilterAndClosesTheView() {
        show(results(false, added(0, "Mid")));

        press("results-show-changed");

        assertThat(shownChanged.get()).isEqualTo(1);
        assertThat(done.get()).isEqualTo(1);
        assertThat(isShowing()).isFalse();
    }

    @Test
    void showChangedRows_onlyRemovedRows_isOffButNotMissing() {
        show(results(false, removed(0, "Well", null)));

        assertThat(ThemeTestSupport.onFx(() -> required("results-show-changed").isDisabled()))
                .isTrue();
    }

    @Test
    void done_pressed_closesTheViewOnce() {
        show(results(false, added(0, "Mid")));

        press("results-done");

        assertThat(done.get()).isEqualTo(1);
        assertThat(shownChanged.get()).isZero();
        assertThat(isShowing()).isFalse();
    }

    // IF Escape did something other than Done, THEN the key would leave the filter or the undo half done.
    @Test
    void escape_pressed_runsDone() {
        show(results(false, added(0, "Mid")));

        onFx(() -> Event.fireEvent(
                scene, new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.ESCAPE, false, false, false, false)));

        assertThat(done.get()).isEqualTo(1);
    }

    // IF Undo all stayed beside Done on a pass that changed nothing, THEN it would offer an undo of nothing.
    @Test
    void footer_nothingChanged_offersOnlyDone() {
        show(results(false));

        assertThat(scene.getRoot().lookup("#results-undo-all")).isNull();
        assertThat(scene.getRoot().lookup("#results-show-changed")).isNull();
        assertThat(((Button) required("results-done")).getText()).isEqualTo("Done");
    }

    // Every operable control explains itself on hover.
    @ParameterizedTest
    @ValueSource(strings = {"results-done", "results-show-changed", "results-undo-all"})
    void controls_shown_eachExplainsItself(final String id) {
        show(results(false, added(0, "Mid")));

        assertThat(Tips.explanationOf(required(id))).as(id).isPresent();
    }

    @Test
    void footer_changed_listsUndoAllShowChangedRowsThenDone() {
        show(results(false, added(0, "Mid")));

        assertThat(ThemeTestSupport.onFx(
                        () -> List.of("results-undo-all", "results-show-changed", "results-done").stream()
                                .map(id -> ((Button) required(id)).getText())
                                .toList()))
                .containsExactly("Undo all", "Show changed rows", "Done");
    }
}
