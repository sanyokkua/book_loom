package ua.bookloom.ui.dialog;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ListView;
import javafx.scene.control.TitledPane;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import ua.bookloom.ui.LiveCallFixtures;
import ua.bookloom.ui.ShellTestBase;
import ua.bookloom.ui.ThemeTestSupport;
import ua.bookloom.ui.control.Tips;
import ua.bookloom.ui.state.ChangeKind;
import ua.bookloom.ui.state.ChangeOperation;
import ua.bookloom.ui.state.ChangeOrigin;
import ua.bookloom.ui.state.ChangeResults;
import ua.bookloom.ui.state.ChangeRow;
import ua.bookloom.ui.state.LiveCalls;

/**
 * The results card: its counts, rows and columns drawn from the results, and each control reaching the results or the
 * screen. The reversals are recording fakes, since what they do to the glossary is proven on the view model.
 */
class ChangeResultsDialogTest extends ShellTestBase {

    private final List<Long> reverted = new ArrayList<>();
    private final AtomicInteger shownChanged = new AtomicInteger();

    private ChangeResults results(final boolean stopped, final ChangeRow... rows) {
        final List<ChangeResults.Item> items = new ArrayList<>();
        for (final ChangeRow row : rows) {
            items.add(new ChangeResults.Item(row, done -> {
                reverted.add(row.id());
                done.accept(true);
            }));
        }
        return new ChangeResults(ChangeOperation.NAME_REVIEW, stopped, items, LiveCalls.EMPTY);
    }

    private static ChangeRow added(final long id, final String term) {
        return new ChangeRow(id, ChangeKind.ADDED, term, "—", "Терм · character", null);
    }

    private static ChangeRow found(final long id, final String term) {
        return new ChangeRow(id, ChangeKind.ADDED, term, "—", "—", null, ChangeOrigin.TEXT);
    }

    private static ChangeRow removed(final long id, final String term, final @Nullable String reason) {
        return new ChangeRow(id, ChangeKind.REMOVED, term, "No target · other", "—", reason);
    }

    private void show(final ChangeResults results) {
        onFx(() -> injector.getInstance(ChangeResultsDialog.class).show(results, shownChanged::incrementAndGet));
    }

    private Button press(final String id) {
        final Button button = (Button) required(id);
        onFx(button::fire);
        return button;
    }

    private List<String> texts() {
        return textsUnder(required(ChangeResultsDialog.CARD_ID));
    }

    // IF the card gave counts only, THEN the person could not tell how large the operation was before reading rows.
    @Test
    void show_addedAndRemovedRows_headsTheCardWithTheOperationAndEveryCount() {
        show(results(false, added(0, "Mid"), added(1, "Hale"), removed(2, "Well", "not a name")));

        assertThat(texts())
                .contains(
                        "Name review — results",
                        "Added (2) · Removed (1) · Changed (0)",
                        "Added (2)",
                        "Removed (1)",
                        "Mid",
                        "Hale",
                        "Well");
        assertThat(texts()).doesNotContain("Changed (0)");
    }

    // IF text finds and model additions shared one "Added" heading, THEN the person could not tell what needed no
    // model.
    @Test
    void show_addedRowsOfBothOrigins_groupsThemUnderFoundInTheTextAndAddedByTheModel() {
        show(results(false, found(0, "imp"), added(1, "pentacle"), found(2, "master")));

        assertThat(texts()).contains("Found in the text (2)", "Added by the model (1)");
        assertThat(texts()).doesNotContain("Added (3)");
        assertThat(texts().indexOf("Found in the text (2)")).isLessThan(texts().indexOf("Added by the model (1)"));
    }

    // IF a name scan said "Added by the model" where there is no text step, THEN the heading would add noise.
    @Test
    void show_addedRowsOfOneOrigin_keepsThePlainAddedHeading() {
        show(results(false, added(0, "Mid"), added(1, "Hale")));

        assertThat(texts()).contains("Added (2)").doesNotContain("Added by the model (2)", "Found in the text (0)");
    }

    @Test
    void show_aRowWithAReason_showsTheWhyColumnWithTheReasonAndADashElsewhere() {
        show(results(false, added(0, "Mid"), removed(1, "Well", "not a name")));

        assertThat(texts()).contains("Why", "not a name", "—");
    }

    // IF the column showed with nothing in it, THEN every row would carry a dead cell.
    @Test
    void show_noRowHasAReason_hasNoWhyColumn() {
        show(results(false, added(0, "Mid")));

        assertThat(texts()).doesNotContain("Why", "not a name");
    }

    @Test
    void show_nothingChanged_saysSoInOnePlainLineWithNoRowsOrUndo() {
        show(results(false));

        assertThat(texts()).contains("Nothing changed.");
        assertThat(scene.getRoot().lookup("#" + ChangeResultsDialog.UNDO_ID)).isNull();
        assertThat(scene.getRoot().lookup("#" + ChangeResultsDialog.LIST_ID)).isNull();
    }

    @Test
    void show_stopped_saysNothingWasChanged() {
        show(results(true));

        assertThat(texts()).contains("Stopped. Nothing was changed.");
    }

    // IF Revert on a row did not reach the results, THEN the button would be decoration.
    @Test
    void revert_rowButtonPressed_revertsThatRowAndShowsItReverted() {
        show(results(false, added(0, "Mid"), added(1, "Hale")));
        final Button first = ThemeTestSupport.onFx(() -> firstVisibleRevert());

        onFx(first::fire);

        assertThat(reverted).containsExactly(0L);
        assertThat(texts()).contains("Reverted");
    }

    private Button firstVisibleRevert() {
        return required(ChangeResultsDialog.LIST_ID).lookupAll(".button").stream()
                .filter(Node::isVisible)
                .map(Button.class::cast)
                .filter(button -> "Revert".equals(button.getText()))
                .findFirst()
                .orElseThrow();
    }

    @Test
    void undoAll_pressed_revertsEveryRowAndDisablesItself() {
        show(results(false, added(0, "Mid"), added(1, "Hale")));

        final Button undo = press(ChangeResultsDialog.UNDO_ID);

        assertThat(reverted).containsExactly(0L, 1L);
        assertThat(ThemeTestSupport.onFx(undo::isDisabled)).isTrue();
    }

    // IF the filter button only closed the card, THEN "Show changed rows" would show nothing.
    @Test
    void showChangedRows_pressed_closesTheCardAndAsksTheScreenToFilter() {
        show(results(false, added(0, "Mid")));

        press(ChangeResultsDialog.SHOW_ID);

        assertThat(shownChanged.get()).isEqualTo(1);
        assertThat(ThemeTestSupport.onFx(() -> isShowing())).isFalse();
    }

    private boolean isShowing() {
        final Node card = scene.getRoot().lookup("#" + ChangeResultsDialog.CARD_ID);
        return card != null && card.isVisible() && card.getScene() != null;
    }

    @Test
    void showChangedRows_onlyRemovedRows_isOffButNotMissing() {
        show(results(false, removed(0, "Well", null)));

        assertThat(ThemeTestSupport.onFx(
                        () -> required(ChangeResultsDialog.SHOW_ID).isDisabled()))
                .isTrue();
    }

    @Test
    void close_pressed_closesTheCard() {
        show(results(false, added(0, "Mid")));

        press(ChangeResultsDialog.CLOSE_ID);

        assertThat(ThemeTestSupport.onFx(() -> isShowing())).isFalse();
    }

    // IF the calls the operation made were not kept for the card, THEN the new fold would always be empty.
    @Test
    void show_operationMadeCalls_offersTheFoldedModelCallsSection() {
        final var call = LiveCallFixtures.waiting(3, 1, "One.", LiveCallFixtures.smallPrompt());
        final ChangeResults withCalls =
                new ChangeResults(ChangeOperation.NAME_SCAN, false, List.of(), LiveCallFixtures.calls(call, null));

        show(withCalls);

        final TitledPane calls = (TitledPane) required(ChangeResultsDialog.CALLS_ID);
        assertThat(ThemeTestSupport.onFx(() -> calls.isVisible())).isTrue();
        assertThat(ThemeTestSupport.onFx(() -> calls.isExpanded())).isFalse();
        assertThat(calls.getText()).isEqualTo("Model calls");
    }

    @Test
    void show_operationMadeNoCalls_hasNoModelCallsSection() {
        show(results(false, added(0, "Mid")));

        assertThat(ThemeTestSupport.onFx(
                        () -> required(ChangeResultsDialog.CALLS_ID).isVisible()))
                .isFalse();
    }

    @Test
    void list_manyRows_hasFixedRowHeightsAndOneItemPerRowAndHeading() {
        show(results(false, added(0, "Mid"), removed(1, "Well", null)));

        final ListView<?> list = (ListView<?>) required(ChangeResultsDialog.LIST_ID);

        assertThat(list.getFixedCellSize()).isEqualTo(ResultRows.ROW_HEIGHT);
        assertThat(ThemeTestSupport.onFx(() -> list.getItems().size())).isEqualTo(4);
    }

    // Every operable control explains itself on hover.
    @ParameterizedTest
    @ValueSource(strings = {"results-close", "results-show-changed", "results-undo-all"})
    void controls_shown_eachExplainsItself(final String id) {
        show(results(false, added(0, "Mid")));

        assertThat(Tips.explanationOf(required(id))).as(id).isPresent();
    }
}
